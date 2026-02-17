package io.morgan.idonthaveyourtime.core.model

data class SegmentationConfig(
    val frameDurationMs: Int = 20,
    val targetSpeechMs: Long = 15_000,
    val maxSpeechMs: Long = 20_000,
    val boundaryPadMs: Long = 400,
    val overlapMs: Long = 600,
    val minSpeechStartMs: Long = 200,
    val minSilenceEndMs: Long = 300,
)
