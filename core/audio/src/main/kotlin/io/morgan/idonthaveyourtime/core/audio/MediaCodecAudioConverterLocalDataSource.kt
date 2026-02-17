package io.morgan.idonthaveyourtime.core.audio

import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import io.morgan.idonthaveyourtime.core.common.ProcessingException
import io.morgan.idonthaveyourtime.core.data.datasource.audio.AudioConverterLocalDataSource
import io.morgan.idonthaveyourtime.core.data.di.IoDispatcher
import io.morgan.idonthaveyourtime.core.model.WavAudio
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.inject.Inject
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.system.measureTimeMillis
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import timber.log.Timber

internal class MediaCodecAudioConverterLocalDataSource @Inject constructor(
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : AudioConverterLocalDataSource {

    override suspend fun toMono16kWav(inputFilePath: String, sessionId: String): Result<WavAudio> =
        withContext(ioDispatcher) {
            val cancellationContext = coroutineContext

            Timber.tag(TAG).i(
                "Audio conversion start sessionId=%s input=%s sizeBytes=%d",
                sessionId,
                inputFilePath,
                runCatching { File(inputFilePath).length() }.getOrDefault(-1L),
            )

            runCatching {
                var wavAudio: WavAudio? = null
                    val elapsedMs = measureTimeMillis {
                        val outputFile = File(File(inputFilePath).parentFile, "converted.wav")
                        val durationMs = decodeResampleToWav(
                            inputFilePath = inputFilePath,
                            outputFile = outputFile,
                            cancellationContext = cancellationContext,
                        )

                        wavAudio = WavAudio(
                            filePath = outputFile.absolutePath,
                            sampleRate = TARGET_SAMPLE_RATE,
                        channels = 1,
                        durationMs = durationMs,
                    )
                }

                val created = requireNotNull(wavAudio) { "WAV conversion completed without producing output" }
                Timber.tag(TAG).i(
                    "Audio conversion done sessionId=%s output=%s durationMs=%s elapsedMs=%d outputSizeBytes=%d",
                    sessionId,
                    created.filePath,
                    created.durationMs,
                    elapsedMs,
                    runCatching { File(created.filePath).length() }.getOrDefault(-1L),
                )

                created
            }.onFailure { throwable ->
                Timber.tag(TAG).e(
                    throwable,
                    "Audio conversion failed sessionId=%s input=%s",
                    sessionId,
                    inputFilePath,
                )
            }
        }

    private fun decodeResampleToWav(
        inputFilePath: String,
        outputFile: File,
        cancellationContext: CoroutineContext,
    ): Long {
        Timber.tag(TAG).d("decodeResampleToWav start input=%s output=%s", inputFilePath, outputFile.absolutePath)

        val extractor = MediaExtractor()
        var decoder: MediaCodec? = null
        var raf: RandomAccessFile? = null

        try {
            extractor.setDataSource(inputFilePath)

            val audioTrackIndex = (0 until extractor.trackCount)
                .firstOrNull { index ->
                    val format = extractor.getTrackFormat(index)
                    val mime = format.getString(MediaFormat.KEY_MIME).orEmpty()
                    mime.startsWith("audio/")
                } ?: throw ProcessingException("UNSUPPORTED_AUDIO_FORMAT", "No audio track found")

            extractor.selectTrack(audioTrackIndex)
            val inputFormat = extractor.getTrackFormat(audioTrackIndex)
            val mimeType = inputFormat.getString(MediaFormat.KEY_MIME)
                ?: throw ProcessingException("UNSUPPORTED_AUDIO_FORMAT", "Missing audio mime type")

            decoder = MediaCodec.createDecoderByType(mimeType)
            decoder.configure(inputFormat, null, null, 0)
            decoder.start()

            outputFile.parentFile?.mkdirs()
            raf = RandomAccessFile(outputFile, "rw")
            raf.setLength(0L)
            writeWavHeader(
                raf = raf,
                sampleRate = TARGET_SAMPLE_RATE,
                channels = 1,
                bitsPerSample = 16,
                dataSizeBytes = 0,
            )

            raf.seek(WAV_HEADER_BYTES.toLong())

            var channelCount = inputFormat.getIntegerSafely(MediaFormat.KEY_CHANNEL_COUNT) ?: 1
            var inputSampleRate = inputFormat.getIntegerSafely(MediaFormat.KEY_SAMPLE_RATE) ?: TARGET_SAMPLE_RATE
            var pcmEncoding: Int = AudioFormat.ENCODING_PCM_16BIT

            val resampler = StreamingLinearResampler(
                inputRate = inputSampleRate,
                outputRate = TARGET_SAMPLE_RATE,
            )

            val bufferInfo = MediaCodec.BufferInfo()
            var inputDone = false
            var outputDone = false
            var totalOutputSamples = 0L

            var pcmWriteBuffer = ByteArray(0)

            while (!outputDone) {
                cancellationContext.ensureActive()

                if (!inputDone) {
                    val inputBufferIndex = decoder.dequeueInputBuffer(TIMEOUT_US)
                    if (inputBufferIndex >= 0) {
                        val inputBuffer = decoder.getInputBuffer(inputBufferIndex)
                            ?: throw ProcessingException("DECODE_FAILED", "Missing decoder input buffer")

                        val sampleSize = extractor.readSampleData(inputBuffer, 0)
                        if (sampleSize < 0) {
                            decoder.queueInputBuffer(
                                inputBufferIndex,
                                0,
                                0,
                                0,
                                MediaCodec.BUFFER_FLAG_END_OF_STREAM,
                            )
                            inputDone = true
                        } else {
                            decoder.queueInputBuffer(
                                inputBufferIndex,
                                0,
                                sampleSize,
                                extractor.sampleTime,
                                0,
                            )
                            extractor.advance()
                        }
                    }
                }

                val outputBufferIndex = decoder.dequeueOutputBuffer(bufferInfo, TIMEOUT_US)
                when {
                    outputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        val outputFormat = decoder.outputFormat
                        channelCount = outputFormat.getIntegerSafely(MediaFormat.KEY_CHANNEL_COUNT) ?: channelCount
                        inputSampleRate = outputFormat.getIntegerSafely(MediaFormat.KEY_SAMPLE_RATE) ?: inputSampleRate
                        pcmEncoding = outputFormat.getIntegerSafely(MediaFormat.KEY_PCM_ENCODING) ?: pcmEncoding

                        resampler.updateInputRate(inputSampleRate)

                        Timber.tag(TAG).d(
                            "decodeResampleToWav outputFormatChanged channels=%d sampleRate=%d encoding=%d",
                            channelCount,
                            inputSampleRate,
                            pcmEncoding,
                        )
                    }

                    outputBufferIndex >= 0 -> {
                        val outputBuffer = decoder.getOutputBuffer(outputBufferIndex)
                            ?: throw ProcessingException("DECODE_FAILED", "Missing decoder output buffer")

                        outputBuffer.position(bufferInfo.offset)
                        outputBuffer.limit(bufferInfo.offset + bufferInfo.size)

                        val mono = downmixToMonoFloats(
                            buffer = outputBuffer,
                            channelCount = channelCount,
                            pcmEncoding = pcmEncoding,
                        )

                        val resampled = resampler.resampleToPcm16(mono)

                        val bytesToWrite = resampled.size * BYTES_PER_SAMPLE
                        if (bytesToWrite > 0) {
                            if (pcmWriteBuffer.size < bytesToWrite) {
                                pcmWriteBuffer = ByteArray(bytesToWrite)
                            }

                            var outOffset = 0
                            for (sample in resampled) {
                                val v = sample.toInt()
                                pcmWriteBuffer[outOffset] = (v and 0xFF).toByte()
                                pcmWriteBuffer[outOffset + 1] = ((v ushr 8) and 0xFF).toByte()
                                outOffset += 2
                            }
                            raf.write(pcmWriteBuffer, 0, bytesToWrite)
                            totalOutputSamples += resampled.size.toLong()
                        }

                        decoder.releaseOutputBuffer(outputBufferIndex, false)

                        if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                            outputDone = true
                        }
                    }
                }
            }

            val dataSizeBytes = (totalOutputSamples * BYTES_PER_SAMPLE.toLong()).toInt()
            patchWavHeaderSizes(raf = raf, dataSizeBytes = dataSizeBytes)

            val durationMs = ((totalOutputSamples.toDouble() / TARGET_SAMPLE_RATE.toDouble()) * 1000.0).toLong()
            Timber.tag(TAG).d(
                "decodeResampleToWav done outputSamples=%d durationMs=%d",
                totalOutputSamples,
                durationMs,
            )
            return durationMs
        } finally {
            runCatching {
                decoder?.stop()
            }
            runCatching {
                decoder?.release()
            }
            runCatching {
                extractor.release()
            }
            runCatching {
                raf?.close()
            }
        }
    }

    private fun downmixToMonoFloats(
        buffer: ByteBuffer,
        channelCount: Int,
        pcmEncoding: Int,
    ): FloatArray {
        if (channelCount <= 0) {
            throw ProcessingException("UNSUPPORTED_AUDIO_FORMAT", "Invalid channel count: $channelCount")
        }

        return when (pcmEncoding) {
            AudioFormat.ENCODING_PCM_16BIT -> {
                val shortCount = buffer.remaining() / BYTES_PER_SAMPLE
                val frames = shortCount / channelCount
                val mono = FloatArray(frames)
                val shortBuffer = buffer.slice().order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
                for (frameIndex in 0 until frames) {
                    var sum = 0f
                    val base = frameIndex * channelCount
                    for (ch in 0 until channelCount) {
                        val sample = shortBuffer.get(base + ch)
                        sum += sample / 32768f
                    }
                    mono[frameIndex] = sum / channelCount.toFloat()
                }
                mono
            }

            AudioFormat.ENCODING_PCM_FLOAT -> {
                val floatCount = buffer.remaining() / Float.SIZE_BYTES
                val frames = floatCount / channelCount
                val mono = FloatArray(frames)
                val floatBuffer = buffer.slice().order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer()
                for (frameIndex in 0 until frames) {
                    var sum = 0f
                    val base = frameIndex * channelCount
                    for (ch in 0 until channelCount) {
                        sum += floatBuffer.get(base + ch)
                    }
                    mono[frameIndex] = sum / channelCount.toFloat()
                }
                mono
            }

            else -> throw ProcessingException("UNSUPPORTED_AUDIO_FORMAT", "Unsupported PCM encoding: $pcmEncoding")
        }
    }

    private companion object {
        const val TAG = "MediaCodecAudioConverterDataSource"
    }
}

