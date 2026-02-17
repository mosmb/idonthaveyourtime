package io.morgan.idonthaveyourtime.core.data.datasource.transcription

import io.morgan.idonthaveyourtime.core.model.LanguageHint
import io.morgan.idonthaveyourtime.core.model.Transcript

/**
 * Performs speech-to-text transcription locally.
 *
 * This is a **local** data source: implementations must not perform network I/O.
 */
interface TranscriptionEngineLocalDataSource {
    suspend fun transcribe(
        audioData: FloatArray,
        languageHint: LanguageHint = LanguageHint.Auto,
        onProgress: suspend (Float) -> Unit = {},
    ): Result<Transcript>
}

