package io.morgan.idonthaveyourtime.core.domain.usecase

import io.morgan.idonthaveyourtime.core.common.ProcessingException
import io.morgan.idonthaveyourtime.core.domain.repository.AudioImportRepository
import io.morgan.idonthaveyourtime.core.domain.repository.ProcessingQueueRepository
import io.morgan.idonthaveyourtime.core.domain.repository.SessionRepository
import io.morgan.idonthaveyourtime.core.model.ProcessingSession
import io.morgan.idonthaveyourtime.core.model.ProcessingStage
import io.morgan.idonthaveyourtime.core.model.SharedAudioInput
import java.util.UUID
import javax.inject.Inject
import kotlinx.datetime.Clock

class EnqueueSharedAudioUseCase @Inject constructor(
    private val sessionRepository: SessionRepository,
    private val audioImportRepository: AudioImportRepository,
    private val processingQueueRepository: ProcessingQueueRepository,
) {
    suspend operator fun invoke(sharedAudios: List<SharedAudioInput>): Result<List<String>> = runCatching {
        val sessionIds = mutableListOf<String>()

        sharedAudios.forEach { sharedAudio ->
            val sessionId = UUID.randomUUID().toString()
            val importingSession = ProcessingSession(
                id = sessionId,
                createdAtEpochMs = Clock.System.now().toEpochMilliseconds(),
                sourceName = sharedAudio.displayName,
                mimeType = sharedAudio.mimeType,
                durationMs = null,
                stage = ProcessingStage.Importing,
                progress = 0f,
                transcript = null,
                summary = null,
                languageCode = null,
                errorCode = null,
                errorMessage = null,
            )

            val importedAudio = audioImportRepository.importFromUri(sharedAudio, sessionId)
                .getOrElse { throwable ->
                    throw ProcessingException(
                        code = "IMPORT_FAILED",
                        message = throwable.message ?: "Unable to import shared audio",
                        cause = throwable,
                    )
                }

            val queuedSession = importingSession.copy(
                stage = ProcessingStage.Queued,
                progress = 0f,
            )

            sessionRepository.createSession(queuedSession, importedAudio.cachedFilePath)
                .getOrElse { throwable ->
                    throw ProcessingException(
                        code = "SESSION_CREATE_FAILED",
                        message = throwable.message ?: "Unable to persist session",
                        cause = throwable,
                    )
                }

            processingQueueRepository.enqueue(sessionId)
                .getOrElse { throwable ->
                    sessionRepository.setError(
                        sessionId = sessionId,
                        errorCode = "QUEUE_FAILED",
                        errorMessage = throwable.message ?: "Unable to schedule processing",
                    )
                    throw ProcessingException(
                        code = "QUEUE_FAILED",
                        message = throwable.message ?: "Unable to schedule processing",
                        cause = throwable,
                    )
                }

            sessionIds += sessionId
        }

        sessionIds
    }
}
