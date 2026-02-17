package io.morgan.idonthaveyourtime.core.domain.repository

import io.morgan.idonthaveyourtime.core.model.LanguageHint
import io.morgan.idonthaveyourtime.core.model.Transcript

/**
 * Runs local speech-to-text transcription over audio samples.
 *
 * The input is expected to be normalized mono PCM samples (typically 16kHz), but concrete details
 * are up to the implementation.
 */
interface TranscriptionRepository {
    suspend fun transcribe(
        audioData: FloatArray,
        languageHint: LanguageHint = LanguageHint.Auto,
        onProgress: suspend (Float) -> Unit = {},
    ): Result<Transcript>
}
