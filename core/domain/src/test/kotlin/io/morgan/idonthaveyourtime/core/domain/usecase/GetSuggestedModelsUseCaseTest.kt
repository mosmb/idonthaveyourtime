package io.morgan.idonthaveyourtime.core.domain.usecase

import com.google.common.truth.Truth.assertThat
import io.morgan.idonthaveyourtime.core.model.ModelId
import io.morgan.idonthaveyourtime.core.model.TranscriptionModelFormat
import io.morgan.idonthaveyourtime.core.model.TranscriptionRuntime
import org.junit.Test

class GetSuggestedModelsUseCaseTest {

    private val useCase = GetSuggestedModelsUseCase()

    @Test
    fun `model ids only expose transcription and llm entries`() {
        assertThat(ModelId.entries).containsExactly(
            ModelId.Transcription,
            ModelId.Llm,
        ).inOrder()
    }

    @Test
    fun `transcription suggestions only expose Google AI Edge litertlm models`() {
        val suggestions = useCase(ModelId.Transcription)

        assertThat(suggestions).isNotEmpty()
        assertThat(suggestions.map { it.modelId }.distinct()).containsExactly(ModelId.Transcription)
        assertThat(suggestions.map { it.transcriptionRuntime }.distinct())
            .containsExactly(TranscriptionRuntime.GoogleAiEdgeLiteRtLm)
        assertThat(suggestions.map { it.transcriptionModelFormat }.distinct())
            .containsExactly(TranscriptionModelFormat.LiteRtLm)
        assertThat(suggestions.map { it.fileName.substringAfterLast('.') }.distinct())
            .containsExactly(TranscriptionModelFormat.LiteRtLm.fileExtension)
    }

    @Test
    fun `llm suggestions remain scoped to llm model downloads`() {
        val suggestions = useCase(ModelId.Llm)

        assertThat(suggestions).isNotEmpty()
        assertThat(suggestions.map { it.modelId }.distinct()).containsExactly(ModelId.Llm)
    }
}
