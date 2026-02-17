package io.morgan.idonthaveyourtime.core.audio

import com.google.common.truth.Truth.assertThat
import io.morgan.idonthaveyourtime.core.model.SegmentationConfig
import java.io.File
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class EnergyVadSegmenterTest {

    @Test
    fun `segment16kMonoWav returns empty when audio is silence`() = runTest {
        val wav = createTempWav(
            pcm16 = ShortArray(16_000 * 3),
        )

        val segmenter = EnergyVadSpeechSegmenterLocalDataSource(ioDispatcher = UnconfinedTestDispatcher(testScheduler))
        val segments = segmenter.segment16kMonoWav(
            wavFilePath = wav.absolutePath,
            config = SegmentationConfig(),
        ).getOrThrow()

        assertThat(segments).isEmpty()
    }

    @Test
    fun `segment16kMonoWav splits speech segments and applies overlap`() = runTest {
        val speech1 = sineWavePcm(durationMs = 1_000, amplitude = 10_000)
        val silence = ShortArray(16_000 / 2)
        val speech2 = sineWavePcm(durationMs = 1_000, amplitude = 10_000)
        val pcm = concat(speech1, silence, speech2)

        val wav = createTempWav(pcm16 = pcm)

        val segmenter = EnergyVadSpeechSegmenterLocalDataSource(ioDispatcher = UnconfinedTestDispatcher(testScheduler))
        val segments = segmenter.segment16kMonoWav(
            wavFilePath = wav.absolutePath,
            config = SegmentationConfig(),
        ).getOrThrow()
            .sortedBy { it.startMs }

        assertThat(segments).hasSize(2)
        val first = segments[0]
        val second = segments[1]

        assertThat(first.startMs).isEqualTo(0L)
        assertThat(first.endMs).isAtLeast(1_200L)
        assertThat(second.endMs).isEqualTo(2_500L)

        val overlapMs = first.endMs - second.startMs
        assertThat(overlapMs).isAtLeast(600L)
        assertThat(second.startMs).isAtLeast(0L)
    }

    @Test
    fun `segment16kMonoWav detects speech above noise floor`() = runTest {
        val noise1 = noisePcm(durationMs = 1_000, amplitude = 100, seed = 0)
        val speech = sineWavePcm(durationMs = 1_000, amplitude = 10_000)
        val noise2 = noisePcm(durationMs = 1_000, amplitude = 100, seed = 1)
        val wav = createTempWav(pcm16 = concat(noise1, speech, noise2))

        val segmenter = EnergyVadSpeechSegmenterLocalDataSource(ioDispatcher = UnconfinedTestDispatcher(testScheduler))
        val segments = segmenter.segment16kMonoWav(
            wavFilePath = wav.absolutePath,
            config = SegmentationConfig(),
        ).getOrThrow()
            .sortedBy { it.startMs }

        assertThat(segments).hasSize(1)
        val segment = segments.single()
        assertThat(segment.startMs).isAtMost(1_000L)
        assertThat(segment.endMs).isAtLeast(2_000L)
    }
}

private fun createTempWav(pcm16: ShortArray): File {
    val file = File.createTempFile("vad_test", ".wav")
    file.deleteOnExit()
    writeWav(
        outputFile = file,
        pcmSamples = pcm16,
        sampleRate = 16_000,
        channels = 1,
    )
    return file
}

private fun sineWavePcm(
    durationMs: Int,
    amplitude: Int,
    sampleRate: Int = 16_000,
    frequencyHz: Double = 440.0,
): ShortArray {
    val sampleCount = (durationMs * sampleRate) / 1_000
    val out = ShortArray(sampleCount)
    for (i in 0 until sampleCount) {
        val t = i.toDouble() / sampleRate.toDouble()
        val v = sin(2.0 * PI * frequencyHz * t) * amplitude.toDouble()
        out[i] = v.toInt().coerceIn(-32768, 32767).toShort()
    }
    return out
}

private fun noisePcm(
    durationMs: Int,
    amplitude: Int,
    seed: Int,
    sampleRate: Int = 16_000,
): ShortArray {
    val random = Random(seed)
    val sampleCount = (durationMs * sampleRate) / 1_000
    return ShortArray(sampleCount) {
        random.nextInt(-amplitude, amplitude + 1).toShort()
    }
}

private fun concat(vararg arrays: ShortArray): ShortArray {
    val total = arrays.sumOf { it.size }
    val out = ShortArray(total)
    var offset = 0
    for (array in arrays) {
        array.copyInto(out, destinationOffset = offset)
        offset += array.size
    }
    return out
}
