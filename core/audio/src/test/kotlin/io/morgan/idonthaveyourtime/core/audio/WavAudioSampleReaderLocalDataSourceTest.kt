package io.morgan.idonthaveyourtime.core.audio

import com.google.common.truth.Truth.assertThat
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Test

class WavAudioSampleReaderLocalDataSourceTest {

    @Test
    fun `read16kMonoWavBytes returns valid wav clip for requested segment`() = runTest {
        val wav = createTempWav(
            pcm16 = ShortArray(16_000) { index -> index.toShort() },
        )

        val reader = WavAudioSampleReaderLocalDataSource(StandardTestDispatcher(testScheduler))

        val bytes = reader.read16kMonoWavBytes(
            wavFilePath = wav.absolutePath,
            startMs = 100L,
            endMs = 200L,
        ).getOrThrow()

        assertThat(String(bytes.copyOfRange(0, 4))).isEqualTo("RIFF")
        assertThat(String(bytes.copyOfRange(8, 12))).isEqualTo("WAVE")
        assertThat(String(bytes.copyOfRange(12, 16))).isEqualTo("fmt ")
        assertThat(String(bytes.copyOfRange(36, 40))).isEqualTo("data")
        assertThat(bytes.readLittleEndianShort(22)).isEqualTo(1)
        assertThat(bytes.readLittleEndianInt(24)).isEqualTo(16_000)
        assertThat(bytes.readLittleEndianShort(34)).isEqualTo(16)

        val dataSizeBytes = bytes.readLittleEndianInt(40)
        assertThat(dataSizeBytes).isEqualTo(3_200)
        assertThat(bytes.size).isEqualTo(44 + dataSizeBytes)

        val payload = bytes.copyOfRange(44, bytes.size).toShortArray()
        assertThat(payload.size).isEqualTo(1_600)
        assertThat(payload.first()).isEqualTo(1_600.toShort())
        assertThat(payload.last()).isEqualTo(3_199.toShort())
    }

    @Test
    fun `read16kMonoFloats preserves existing segment reading behavior`() = runTest {
        val wav = createTempWav(
            pcm16 = ShortArray(16_000) { index ->
                when (index % 4) {
                    0 -> 16_384.toShort()
                    1 -> (-16_384).toShort()
                    2 -> 8_192.toShort()
                    else -> (-8_192).toShort()
                }
            },
        )

        val reader = WavAudioSampleReaderLocalDataSource(StandardTestDispatcher(testScheduler))

        val floats = reader.read16kMonoFloats(
            wavFilePath = wav.absolutePath,
            startMs = 0L,
            endMs = 250L,
        ).getOrThrow()

        assertThat(floats.size).isEqualTo(4_000)
        assertThat(floats[0]).isWithin(0.0001f).of(0.5f)
        assertThat(floats[1]).isWithin(0.0001f).of(-0.5f)
    }
}

private fun createTempWav(pcm16: ShortArray): File {
    val file = File.createTempFile("wav_reader_test", ".wav")
    file.deleteOnExit()
    file.writeBytes(buildWavBytes(pcm16))
    return file
}

private fun buildWavBytes(pcm16: ShortArray): ByteArray {
    val data = ByteBuffer.allocate(pcm16.size * Short.SIZE_BYTES)
        .order(ByteOrder.LITTLE_ENDIAN)
        .apply {
            pcm16.forEach { putShort(it) }
        }
        .array()
    val header = ByteBuffer.allocate(44)
        .order(ByteOrder.LITTLE_ENDIAN)
        .apply {
            put("RIFF".toByteArray())
            putInt(36 + data.size)
            put("WAVE".toByteArray())
            put("fmt ".toByteArray())
            putInt(16)
            putShort(1.toShort())
            putShort(1.toShort())
            putInt(16_000)
            putInt(16_000 * 2)
            putShort(2.toShort())
            putShort(16.toShort())
            put("data".toByteArray())
            putInt(data.size)
        }
        .array()
    return header + data
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

private fun ByteArray.toShortArray(): ShortArray {
    val shorts = ShortArray(size / Short.SIZE_BYTES)
    ByteBuffer.wrap(this)
        .order(ByteOrder.LITTLE_ENDIAN)
        .asShortBuffer()
        .get(shorts)
    return shorts
}
