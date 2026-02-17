package io.morgan.idonthaveyourtime.core.data.repository

import io.morgan.idonthaveyourtime.core.data.datasource.transcription.TranscriptionEngineLocalDataSource
import io.morgan.idonthaveyourtime.core.domain.repository.TranscriptionRepository
import io.morgan.idonthaveyourtime.core.model.LanguageHint
import io.morgan.idonthaveyourtime.core.model.Transcript
import javax.inject.Inject

internal class DefaultTranscriptionRepository @Inject constructor(
    private val transcriptionEngine: TranscriptionEngineLocalDataSource,
) : TranscriptionRepository {
    override suspend fun transcribe(
        audioData: FloatArray,
        languageHint: LanguageHint,
        onProgress: suspend (Float) -> Unit,
    ): Result<Transcript> = transcriptionEngine.transcribe(
        audioData = audioData,
        languageHint = languageHint,
        onProgress = onProgress,
    )
}
