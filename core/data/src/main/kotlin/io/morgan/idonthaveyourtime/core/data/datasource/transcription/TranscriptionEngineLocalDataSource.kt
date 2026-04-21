package io.morgan.idonthaveyourtime.core.data.datasource.transcription

import io.morgan.idonthaveyourtime.core.model.LanguageHint
import io.morgan.idonthaveyourtime.core.model.TranscriptionEngineCapability
import io.morgan.idonthaveyourtime.core.model.TranscriptionEngineProbeResult
import io.morgan.idonthaveyourtime.core.model.TranscriptionRequest
import io.morgan.idonthaveyourtime.core.model.TranscriptionResult

/**
 * Performs speech-to-text transcription locally.
 *
 * This is a **local** data source: implementations must not perform network I/O.
 */
interface TranscriptionEngineLocalDataSource {
    fun capability(): TranscriptionEngineCapability

    suspend fun probe(): Result<TranscriptionEngineProbeResult>

    suspend fun transcribe(
        request: TranscriptionRequest,
        languageHint: LanguageHint = LanguageHint.Auto,
        onProgress: suspend (Float) -> Unit = {},
        onPartialResult: suspend (String) -> Unit = {},
    ): Result<TranscriptionResult>
}
