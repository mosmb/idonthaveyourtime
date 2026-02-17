package io.morgan.idonthaveyourtime.core.domain.usecase

import io.morgan.idonthaveyourtime.core.domain.repository.SessionRepository
import io.morgan.idonthaveyourtime.core.model.ProcessingSession
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

class ObserveSessionUseCase @Inject constructor(
    private val sessionRepository: SessionRepository,
) {
    operator fun invoke(sessionId: String): Flow<ProcessingSession?> =
        sessionRepository.observeSession(sessionId)
}
