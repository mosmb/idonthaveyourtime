package io.morgan.idonthaveyourtime.core.domain.usecase

import io.morgan.idonthaveyourtime.core.model.ModelId
import io.morgan.idonthaveyourtime.core.model.SuggestedModel
import io.morgan.idonthaveyourtime.core.model.SummarizerModelFormat
import io.morgan.idonthaveyourtime.core.model.SummarizerRuntime
import io.morgan.idonthaveyourtime.core.model.TranscriptionModelFormat
import io.morgan.idonthaveyourtime.core.model.TranscriptionRuntime
import javax.inject.Inject

class GetSuggestedModelsUseCase @Inject constructor() {
    operator fun invoke(modelId: ModelId): List<SuggestedModel> = when (modelId) {
        ModelId.Transcription -> listOf(
            SuggestedModel(
                modelId = ModelId.Transcription,
                displayName = "Gemma 4 E2B IT",
                description = "Current Google AI Edge Gallery default for Audio Scribe. Audio-capable LiteRT-LM path.",
                huggingFaceRepoId = "litert-community/gemma-4-E2B-it-litert-lm",
                fileName = "gemma-4-E2B-it.litertlm",
                transcriptionRuntime = TranscriptionRuntime.GoogleAiEdgeLiteRtLm,
                transcriptionModelFormat = TranscriptionModelFormat.LiteRtLm,
            ),
            SuggestedModel(
                modelId = ModelId.Transcription,
                displayName = "Gemma 4 E4B IT",
                description = "Higher-quality Google AI Edge transcription model. Heavier memory footprint.",
                huggingFaceRepoId = "litert-community/gemma-4-E4B-it-litert-lm",
                fileName = "gemma-4-E4B-it.litertlm",
                transcriptionRuntime = TranscriptionRuntime.GoogleAiEdgeLiteRtLm,
                transcriptionModelFormat = TranscriptionModelFormat.LiteRtLm,
            ),
            SuggestedModel(
                modelId = ModelId.Transcription,
                displayName = "Gemma 3n E2B IT",
                description = "Gallery-proven audio-capable Gemma 3n model for on-device transcription.",
                huggingFaceRepoId = "google/gemma-3n-E2B-it-litert-lm",
                fileName = "gemma-3n-E2B-it-int4.litertlm",
                transcriptionRuntime = TranscriptionRuntime.GoogleAiEdgeLiteRtLm,
                transcriptionModelFormat = TranscriptionModelFormat.LiteRtLm,
            ),
            SuggestedModel(
                modelId = ModelId.Transcription,
                displayName = "Gemma 3n E4B IT",
                description = "Higher-quality Gemma 3n audio model. Larger download and memory use.",
                huggingFaceRepoId = "google/gemma-3n-E4B-it-litert-lm",
                fileName = "gemma-3n-E4B-it-int4.litertlm",
                transcriptionRuntime = TranscriptionRuntime.GoogleAiEdgeLiteRtLm,
                transcriptionModelFormat = TranscriptionModelFormat.LiteRtLm,
            ),
        )

        ModelId.Llm -> listOf(
            SuggestedModel(
                modelId = ModelId.Llm,
                displayName = "Gemma 4 E4B IT",
                description = "Gallery-aligned LiteRT-LM option with higher quality than E2B. Larger download and heavier memory use.",
                huggingFaceRepoId = "litert-community/gemma-4-E4B-it-litert-lm",
                fileName = "gemma-4-E4B-it.litertlm",
                summarizerRuntime = SummarizerRuntime.LiteRtLm,
                summarizerModelFormat = SummarizerModelFormat.LiteRtLm,
            ),
            SuggestedModel(
                modelId = ModelId.Llm,
                displayName = "Gemma 4 E2B IT",
                description = "Preferred LiteRT-LM path. Google AI Edge runtime, streamed partials, accelerator-first.",
                huggingFaceRepoId = "litert-community/gemma-4-E2B-it-litert-lm",
                fileName = "gemma-4-E2B-it.litertlm",
                summarizerRuntime = SummarizerRuntime.LiteRtLm,
                summarizerModelFormat = SummarizerModelFormat.LiteRtLm,
            ),
            SuggestedModel(
                modelId = ModelId.Llm,
                displayName = "Qwen2.5 0.5B Instruct",
                description = "MediaPipe LLM Inference `.task` sample. Smaller, fast, and public for Android import/download flows.",
                huggingFaceRepoId = "diamondbelema/edu-hive-llm-models",
                fileName = "Qwen2.5-0.5B-Instruct_multi-prefill-seq_q8_ekv1280.task",
                summarizerRuntime = SummarizerRuntime.MediaPipeLlmInference,
                summarizerModelFormat = SummarizerModelFormat.Task,
            ),
            SuggestedModel(
                modelId = ModelId.Llm,
                displayName = "Gemma 3 270M IT",
                description = "Very small MediaPipe `.task` option for faster first-token latency on constrained devices.",
                huggingFaceRepoId = "diamondbelema/edu-hive-llm-models",
                fileName = "gemma3-270m-it-q8.task",
                summarizerRuntime = SummarizerRuntime.MediaPipeLlmInference,
                summarizerModelFormat = SummarizerModelFormat.Task,
            ),
            SuggestedModel(
                modelId = ModelId.Llm,
                displayName = "Qwen2.5 0.5B Instruct (Q4_K_M)",
                description = "llama.cpp fallback. Small GGUF model for broad compatibility.",
                huggingFaceRepoId = "bartowski/Qwen2.5-0.5B-Instruct-GGUF",
                fileName = "Qwen2.5-0.5B-Instruct-Q4_K_M.gguf",
                summarizerRuntime = SummarizerRuntime.LlamaCpp,
                summarizerModelFormat = SummarizerModelFormat.Gguf,
            ),
            SuggestedModel(
                modelId = ModelId.Llm,
                displayName = "Qwen2.5 1.5B Instruct (Q4_K_M)",
                description = "llama.cpp fallback with better quality than 0.5B, but heavier.",
                huggingFaceRepoId = "bartowski/Qwen2.5-1.5B-Instruct-GGUF",
                fileName = "Qwen2.5-1.5B-Instruct-Q4_K_M.gguf",
                summarizerRuntime = SummarizerRuntime.LlamaCpp,
                summarizerModelFormat = SummarizerModelFormat.Gguf,
            ),
            SuggestedModel(
                modelId = ModelId.Llm,
                displayName = "Gemma 2 2B IT (Q4_K_M)",
                description = "Higher-quality llama.cpp fallback. Large GGUF download.",
                huggingFaceRepoId = "bartowski/gemma-2-2b-it-GGUF",
                fileName = "gemma-2-2b-it-Q4_K_M.gguf",
                summarizerRuntime = SummarizerRuntime.LlamaCpp,
                summarizerModelFormat = SummarizerModelFormat.Gguf,
            ),
        )
    }
}
