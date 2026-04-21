package io.morgan.idonthaveyourtime.core.data.repository

import io.morgan.idonthaveyourtime.core.data.datasource.transcription.TranscriptionEngineLocalDataSource
import io.morgan.idonthaveyourtime.core.domain.repository.TranscriptionRepository
import io.morgan.idonthaveyourtime.core.model.LanguageHint
import io.morgan.idonthaveyourtime.core.model.TranscriptionRequest
import io.morgan.idonthaveyourtime.core.model.TranscriptionEngineProbeResult
import io.morgan.idonthaveyourtime.core.model.TranscriptionResult
import javax.inject.Inject

internal class DefaultTranscriptionRepository @Inject constructor(
    private val transcriptionEngine: TranscriptionEngineLocalDataSource,
) : TranscriptionRepository {
    override suspend fun probe(): Result<TranscriptionEngineProbeResult> =
        transcriptionEngine.probe()

    override suspend fun transcribe(
        request: TranscriptionRequest,
        languageHint: LanguageHint,
        onProgress: suspend (Float) -> Unit,
        onPartialResult: suspend (String) -> Unit,
    ): Result<TranscriptionResult> = transcriptionEngine.transcribe(
        request = request,
        languageHint = languageHint,
        onProgress = onProgress,
        onPartialResult = onPartialResult,
    )
}
