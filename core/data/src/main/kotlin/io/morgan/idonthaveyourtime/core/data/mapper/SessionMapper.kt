package io.morgan.idonthaveyourtime.core.data.mapper

import io.morgan.idonthaveyourtime.core.database.model.SessionEntity
import io.morgan.idonthaveyourtime.core.model.ProcessingSession
import io.morgan.idonthaveyourtime.core.model.ProcessingStage

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
    inputFilePath = inputFilePath,
    wavFilePath = wavFilePath,
)

private fun String.toProcessingStage(): ProcessingStage =
    ProcessingStage.entries.firstOrNull { it.name == this } ?: ProcessingStage.Error
