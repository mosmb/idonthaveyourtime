package io.morgan.idonthaveyourtime.core.model

data class ProcessingConfig(
    val whisperModelSize: WhisperModelSize = WhisperModelSize.Base,
    val llmModelFileName: String = "Qwen2.5-0.5B-Instruct-Q4_K_M.gguf",
    val segmentationTargetSpeechMs: Long = 15_000,
    val segmentationOverlapMs: Long = 600,
    val mapEverySegments: Int = 3,
)
