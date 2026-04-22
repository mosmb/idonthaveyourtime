package io.morgan.idonthaveyourtime.core.domain.usecase

import com.google.common.truth.Truth.assertThat
import io.morgan.idonthaveyourtime.core.model.ModelId
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
}

