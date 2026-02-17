package io.morgan.idonthaveyourtime.core.domain.usecase

import io.morgan.idonthaveyourtime.core.domain.repository.ProcessingQueueRepository
import io.morgan.idonthaveyourtime.core.domain.repository.SessionRepository
import javax.inject.Inject

class CancelSessionUseCase @Inject constructor(
    private val processingQueueRepository: ProcessingQueueRepository,
    private val sessionRepository: SessionRepository,
) {
    suspend operator fun invoke(sessionId: String): Result<Unit> = runCatching {
        processingQueueRepository.cancel(sessionId).getOrThrow()
        sessionRepository.markCancelled(sessionId).getOrThrow()
    }
}
