package io.morgan.idonthaveyourtime.core.domain.repository

import io.morgan.idonthaveyourtime.core.model.ImportedAudio
import io.morgan.idonthaveyourtime.core.model.SharedAudioInput

/**
 * Imports a shared audio input into app-managed storage for a processing session.
 *
 * Implementations are responsible for validating the input, copying bytes into a stable local file,
 * and returning enough metadata for the rest of the processing pipeline.
 */
interface AudioImportRepository {
    suspend fun importFromUri(
        sharedAudio: SharedAudioInput,
        sessionId: String,
    ): Result<ImportedAudio>
}
