package io.morgan.idonthaveyourtime.core.domain.repository

import io.morgan.idonthaveyourtime.core.model.LanguageHint
import io.morgan.idonthaveyourtime.core.model.TranscriptionRequest
import io.morgan.idonthaveyourtime.core.model.TranscriptionResult
import io.morgan.idonthaveyourtime.core.model.TranscriptionEngineProbeResult

/**
 * Runs local speech-to-text transcription.
 */
interface TranscriptionRepository {
    suspend fun probe(): Result<TranscriptionEngineProbeResult>

    suspend fun transcribe(
        request: TranscriptionRequest,
        languageHint: LanguageHint = LanguageHint.Auto,
        onProgress: suspend (Float) -> Unit = {},
        onPartialResult: suspend (String) -> Unit = {},
    ): Result<TranscriptionResult>
}
