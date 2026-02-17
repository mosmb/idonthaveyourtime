package io.morgan.idonthaveyourtime.core.domain.usecase

import io.morgan.idonthaveyourtime.core.domain.repository.ProcessingQueueRepository
import io.morgan.idonthaveyourtime.core.domain.repository.SessionRepository
import io.morgan.idonthaveyourtime.core.model.ProcessingStage
import javax.inject.Inject

class RequeueSessionUseCase @Inject constructor(
    private val sessionRepository: SessionRepository,
    private val processingQueueRepository: ProcessingQueueRepository,
) {
    suspend operator fun invoke(sessionId: String): Result<Unit> = runCatching {
        sessionRepository.updateStage(sessionId, ProcessingStage.Queued, progress = 0f).getOrThrow()
        processingQueueRepository.enqueue(sessionId).getOrThrow()
    }
}
