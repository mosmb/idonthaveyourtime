package io.morgan.idonthaveyourtime.core.model

data class SummarizerEngineCapability(
    val runtime: SummarizerRuntime,
    val supportedFormats: Set<SummarizerModelFormat>,
    val supportsStreaming: Boolean,
    val supportsAsyncGeneration: Boolean,
    val supportsHardwareAcceleration: Boolean,
)
