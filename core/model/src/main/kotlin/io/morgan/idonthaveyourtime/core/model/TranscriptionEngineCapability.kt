package io.morgan.idonthaveyourtime.core.model

data class TranscriptionEngineCapability(
    val runtime: TranscriptionRuntime,
    val supportedFormats: Set<TranscriptionModelFormat>,
    val supportsStreaming: Boolean,
    val supportsAsyncGeneration: Boolean,
    val supportsHardwareAcceleration: Boolean,
)
