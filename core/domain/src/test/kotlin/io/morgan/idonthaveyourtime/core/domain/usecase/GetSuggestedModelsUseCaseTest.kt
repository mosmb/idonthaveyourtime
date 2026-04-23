package io.morgan.idonthaveyourtime.core.domain.usecase

import com.google.common.truth.Truth.assertThat
import io.morgan.idonthaveyourtime.core.model.ModelId
import io.morgan.idonthaveyourtime.core.model.SuggestedModel
import io.morgan.idonthaveyourtime.core.model.SummarizerModelFormat
import io.morgan.idonthaveyourtime.core.model.SummarizerRuntime
import org.junit.Test

class GetSuggestedModelsUseCaseTest {

    private val useCase = GetSuggestedModelsUseCase()

    @Test
    fun `model ids only include transcription and llm`() {
        assertThat(ModelId.entries)
            .containsExactly(ModelId.Transcription, ModelId.Llm)
            .inOrder()
    }

    @Test
    fun `suggested models only use supported file extensions`() {
        val suggestions = ModelId.entries.flatMap { modelId -> useCase(modelId) }

        assertThat(suggestions.map { suggested -> suggested.fileName.substringAfterLast('.') }.toSet())
            .containsExactly("litertlm", "task")
    }

    @Test
    fun `suggested model contract does not expose runtime metadata`() {
        val fieldNames = SuggestedModel::class.java.declaredFields.map { it.name }.toSet()

        assertThat(fieldNames).doesNotContain("transcriptionRuntime")
        assertThat(fieldNames).doesNotContain("summarizerRuntime")
    }

    @Test
    fun `summarizer contracts only expose supported runtimes and formats`() {
        assertThat(SummarizerRuntime.entries)
            .containsExactly(SummarizerRuntime.LiteRtLm, SummarizerRuntime.MediaPipeLlmInference)
            .inOrder()
        assertThat(SummarizerModelFormat.entries.map { it.fileExtension })
            .containsExactly("litertlm", "task")
            .inOrder()
    }

    @Test
    fun `suggested llm models only use supported formats`() {
        val suggestions = useCase(ModelId.Llm)

        assertThat(suggestions.mapNotNull { it.summarizerModelFormat?.fileExtension }.toSet())
            .containsExactly("litertlm", "task")
        assertThat(suggestions.map { it.fileName.substringAfterLast('.') }.toSet())
            .containsExactly("litertlm", "task")
    }
}
