package io.morgan.idonthaveyourtime.core.model

data class ProcessingConfig(
    val transcriptionRuntime: TranscriptionRuntime = TranscriptionRuntime.Auto,
    val transcriptionModelFileName: String = "gemma-4-E2B-it.litertlm",
    val whisperModelSize: WhisperModelSize = WhisperModelSize.Base,
    val llmModelFileName: String = "gemma-4-E2B-it.litertlm",
    val summarizerRuntime: SummarizerRuntime = SummarizerRuntime.Auto,
    val segmentationTargetSpeechMs: Long = 15_000,
    val segmentationOverlapMs: Long = 600,
    val mapEverySegments: Int = 3,
)
