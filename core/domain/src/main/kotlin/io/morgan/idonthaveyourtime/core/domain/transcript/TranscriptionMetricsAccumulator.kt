package io.morgan.idonthaveyourtime.core.domain.transcript

import io.morgan.idonthaveyourtime.core.model.SessionTranscriptionDiagnostics
import io.morgan.idonthaveyourtime.core.model.TranscriptionMetrics

class TranscriptionMetricsAccumulator {
    private var aggregate: SessionTranscriptionDiagnostics? = null

    fun record(metrics: TranscriptionMetrics) {
        aggregate = aggregate?.merge(metrics) ?: metrics.asSessionDiagnostics()
    }

    fun snapshot(): SessionTranscriptionDiagnostics? = aggregate

    private fun SessionTranscriptionDiagnostics.merge(next: TranscriptionMetrics): SessionTranscriptionDiagnostics {
        val combinedAudioDurationMs = audioDurationMs + next.audioDurationMs
        val combinedTotalMs = totalMs + next.totalMs

        return copy(
            backendName = backendName ?: next.backendName,
            modelFileName = modelFileName ?: next.modelFileName,
            modelLoadMs = modelLoadMs ?: next.modelLoadMs,
            firstTextMs = firstTextMs ?: next.firstTextMs,
            totalMs = combinedTotalMs,
            audioDurationMs = combinedAudioDurationMs,
            audioSecondsPerWallSecond = if (combinedTotalMs > 0L) {
                combinedAudioDurationMs.toDouble() / combinedTotalMs.toDouble()
            } else {
                null
            },
            fallbackReason = fallbackReason ?: next.fallbackReason,
            failureReason = failureReason ?: next.failureReason,
            deviceLabel = deviceLabel ?: next.deviceLabel,
        )
    }

    private fun TranscriptionMetrics.asSessionDiagnostics(): SessionTranscriptionDiagnostics =
        SessionTranscriptionDiagnostics(
            runtime = runtime,
            backendName = backendName,
            modelFileName = modelFileName,
            warmStart = warmStart,
            modelLoadMs = modelLoadMs,
            firstTextMs = firstTextMs,
            totalMs = totalMs,
            audioDurationMs = audioDurationMs,
            audioSecondsPerWallSecond = audioSecondsPerWallSecond,
            fallbackReason = fallbackReason,
            failureReason = failureReason,
            deviceLabel = deviceLabel,
        )
}
