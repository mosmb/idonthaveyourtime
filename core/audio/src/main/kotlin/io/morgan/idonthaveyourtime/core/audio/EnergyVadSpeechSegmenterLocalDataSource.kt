package io.morgan.idonthaveyourtime.core.audio

import io.morgan.idonthaveyourtime.core.data.datasource.audio.SpeechSegmenterLocalDataSource
import io.morgan.idonthaveyourtime.core.data.di.IoDispatcher
import io.morgan.idonthaveyourtime.core.model.AudioSegment
import io.morgan.idonthaveyourtime.core.model.SegmentationConfig
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.inject.Inject
import kotlin.math.ceil
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import timber.log.Timber

internal class EnergyVadSpeechSegmenterLocalDataSource @Inject constructor(
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : SpeechSegmenterLocalDataSource {

    override suspend fun segment16kMonoWav(
        wavFilePath: String,
        config: SegmentationConfig,
        onProgress: suspend (Float) -> Unit,
    ): Result<List<AudioSegment>> = withContext(ioDispatcher) {
        runCatching {
            val wavFile = File(wavFilePath)
            require(wavFile.exists()) { "WAV file does not exist: ${wavFile.absolutePath}" }

            RandomAccessFile(wavFile, "r").use { raf ->
                val info = readWavInfo(raf)
                require(info.audioFormat == 1) { "Unsupported WAV format: ${info.audioFormat} (expected PCM)" }
                require(info.channels == 1) { "Unsupported WAV channels: ${info.channels} (expected mono)" }
                require(info.sampleRate == 16_000) { "Unsupported WAV sample rate: ${info.sampleRate} (expected 16000)" }
                require(info.bitsPerSample == 16) { "Unsupported WAV bits/sample: ${info.bitsPerSample} (expected 16)" }

                val frameMs = config.frameDurationMs.coerceIn(10, 30)
                val frameSamples = (info.sampleRate * frameMs) / 1000
                require(frameSamples > 0) { "Invalid VAD frame size: $frameSamples samples" }

                val totalSamples = info.dataSizeBytes / BYTES_PER_SAMPLE.toLong()
                val totalFrames = ceil(totalSamples.toDouble() / frameSamples.toDouble()).toInt().coerceAtLeast(1)
                val durationMs = ((totalSamples.toDouble() / info.sampleRate.toDouble()) * 1000.0).toLong().coerceAtLeast(0L)

                Timber.tag(TAG).i(
                    "VAD start wav=%s durationMs=%d frames=%d frameMs=%d",
                    wavFile.absolutePath,
                    durationMs,
                    totalFrames,
                    frameMs,
                )

                val energiesDb = FloatArray(totalFrames)
                raf.seek(info.dataOffsetBytes)

                val frameBytes = frameSamples * BYTES_PER_SAMPLE
                val framesPerRead = 64
                val buffer = ByteArray(frameBytes * framesPerRead)

                var frameIndex = 0
                while (frameIndex < totalFrames) {
                    coroutineContext.ensureActive()

                    val framesToRead = min(framesPerRead, totalFrames - frameIndex)
                    val bytesToRead = framesToRead * frameBytes
                    val bytesRead = raf.read(buffer, 0, bytesToRead)
                    if (bytesRead <= 0) {
                        break
                    }

                    var offset = 0
                    var readFrames = 0
                    while (readFrames < framesToRead && offset < bytesRead) {
                        val remaining = bytesRead - offset
                        val thisFrameBytes = min(frameBytes, remaining)
                        val sampleCount = thisFrameBytes / BYTES_PER_SAMPLE
                        if (sampleCount <= 0) break

                        var sumSquares = 0.0
                        var sampleOffset = offset
                        for (i in 0 until sampleCount) {
                            val lo = buffer[sampleOffset].toInt() and 0xFF
                            val hi = buffer[sampleOffset + 1].toInt()
                            val sample = ((hi shl 8) or lo).toShort()
                            val v = sample / 32768.0
                            sumSquares += v * v
                            sampleOffset += 2
                        }

                        val rms = sqrt(sumSquares / sampleCount.toDouble())
                        val db = if (rms <= 0.0) {
                            MIN_DB
                        } else {
                            (20.0 * log10(rms)).toFloat().coerceAtLeast(MIN_DB)
                        }

                        energiesDb[frameIndex + readFrames] = db
                        offset += thisFrameBytes
                        readFrames += 1
                    }

                    frameIndex += readFrames
                    onProgress(((frameIndex.toFloat() / totalFrames.toFloat()) * 0.5f).coerceIn(0f, 0.5f))
                }

                val thresholdDb = computeThresholdDb(energiesDb)
                Timber.tag(TAG).i("VAD thresholdDb=%.2f", thresholdDb)

                val rawSegments = detectSpeechSegments(
                    energiesDb = energiesDb,
                    thresholdDb = thresholdDb,
                    frameMs = frameMs,
                    minSpeechStartMs = config.minSpeechStartMs,
                    minSilenceEndMs = config.minSilenceEndMs,
                )

                val targetFrames = (config.targetSpeechMs / frameMs).toInt().coerceAtLeast(1)
                val maxFrames = (config.maxSpeechMs / frameMs).toInt().coerceAtLeast(targetFrames)
                val splitSegments = rawSegments.flatMap { seg ->
                    splitLongSegment(
                        energiesDb = energiesDb,
                        startFrame = seg.first,
                        endFrameExclusive = seg.second,
                        targetFrames = targetFrames,
                        maxFrames = maxFrames,
                        searchWindowFrames = (2_000 / frameMs).coerceAtLeast(1),
                    )
                }

                val padded = splitSegments
                    .map { (startFrame, endFrameExclusive) ->
                        val startMs = (startFrame.toLong() * frameMs.toLong())
                        val endMs = (endFrameExclusive.toLong() * frameMs.toLong())
                        val paddedStart = (startMs - config.boundaryPadMs).coerceAtLeast(0L)
                        val paddedEnd = (endMs + config.boundaryPadMs).coerceAtMost(durationMs)
                        paddedStart to paddedEnd
                    }
                    .filter { (startMs, endMs) -> endMs > startMs }
                    .toMutableList()

                for (i in 1 until padded.size) {
                    val (startMs, endMs) = padded[i]
                    padded[i] = ((startMs - config.overlapMs).coerceAtLeast(0L)) to endMs
                }

                val result = padded
                    .map { (startMs, endMs) -> AudioSegment(startMs = startMs, endMs = endMs) }
                    .distinctBy { it.startMs to it.endMs }

                onProgress(1f)
                Timber.tag(TAG).i("VAD done segments=%d", result.size)
                result
            }
        }
    }

    private data class WavInfo(
        val audioFormat: Int,
        val channels: Int,
        val sampleRate: Int,
        val bitsPerSample: Int,
        val dataOffsetBytes: Long,
        val dataSizeBytes: Long,
    )

    private fun readWavInfo(raf: RandomAccessFile): WavInfo {
        raf.seek(0)
        val header = ByteArray(12)
        raf.readFully(header)
        val riff = String(header, 0, 4)
        val wave = String(header, 8, 4)
        require(riff == "RIFF" && wave == "WAVE") { "Invalid WAV header (expected RIFF/WAVE)" }

        var audioFormat = -1
        var channels = -1
        var sampleRate = -1
        var bitsPerSample = -1
        var dataOffset = -1L
        var dataSize = -1L

        while (raf.filePointer + 8 <= raf.length()) {
            val chunkHeader = ByteArray(8)
            raf.readFully(chunkHeader)
            val chunkId = String(chunkHeader, 0, 4)
            val chunkSize = ByteBuffer.wrap(chunkHeader, 4, 4).order(ByteOrder.LITTLE_ENDIAN).int.toLong()
            val chunkDataStart = raf.filePointer

            when (chunkId) {
                "fmt " -> {
                    require(chunkSize >= 16L) { "Invalid WAV fmt chunk" }
                    val fmt = ByteArray(16)
                    raf.readFully(fmt)
                    val bb = ByteBuffer.wrap(fmt).order(ByteOrder.LITTLE_ENDIAN)
                    audioFormat = (bb.short.toInt() and 0xFFFF)
                    channels = (bb.short.toInt() and 0xFFFF)
                    sampleRate = bb.int
                    bb.int // byteRate
                    bb.short // blockAlign
                    bitsPerSample = (bb.short.toInt() and 0xFFFF)
                    raf.seek(chunkDataStart + chunkSize + (chunkSize % 2))
                }

                "data" -> {
                    dataOffset = chunkDataStart
                    dataSize = chunkSize
                    raf.seek(chunkDataStart + chunkSize + (chunkSize % 2))
                }

                else -> raf.seek(chunkDataStart + chunkSize + (chunkSize % 2))
            }

            if (audioFormat != -1 && dataOffset != -1L) {
                break
            }
        }

        require(audioFormat != -1 && dataOffset != -1L) { "Invalid WAV file (missing fmt or data chunk)" }

        return WavInfo(
            audioFormat = audioFormat,
            channels = channels,
            sampleRate = sampleRate,
            bitsPerSample = bitsPerSample,
            dataOffsetBytes = dataOffset,
            dataSizeBytes = dataSize,
        )
    }

    private fun computeThresholdDb(energiesDb: FloatArray): Float {
        if (energiesDb.isEmpty()) return -40f

        val sorted = energiesDb.copyOf()
        sorted.sort()
        val noiseDb = sorted[(sorted.size * 0.2f).toInt().coerceIn(0, sorted.lastIndex)]
        val speechDb = sorted[(sorted.size * 0.8f).toInt().coerceIn(0, sorted.lastIndex)]
        val dynamicRange = (speechDb - noiseDb).coerceAtLeast(5f)

        var threshold = noiseDb + (dynamicRange * 0.5f)
        threshold = threshold.coerceIn(noiseDb + 6f, noiseDb + 20f)
        threshold = threshold.coerceIn(-60f, -20f)
        return threshold
    }

    private fun detectSpeechSegments(
        energiesDb: FloatArray,
        thresholdDb: Float,
        frameMs: Int,
        minSpeechStartMs: Long,
        minSilenceEndMs: Long,
    ): List<Pair<Int, Int>> {
        val minSpeechFrames = ceil(minSpeechStartMs.toDouble() / frameMs.toDouble()).toInt().coerceAtLeast(1)
        val minSilenceFrames = ceil(minSilenceEndMs.toDouble() / frameMs.toDouble()).toInt().coerceAtLeast(1)

        val segments = mutableListOf<Pair<Int, Int>>()

        var inSpeech = false
        var speechStreak = 0
        var silenceStreak = 0
        var segmentStart = 0
        var lastSpeechFrame = 0

        for (i in energiesDb.indices) {
            val isSpeech = energiesDb[i] > thresholdDb

            if (isSpeech) {
                speechStreak += 1
                silenceStreak = 0
                lastSpeechFrame = i

                if (!inSpeech && speechStreak >= minSpeechFrames) {
                    inSpeech = true
                    segmentStart = (i - speechStreak + 1).coerceAtLeast(0)
                }
            } else {
                if (inSpeech) {
                    silenceStreak += 1
                    if (silenceStreak >= minSilenceFrames) {
                        val endExclusive = (lastSpeechFrame + 1).coerceAtMost(energiesDb.size)
                        if (endExclusive > segmentStart) {
                            segments += segmentStart to endExclusive
                        }
                        inSpeech = false
                        speechStreak = 0
                        silenceStreak = 0
                    }
                } else {
                    speechStreak = 0
                }
            }
        }

        if (inSpeech) {
            val endExclusive = (lastSpeechFrame + 1).coerceAtMost(energiesDb.size)
            if (endExclusive > segmentStart) {
                segments += segmentStart to endExclusive
            }
        }

        return segments
    }

    private fun splitLongSegment(
        energiesDb: FloatArray,
        startFrame: Int,
        endFrameExclusive: Int,
        targetFrames: Int,
        maxFrames: Int,
        searchWindowFrames: Int,
    ): List<Pair<Int, Int>> {
        val result = mutableListOf<Pair<Int, Int>>()
        var cursor = startFrame

        while (cursor < endFrameExclusive) {
            val remaining = endFrameExclusive - cursor
            if (remaining <= maxFrames) {
                result += cursor to endFrameExclusive
                break
            }

            val desired = cursor + targetFrames
            val earliest = max(cursor + 1, desired - searchWindowFrames)
            val latestExclusive = min(endFrameExclusive, min(cursor + maxFrames, desired + searchWindowFrames))

            var cut = min(desired, endFrameExclusive)
            var minEnergy = Float.MAX_VALUE
            for (i in earliest until latestExclusive) {
                val e = energiesDb.getOrNull(i) ?: continue
                if (e < minEnergy) {
                    minEnergy = e
                    cut = i
                }
            }

            if (cut <= cursor) {
                cut = min(desired, endFrameExclusive)
            }

            result += cursor to cut
            cursor = cut
        }

        return result
    }

    private companion object {
        const val TAG = "EnergyVadSpeechSegmenterDataSource"
        const val BYTES_PER_SAMPLE = 2
        const val MIN_DB = -80f
    }
}
