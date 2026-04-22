package io.morgan.idonthaveyourtime.core.data.mapper

import io.morgan.idonthaveyourtime.core.database.model.SessionEntity
import io.morgan.idonthaveyourtime.core.model.ProcessingSession
import io.morgan.idonthaveyourtime.core.model.ProcessingStage
import io.morgan.idonthaveyourtime.core.model.SessionTranscriptionDiagnostics
import io.morgan.idonthaveyourtime.core.model.TranscriptionRuntime

internal fun SessionEntity.asModel(): ProcessingSession = ProcessingSession(
    id = id,
    createdAtEpochMs = createdAtEpochMs,
    sourceName = sourceName,
    mimeType = mimeType,
    durationMs = durationMs,
    stage = stage.toProcessingStage(),
    progress = progress,
    transcript = transcript,
    summary = summary,
    languageCode = languageCode,
    errorCode = errorCode,
    errorMessage = errorMessage,
    transcriptionDiagnostics = asTranscriptionDiagnostics(),
)

internal fun ProcessingSession.asEntity(inputFilePath: String, wavFilePath: String?): SessionEntity = SessionEntity(
    id = id,
    createdAtEpochMs = createdAtEpochMs,
    sourceName = sourceName,
    mimeType = mimeType,
    durationMs = durationMs,
    stage = stage.name,
    progress = progress,
    transcript = transcript,
    summary = summary,
    languageCode = languageCode,
    errorCode = errorCode,
    errorMessage = errorMessage,
    transcriptionRuntime = transcriptionDiagnostics?.runtime?.name,
    transcriptionBackendName = transcriptionDiagnostics?.backendName,
    transcriptionModelFileName = transcriptionDiagnostics?.modelFileName,
    transcriptionWarmStart = transcriptionDiagnostics?.warmStart,
    transcriptionModelLoadMs = transcriptionDiagnostics?.modelLoadMs,
    transcriptionFirstTextMs = transcriptionDiagnostics?.firstTextMs,
    transcriptionTotalMs = transcriptionDiagnostics?.totalMs,
    transcriptionAudioDurationMs = transcriptionDiagnostics?.audioDurationMs,
    transcriptionAudioSecondsPerWallSecond = transcriptionDiagnostics?.audioSecondsPerWallSecond,
    transcriptionFallbackReason = transcriptionDiagnostics?.fallbackReason,
    transcriptionFailureReason = transcriptionDiagnostics?.failureReason,
    transcriptionDeviceLabel = transcriptionDiagnostics?.deviceLabel,
    inputFilePath = inputFilePath,
    wavFilePath = wavFilePath,
)

private fun String.toProcessingStage(): ProcessingStage =
    ProcessingStage.entries.firstOrNull { it.name == this } ?: ProcessingStage.Error

private fun SessionEntity.asTranscriptionDiagnostics(): SessionTranscriptionDiagnostics? {
    val runtime = transcriptionRuntime
        ?.takeIf { rawRuntime -> rawRuntime == TranscriptionRuntime.GoogleAiEdgeLiteRtLm.name }
        ?.let { TranscriptionRuntime.GoogleAiEdgeLiteRtLm }
        ?: return null
    val warmStart = transcriptionWarmStart ?: return null
    val totalMs = transcriptionTotalMs ?: return null
    val audioDurationMs = transcriptionAudioDurationMs ?: return null

    return SessionTranscriptionDiagnostics(
        runtime = runtime,
        backendName = transcriptionBackendName,
        modelFileName = transcriptionModelFileName,
        warmStart = warmStart,
        modelLoadMs = transcriptionModelLoadMs,
        firstTextMs = transcriptionFirstTextMs,
        totalMs = totalMs,
        audioDurationMs = audioDurationMs,
        audioSecondsPerWallSecond = transcriptionAudioSecondsPerWallSecond,
        fallbackReason = transcriptionFallbackReason,
        failureReason = transcriptionFailureReason,
        deviceLabel = transcriptionDeviceLabel,
    )
}