private fun MediaFormat.getIntegerSafely(key: String): Int? =
    if (containsKey(key)) getInteger(key) else null

private class StreamingLinearResampler(
    inputRate: Int,
    private val outputRate: Int,
) {
    private var inputRate: Int = inputRate.coerceAtLeast(1)
    private var step: Double = this.inputRate.toDouble() / outputRate.toDouble()
    private var sourcePos: Double = 0.0
    private var processedInputSamples: Long = 0L
    private var lastSample: Float = 0f
    private var hasLastSample: Boolean = false

    fun updateInputRate(newRate: Int) {
        val safe = newRate.coerceAtLeast(1)
        if (safe == inputRate) return

        inputRate = safe
        step = inputRate.toDouble() / outputRate.toDouble()
    }

    fun resampleToPcm16(inputMono: FloatArray): ShortArray {
        if (inputMono.isEmpty()) return ShortArray(0)

        if (inputRate == outputRate) {
            return ShortArray(inputMono.size) { index ->
                (inputMono[index] * 32767f).roundToInt().coerceIn(-32768, 32767).toShort()
            }
        }

        val expected = ((inputMono.size.toDouble() * outputRate.toDouble() / inputRate.toDouble()).toInt() + 16)
            .coerceAtLeast(0)
        var output = ShortArray(expected.coerceAtLeast(16))
        var outIndex = 0

        val bufferStart = processedInputSamples.toDouble()
        val bufferEnd = (processedInputSamples + inputMono.size).toDouble()

        while (true) {
            val localPos = sourcePos - bufferStart
            val leftIndex = floor(localPos).toInt()
            val rightIndex = leftIndex + 1

            val leftSample = when {
                leftIndex >= 0 -> inputMono.getOrNull(leftIndex)
                leftIndex == -1 && hasLastSample -> lastSample
                else -> null
            } ?: break

            val rightSample = when {
                rightIndex >= 0 -> inputMono.getOrNull(rightIndex)
                rightIndex == -1 && hasLastSample -> lastSample
                else -> null
            } ?: break

            val frac = (localPos - leftIndex.toDouble()).toFloat().coerceIn(0f, 1f)
            val value = leftSample * (1f - frac) + rightSample * frac

            if (outIndex >= output.size) {
                output = output.copyOf(output.size * 2)
            }
            output[outIndex] = (value * 32767f).roundToInt().coerceIn(-32768, 32767).toShort()
            outIndex += 1
            sourcePos += step

            if (sourcePos >= bufferEnd) {
                break
            }
        }

        lastSample = inputMono.last()
        hasLastSample = true
        processedInputSamples += inputMono.size.toLong()

        if (outIndex == output.size) return output
        return output.copyOf(outIndex)
    }
}

