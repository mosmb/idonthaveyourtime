package io.morgan.idonthaveyourtime.core.data.datasource.settings.impl

import com.google.common.truth.Truth.assertThat
import io.morgan.idonthaveyourtime.core.model.ProcessingConfig
import org.junit.Test

class TranscriptionModelFileNameNormalizerTest {

    @Test
    fun `normalizes legacy whisper bin transcription file names to post whisper default`() {
        val normalized = normalizeTranscriptionModelFileName("ggml-base-q5_1.bin")

        assertThat(normalized).isEqualTo(ProcessingConfig().transcriptionModelFileName)
    }

    @Test
    fun `keeps supported litertlm transcription file names`() {
        val normalized = normalizeTranscriptionModelFileName("gemma-4-E4B-it.litertlm")

        assertThat(normalized).isEqualTo("gemma-4-E4B-it.litertlm")
    }
}
