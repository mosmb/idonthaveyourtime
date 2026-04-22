package io.morgan.idonthaveyourtime.core.model

enum class TranscriptionRuntime(
    val displayName: String,
) {
    Auto("Auto"),
    GoogleAiEdgeLiteRtLm("Google AI Edge (LiteRT-LM)"),
}
