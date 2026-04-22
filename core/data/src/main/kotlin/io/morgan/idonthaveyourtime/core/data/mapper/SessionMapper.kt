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
    val runtimeCompatibility = transcriptionRuntime
        ?.let(::resolveLegacyCompatibleRuntime)
        ?: return null
    val warmStart = transcriptionWarmStart ?: return null
    val totalMs = transcriptionTotalMs ?: return null
    val audioDurationMs = transcriptionAudioDurationMs ?: return null

    return SessionTranscriptionDiagnostics(
        runtime = runtimeCompatibility.runtime,
        backendName = transcriptionBackendName ?: runtimeCompatibility.backendName,
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

private fun resolveLegacyCompatibleRuntime(rawRuntime: String): RuntimeCompatibility? =
    when (rawRuntime) {
        LEGACY_WHISPER_RUNTIME -> RuntimeCompatibility(
            runtime = TranscriptionRuntime.Auto,
            backendName = LEGACY_WHISPER_BACKEND_NAME,
        )

        else -> runCatching { TranscriptionRuntime.valueOf(rawRuntime) }
            .getOrNull()
            ?.let { runtime ->
                RuntimeCompatibility(
                    runtime = runtime,
                    backendName = null,
                )
            }
    }

private data class RuntimeCompatibility(
    val runtime: TranscriptionRuntime,
    val backendName: String?,
)

private const val LEGACY_WHISPER_RUNTIME = "WhisperCpp"
private const val LEGACY_WHISPER_BACKEND_NAME = "whisper.cpp (legacy)"
