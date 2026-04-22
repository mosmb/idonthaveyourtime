package io.morgan.idonthaveyourtime.core.domain.usecase

import com.google.common.truth.Truth.assertThat
import io.morgan.idonthaveyourtime.core.model.ModelId
import io.morgan.idonthaveyourtime.core.model.SummarizerModelFormat
import io.morgan.idonthaveyourtime.core.model.SummarizerRuntime
import org.junit.Test

class GetSuggestedModelsUseCaseTest {

    private val useCase = GetSuggestedModelsUseCase()

    @Test
    fun `model ids do not expose whisper`() {
        assertThat(ModelId.entries.map { it.name }).doesNotContain("Whisper")
    }

    @Test
    fun `suggested models never include whisper artifacts`() {
        val suggestions = ModelId.entries.flatMap { modelId -> useCase(modelId) }

        assertThat(suggestions.map { it.huggingFaceRepoId }).doesNotContain("ggerganov/whisper.cpp")
        assertThat(suggestions.any { suggested -> suggested.fileName.trim().lowercase().endsWith(".bin") }).isFalse()
    }

    @Test
    fun `summarizer contracts do not expose llama or gguf`() {
        assertThat(SummarizerRuntime.entries.map { it.name }).doesNotContain("LlamaCpp")
        assertThat(SummarizerModelFormat.entries.map { it.name }).doesNotContain("Gguf")
    }

    @Test
    fun `suggested LLM models never include gguf artifacts`() {
        val suggestions = useCase(ModelId.Llm)

        assertThat(suggestions.any { it.fileName.trim().lowercase().endsWith(".gguf") }).isFalse()
        assertThat(suggestions.any { it.summarizerRuntime?.name == "LlamaCpp" }).isFalse()
        assertThat(suggestions.any { it.summarizerModelFormat?.fileExtension == "gguf" }).isFalse()
    }
}
