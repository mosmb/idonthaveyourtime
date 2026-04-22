package io.morgan.idonthaveyourtime.core.model

data class ProcessingConfig(
    val transcriptionModelFileName: String = "gemma-4-E2B-it.litertlm",
    val llmModelFileName: String = "gemma-4-E2B-it.litertlm",
    val segmentationTargetSpeechMs: Long = 15_000,
    val segmentationOverlapMs: Long = 600,
    val mapEverySegments: Int = 3,
)
