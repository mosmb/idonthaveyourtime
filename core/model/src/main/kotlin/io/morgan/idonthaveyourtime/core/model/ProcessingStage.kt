package io.morgan.idonthaveyourtime.core.model

enum class ProcessingStage {
    Idle,
    Importing,
    Queued,
    Converting,
    Transcribing,
    Summarizing,
    Success,
    Error,
    Cancelled,
}