private fun writeWavHeader(
    raf: RandomAccessFile,
    sampleRate: Int,
    channels: Int,
    bitsPerSample: Int,
    dataSizeBytes: Int,
) {
    val byteRate = sampleRate * channels * (bitsPerSample / 8)
    val blockAlign = channels * (bitsPerSample / 8)

    val header = ByteBuffer.allocate(WAV_HEADER_BYTES)
        .order(ByteOrder.LITTLE_ENDIAN)
        .apply {
            put("RIFF".toByteArray())
            putInt(36 + dataSizeBytes)
            put("WAVE".toByteArray())
            put("fmt ".toByteArray())
            putInt(16)
            putShort(1)
            putShort(channels.toShort())
            putInt(sampleRate)
            putInt(byteRate)
            putShort(blockAlign.toShort())
            putShort(bitsPerSample.toShort())
            put("data".toByteArray())
            putInt(dataSizeBytes)
        }
        .array()

    raf.seek(0L)
    raf.write(header)
}

private fun patchWavHeaderSizes(
    raf: RandomAccessFile,
    dataSizeBytes: Int,
) {
    raf.seek(4L)
    raf.write(ByteBuffer.allocate(Int.SIZE_BYTES).order(ByteOrder.LITTLE_ENDIAN).putInt(36 + dataSizeBytes).array())
    raf.seek(40L)
    raf.write(ByteBuffer.allocate(Int.SIZE_BYTES).order(ByteOrder.LITTLE_ENDIAN).putInt(dataSizeBytes).array())
}

