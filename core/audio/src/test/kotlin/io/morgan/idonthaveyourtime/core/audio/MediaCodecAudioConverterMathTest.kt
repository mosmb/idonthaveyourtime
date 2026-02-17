package io.morgan.idonthaveyourtime.core.audio

import com.google.common.truth.Truth.assertThat
import io.morgan.idonthaveyourtime.core.common.ProcessingException
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import org.junit.Test

class MediaCodecAudioConverterMathTest {

    @Test
    fun `downmixToMono averages multi-channel frames`() {
        val pcm = shortArrayOf(10_000, -10_000, 30_000, 30_000).toByteArray()

        val mono = downmixToMono(pcmBytes = pcm, channels = 2)

        assertThat(mono).hasLength(2)
        assertThat(abs(mono[0])).isLessThan(0.001f)
        assertThat(mono[1]).isGreaterThan(0.9f)
    }

    @Test
    fun `resampleLinear downsamples expected sample count`() {
        val input = FloatArray(48) { index -> index / 48f }

        val output = resampleLinear(
            input = input,
            inputRate = 48_000,
            outputRate = 16_000,
        )

        assertThat(output).hasLength(16)
        assertThat(output.first()).isEqualTo(0)
        assertThat(output.last()).isGreaterThan(10_000)
    }

    @Test(expected = ProcessingException::class)
    fun `resampleLinear rejects invalid source sample rate`() {
        resampleLinear(
            input = floatArrayOf(0.1f, 0.2f),
            inputRate = 0,
            outputRate = 16_000,
        )
    }

    @Test
    fun `writeWav writes valid little-endian header`() {
        val file = File.createTempFile("wav_header_test", ".wav")
        file.deleteOnExit()

        val samples = shortArrayOf(100, -100, 200, -200)
        writeWav(
            outputFile = file,
            pcmSamples = samples,
            sampleRate = 16_000,
            channels = 1,
        )

        val bytes = file.readBytes()
        assertThat(String(bytes.copyOfRange(0, 4))).isEqualTo("RIFF")
        assertThat(String(bytes.copyOfRange(8, 12))).isEqualTo("WAVE")
        assertThat(String(bytes.copyOfRange(12, 16))).isEqualTo("fmt ")
        assertThat(String(bytes.copyOfRange(36, 40))).isEqualTo("data")
        assertThat(bytes.size).isEqualTo(44 + (samples.size * 2))

        val channels = bytes.readLittleEndianShort(offset = 22)
        val sampleRate = bytes.readLittleEndianInt(offset = 24)
        val bitsPerSample = bytes.readLittleEndianShort(offset = 34)
        val dataSize = bytes.readLittleEndianInt(offset = 40)

        assertThat(channels).isEqualTo(1)
        assertThat(sampleRate).isEqualTo(16_000)
        assertThat(bitsPerSample).isEqualTo(16)
        assertThat(dataSize).isEqualTo(samples.size * 2)
    }
}

private fun ShortArray.toByteArray(): ByteArray {
    val buffer = ByteBuffer.allocate(size * 2).order(ByteOrder.LITTLE_ENDIAN)
    forEach { buffer.putShort(it) }
    return buffer.array()
}

private fun ByteArray.readLittleEndianInt(offset: Int): Int =
    ByteBuffer.wrap(this, offset, Int.SIZE_BYTES)
        .order(ByteOrder.LITTLE_ENDIAN)
        .int

private fun ByteArray.readLittleEndianShort(offset: Int): Int =
    ByteBuffer.wrap(this, offset, Short.SIZE_BYTES)
        .order(ByteOrder.LITTLE_ENDIAN)
        .short
        .toInt()
