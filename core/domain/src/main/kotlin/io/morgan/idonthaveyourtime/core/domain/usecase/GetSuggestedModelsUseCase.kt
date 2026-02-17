package io.morgan.idonthaveyourtime.core.domain.usecase

import io.morgan.idonthaveyourtime.core.model.ModelId
import io.morgan.idonthaveyourtime.core.model.SuggestedModel
import javax.inject.Inject

class GetSuggestedModelsUseCase @Inject constructor() {
    operator fun invoke(modelId: ModelId): List<SuggestedModel> = when (modelId) {
        ModelId.Whisper -> listOf(
            SuggestedModel(
                modelId = ModelId.Whisper,
                displayName = "Whisper Base (multilingual, Q5_1)",
                description = "Good default balance for transcription.",
                huggingFaceRepoId = "ggerganov/whisper.cpp",
                fileName = "ggml-base-q5_1.bin",
            ),
            SuggestedModel(
                modelId = ModelId.Whisper,
                displayName = "Whisper Base (English-only, Q5_1)",
                description = "Smaller/faster if you only need English.",
                huggingFaceRepoId = "ggerganov/whisper.cpp",
                fileName = "ggml-base.en-q5_1.bin",
            ),
            SuggestedModel(
                modelId = ModelId.Whisper,
                displayName = "Whisper Small (multilingual, Q5_1)",
                description = "Higher accuracy, heavier and slower.",
                huggingFaceRepoId = "ggerganov/whisper.cpp",
                fileName = "ggml-small-q5_1.bin",
            ),
            SuggestedModel(
                modelId = ModelId.Whisper,
                displayName = "Whisper Small (English-only, Q5_1)",
                description = "Higher accuracy for English, heavier and slower.",
                huggingFaceRepoId = "ggerganov/whisper.cpp",
                fileName = "ggml-small.en-q5_1.bin",
            ),
        )

        ModelId.Llm -> listOf(
            SuggestedModel(
                modelId = ModelId.Llm,
                displayName = "Qwen2.5 0.5B Instruct (Q4_K_M)",
                description = "Very small and fast; best for low-end devices.",
                huggingFaceRepoId = "bartowski/Qwen2.5-0.5B-Instruct-GGUF",
                fileName = "Qwen2.5-0.5B-Instruct-Q4_K_M.gguf",
            ),
            SuggestedModel(
                modelId = ModelId.Llm,
                displayName = "Qwen2.5 1.5B Instruct (Q4_K_M)",
                description = "Better quality than 0.5B; still relatively small.",
                huggingFaceRepoId = "bartowski/Qwen2.5-1.5B-Instruct-GGUF",
                fileName = "Qwen2.5-1.5B-Instruct-Q4_K_M.gguf",
            ),
            SuggestedModel(
                modelId = ModelId.Llm,
                displayName = "Gemma 2 2B IT (Q3_K_M)",
                description = "Higher quality, large download.",
                huggingFaceRepoId = "bartowski/gemma-2-2b-it-GGUF",
                fileName = "gemma-2-2b-it-Q3_K_M.gguf",
            ),
            SuggestedModel(
                modelId = ModelId.Llm,
                displayName = "Gemma 2 2B IT (Q4_K_M)",
                description = "Higher quality, large download (recommended for Gemma).",
                huggingFaceRepoId = "bartowski/gemma-2-2b-it-GGUF",
                fileName = "gemma-2-2b-it-Q4_K_M.gguf",
            ),
            SuggestedModel(
                modelId = ModelId.Llm,
                displayName = "Gemma 2 2B IT (Q5_K_M)",
                description = "Highest quality of these Gemma variants, very large.",
                huggingFaceRepoId = "bartowski/gemma-2-2b-it-GGUF",
                fileName = "gemma-2-2b-it-Q5_K_M.gguf",
            ),
        )
    }
}