internal fun downmixToMono(pcmBytes: ByteArray, channels: Int): FloatArray {
    if (channels <= 0) {
        throw ProcessingException("UNSUPPORTED_AUDIO_FORMAT", "Invalid channel count: $channels")
    }

    val sampleCount = pcmBytes.size / BYTES_PER_SAMPLE
    if (sampleCount == 0) {
        return FloatArray(0)
    }

    val shorts = ShortArray(sampleCount)
    ByteBuffer.wrap(pcmBytes)
        .order(ByteOrder.LITTLE_ENDIAN)
        .asShortBuffer()
        .get(shorts)

    val frames = sampleCount / channels
    val mono = FloatArray(frames)
    for (frameIndex in 0 until frames) {
        var sum = 0f
        for (channelIndex in 0 until channels) {
            val sourceIndex = frameIndex * channels + channelIndex
            sum += shorts[sourceIndex] / 32768f
        }
        mono[frameIndex] = sum / channels.toFloat()
    }
    return mono
}

internal fun resampleLinear(input: FloatArray, inputRate: Int, outputRate: Int): ShortArray {
    if (inputRate <= 0) {
        throw ProcessingException("UNSUPPORTED_AUDIO_FORMAT", "Invalid input sample rate: $inputRate")
    }

    if (input.isEmpty()) {
        return ShortArray(0)
    }

    if (inputRate == outputRate) {
        return ShortArray(input.size) { index ->
            (input[index] * 32767f).roundToInt().coerceIn(-32768, 32767).toShort()
        }
    }

    val ratio = inputRate.toDouble() / outputRate.toDouble()
    val outputSize = (input.size / ratio).toInt().coerceAtLeast(1)
    val output = ShortArray(outputSize)

    for (index in 0 until outputSize) {
        val sourcePosition = index * ratio
        val leftIndex = sourcePosition.toInt().coerceIn(0, input.lastIndex)
        val rightIndex = (leftIndex + 1).coerceAtMost(input.lastIndex)
        val fraction = sourcePosition - leftIndex
        val interpolated = input[leftIndex] * (1.0 - fraction).toFloat() + input[rightIndex] * fraction.toFloat()
        output[index] = (interpolated * 32767f).roundToInt().coerceIn(-32768, 32767).toShort()
    }

    return output
}

internal fun writeWav(outputFile: File, pcmSamples: ShortArray, sampleRate: Int, channels: Int) {
    val byteRate = sampleRate * channels * BYTES_PER_SAMPLE
    val dataSize = pcmSamples.size * BYTES_PER_SAMPLE

    RandomAccessFile(outputFile, "rw").use { raf ->
        raf.setLength(0L)
        writeWavHeader(
            raf = raf,
            sampleRate = sampleRate,
            channels = channels,
            bitsPerSample = (BYTES_PER_SAMPLE * 8),
            dataSizeBytes = dataSize,
        )
        raf.seek(WAV_HEADER_BYTES.toLong())

        val outBytes = ByteArray(dataSize)
        val bb = ByteBuffer.wrap(outBytes).order(ByteOrder.LITTLE_ENDIAN)
        pcmSamples.forEach { sample -> bb.putShort(sample) }
        raf.write(outBytes)
    }
}

private const val BYTES_PER_SAMPLE = 2
private const val TARGET_SAMPLE_RATE = 16_000
private const val TIMEOUT_US = 10_000L
private const val WAV_HEADER_BYTES = 44
