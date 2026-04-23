package io.morgan.idonthaveyourtime.core.model

enum class SummarizerRuntime(
    val displayName: String,
) {
    LiteRtLm(displayName = "LiteRT-LM"),
    MediaPipeLlmInference(displayName = "MediaPipe LLM Inference"),
}
