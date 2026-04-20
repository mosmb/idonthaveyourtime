package io.morgan.idonthaveyourtime.core.model

enum class SummarizerRuntime(
    val displayName: String,
) {
    Auto(displayName = "Auto"),
    LiteRtLm(displayName = "LiteRT-LM"),
    MediaPipeLlmInference(displayName = "MediaPipe LLM Inference"),
    LlamaCpp(displayName = "llama.cpp"),
}
