package io.morgan.idonthaveyourtime.core.domain.usecase

import io.morgan.idonthaveyourtime.core.model.ModelId
import io.morgan.idonthaveyourtime.core.model.SuggestedModel
import io.morgan.idonthaveyourtime.core.model.SummarizerModelFormat
import io.morgan.idonthaveyourtime.core.model.TranscriptionModelFormat
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
                transcriptionModelFormat = TranscriptionModelFormat.LiteRtLm,
            ),
            SuggestedModel(
                modelId = ModelId.Transcription,
                displayName = "Gemma 4 E4B IT",
                description = "Higher-quality Google AI Edge transcription model. Heavier memory footprint.",
                huggingFaceRepoId = "litert-community/gemma-4-E4B-it-litert-lm",
                fileName = "gemma-4-E4B-it.litertlm",
                transcriptionModelFormat = TranscriptionModelFormat.LiteRtLm,
            ),
            SuggestedModel(
                modelId = ModelId.Transcription,
                displayName = "Gemma 3n E2B IT",
                description = "Gallery-proven audio-capable Gemma 3n model for on-device transcription.",
                huggingFaceRepoId = "google/gemma-3n-E2B-it-litert-lm",
                fileName = "gemma-3n-E2B-it-int4.litertlm",
                transcriptionModelFormat = TranscriptionModelFormat.LiteRtLm,
            ),
            SuggestedModel(
                modelId = ModelId.Transcription,
                displayName = "Gemma 3n E4B IT",
                description = "Higher-quality Gemma 3n audio model. Larger download and memory use.",
                huggingFaceRepoId = "google/gemma-3n-E4B-it-litert-lm",
                fileName = "gemma-3n-E4B-it-int4.litertlm",
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
                summarizerModelFormat = SummarizerModelFormat.LiteRtLm,
            ),
            SuggestedModel(
                modelId = ModelId.Llm,
                displayName = "Gemma 4 E2B IT",
                description = "Preferred LiteRT-LM path. Google AI Edge runtime, streamed partials, accelerator-first.",
                huggingFaceRepoId = "litert-community/gemma-4-E2B-it-litert-lm",
                fileName = "gemma-4-E2B-it.litertlm",
                summarizerModelFormat = SummarizerModelFormat.LiteRtLm,
            ),
            SuggestedModel(
                modelId = ModelId.Llm,
                displayName = "Qwen2.5 0.5B Instruct",
                description = "MediaPipe LLM Inference `.task` sample. Smaller, fast, and public for Android import/download flows.",
                huggingFaceRepoId = "diamondbelema/edu-hive-llm-models",
                fileName = "Qwen2.5-0.5B-Instruct_multi-prefill-seq_q8_ekv1280.task",
                summarizerModelFormat = SummarizerModelFormat.Task,
            ),
            SuggestedModel(
                modelId = ModelId.Llm,
                displayName = "Gemma 3 270M IT",
                description = "Very small MediaPipe `.task` option for faster first-token latency on constrained devices.",
                huggingFaceRepoId = "diamondbelema/edu-hive-llm-models",
                fileName = "gemma3-270m-it-q8.task",
                summarizerModelFormat = SummarizerModelFormat.Task,
            ),
        )
    }
}
