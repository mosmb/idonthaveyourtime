package io.morgan.idonthaveyourtime.core.data.repository

import io.morgan.idonthaveyourtime.core.data.datasource.audio.AudioImporterLocalDataSource
import io.morgan.idonthaveyourtime.core.domain.repository.AudioImportRepository
import io.morgan.idonthaveyourtime.core.model.ImportedAudio
import io.morgan.idonthaveyourtime.core.model.SharedAudioInput
import javax.inject.Inject

internal class DefaultAudioImportRepository @Inject constructor(
    private val audioImporter: AudioImporterLocalDataSource,
) : AudioImportRepository {
    override suspend fun importFromUri(
        sharedAudio: SharedAudioInput,
        sessionId: String,
    ): Result<ImportedAudio> = audioImporter.importFromUri(
        sharedAudio = sharedAudio,
        sessionId = sessionId,
    )
}
