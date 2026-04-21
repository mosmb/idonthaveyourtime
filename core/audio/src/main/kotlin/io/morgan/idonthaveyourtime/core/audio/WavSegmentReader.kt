package io.morgan.idonthaveyourtime.core.audio

import io.morgan.idonthaveyourtime.core.common.ProcessingException
import java.io.Closeable
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

class WavSegmentReader(
    wavFile: File,
) : Closeable {

    private val raf = RandomAccessFile(wavFile, "r")
    private val info = readWavInfo(raf)

    val durationMs: Long = run {
        val totalSamples = info.dataSizeBytes / BYTES_PER_SAMPLE.toLong()
        ((totalSamples.toDouble() / info.sampleRate.toDouble()) * 1000.0).toLong().coerceAtLeast(0L)
    }

    fun readFloats(startMs: Long, endMs: Long): FloatArray {
        val clampedStartMs = startMs.coerceIn(0L, durationMs)
        val clampedEndMs = endMs.coerceIn(0L, durationMs)
        if (clampedEndMs <= clampedStartMs) {
            return FloatArray(0)
        }

        val startSample = ((clampedStartMs.toDouble() / 1000.0) * info.sampleRate.toDouble()).toLong()
        val endSampleExclusive = ceil(((clampedEndMs.toDouble() / 1000.0) * info.sampleRate.toDouble())).toLong()
        val sampleCountLong = max(0L, endSampleExclusive - startSample)
        if (sampleCountLong <= 0L) {
            return FloatArray(0)
        }

        val sampleCount = sampleCountLong.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        val result = FloatArray(sampleCount)

        val startByteOffset = info.dataOffsetBytes + startSample * BYTES_PER_SAMPLE.toLong()
        raf.seek(startByteOffset)

        val readBuffer = ByteArray(min(MAX_READ_BYTES, sampleCount * BYTES_PER_SAMPLE))
        var outIndex = 0
        while (outIndex < sampleCount) {
            val remainingSamples = sampleCount - outIndex
            val samplesToRead = min(remainingSamples, readBuffer.size / BYTES_PER_SAMPLE)
            val bytesToRead = samplesToRead * BYTES_PER_SAMPLE

            val bytesRead = raf.read(readBuffer, 0, bytesToRead)
            if (bytesRead <= 0) {
                break
            }

            var offset = 0
            while (offset + 1 < bytesRead && outIndex < sampleCount) {
                val lo = readBuffer[offset].toInt() and 0xFF
                val hi = readBuffer[offset + 1].toInt()
                val sample = ((hi shl 8) or lo).toShort()
                result[outIndex] = (sample / 32768.0f).coerceIn(-1f, 1f)
                outIndex += 1
                offset += BYTES_PER_SAMPLE
            }
        }

        return if (outIndex == sampleCount) result else result.copyOf(outIndex)
    }

    fun readWavBytes(startMs: Long, endMs: Long): ByteArray {
        val pcmBytes = readPcm16Bytes(startMs = startMs, endMs = endMs)
        return buildWavBytes(
            pcmData = pcmBytes,
            sampleRate = info.sampleRate,
            channels = info.channels,
            bitsPerSample = info.bitsPerSample,
        )
    }

    override fun close() {
        raf.close()
    }

    private fun readPcm16Bytes(startMs: Long, endMs: Long): ByteArray {
        val clampedStartMs = startMs.coerceIn(0L, durationMs)
        val clampedEndMs = endMs.coerceIn(0L, durationMs)
        if (clampedEndMs <= clampedStartMs) {
            return ByteArray(0)
        }

        val startSample = ((clampedStartMs.toDouble() / 1000.0) * info.sampleRate.toDouble()).toLong()
        val endSampleExclusive = ceil(((clampedEndMs.toDouble() / 1000.0) * info.sampleRate.toDouble())).toLong()
        val sampleCountLong = max(0L, endSampleExclusive - startSample)
        if (sampleCountLong <= 0L) {
            return ByteArray(0)
        }

        val sampleCount = sampleCountLong.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        val result = ByteArray(sampleCount * BYTES_PER_SAMPLE)

        val startByteOffset = info.dataOffsetBytes + startSample * BYTES_PER_SAMPLE.toLong()
        raf.seek(startByteOffset)

        val readBuffer = ByteArray(min(MAX_READ_BYTES, result.size))
        var outIndex = 0
        while (outIndex < result.size) {
            val bytesToRead = min(result.size - outIndex, readBuffer.size)
            val bytesRead = raf.read(readBuffer, 0, bytesToRead)
            if (bytesRead <= 0) {
                break
            }
            System.arraycopy(readBuffer, 0, result, outIndex, bytesRead)
            outIndex += bytesRead
        }

        return if (outIndex == result.size) result else result.copyOf(outIndex)
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
        if (riff != "RIFF" || wave != "WAVE") {
            throw ProcessingException("INVALID_WAV", "Invalid WAV header (expected RIFF/WAVE)")
        }

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
                    if (chunkSize < 16L) {
                        throw ProcessingException("INVALID_WAV", "Invalid fmt chunk")
                    }
                    val fmt = ByteArray(16)
                    raf.readFully(fmt)
                    val bb = ByteBuffer.wrap(fmt).order(ByteOrder.LITTLE_ENDIAN)
                    audioFormat = (bb.short.toInt() and 0xFFFF)
                    channels = (bb.short.toInt() and 0xFFFF)
                    sampleRate = bb.int
                    bb.int
                    bb.short
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

        if (audioFormat != 1) {
            throw ProcessingException("INVALID_WAV", "Unsupported WAV format (expected PCM)")
        }
        if (channels != 1 || sampleRate != 16_000 || bitsPerSample != 16) {
            throw ProcessingException("INVALID_WAV", "WAV must be PCM16 mono 16kHz")
        }
        if (dataOffset < 0 || dataSize <= 0) {
            throw ProcessingException("INVALID_WAV", "Missing WAV data chunk")
        }

        return WavInfo(
            audioFormat = audioFormat,
            channels = channels,
            sampleRate = sampleRate,
            bitsPerSample = bitsPerSample,
            dataOffsetBytes = dataOffset,
            dataSizeBytes = dataSize,
        )
    }

    private fun buildWavBytes(
        pcmData: ByteArray,
        sampleRate: Int,
        channels: Int,
        bitsPerSample: Int,
    ): ByteArray {
        val byteRate = sampleRate * channels * bitsPerSample / 8
        val blockAlign = channels * bitsPerSample / 8
        val fileSize = 36 + pcmData.size

        val buffer = ByteBuffer.allocate(44 + pcmData.size).order(ByteOrder.LITTLE_ENDIAN)
        buffer.put("RIFF".toByteArray())
        buffer.putInt(fileSize)
        buffer.put("WAVE".toByteArray())
        buffer.put("fmt ".toByteArray())
        buffer.putInt(16)
        buffer.putShort(1)
        buffer.putShort(channels.toShort())
        buffer.putInt(sampleRate)
        buffer.putInt(byteRate)
        buffer.putShort(blockAlign.toShort())
        buffer.putShort(bitsPerSample.toShort())
        buffer.put("data".toByteArray())
        buffer.putInt(pcmData.size)
        buffer.put(pcmData)
        return buffer.array()
    }

    private companion object {
        const val BYTES_PER_SAMPLE = 2
        const val MAX_READ_BYTES = 256 * 1024
    }
}
