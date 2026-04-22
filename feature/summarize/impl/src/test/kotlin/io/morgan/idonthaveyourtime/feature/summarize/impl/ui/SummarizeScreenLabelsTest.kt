package io.morgan.idonthaveyourtime.feature.summarize.impl.ui

import com.google.common.truth.Truth.assertThat
import io.morgan.idonthaveyourtime.core.model.ProcessingConfig
import io.morgan.idonthaveyourtime.core.model.TranscriptionRuntime
import org.junit.Test

class SummarizeScreenLabelsTest {

    @Test
    fun `transcription label shows google ai edge for litertlm models`() {
        val label = configuredTranscriptionLabel(
            ProcessingConfig(transcriptionModelFileName = "gemma-4-E2B-it.litertlm"),
        )

        assertThat(label).isEqualTo(
            "Transcription: ${TranscriptionRuntime.GoogleAiEdgeLiteRtLm.displayName} • gemma-4-E2B-it.litertlm",
        )
    }

    @Test
    fun `transcription label stays unconfigured for unsupported model formats`() {
        val label = configuredTranscriptionLabel(
            ProcessingConfig(transcriptionModelFileName = "legacy.bin"),
        )

        assertThat(label).isEqualTo("Transcription: Unconfigured • legacy.bin")
    }
}
