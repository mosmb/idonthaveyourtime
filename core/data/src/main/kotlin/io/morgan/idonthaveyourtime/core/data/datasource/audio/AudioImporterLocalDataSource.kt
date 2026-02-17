package io.morgan.idonthaveyourtime.core.data.datasource.audio

import io.morgan.idonthaveyourtime.core.model.ImportedAudio
import io.morgan.idonthaveyourtime.core.model.SharedAudioInput

/**
 * Imports a shared audio input into app-managed storage.
 *
 * This is a **local** data source: implementations must not perform network I/O.
 */
interface AudioImporterLocalDataSource {
    suspend fun importFromUri(
        sharedAudio: SharedAudioInput,
        sessionId: String,
    ): Result<ImportedAudio>
}

