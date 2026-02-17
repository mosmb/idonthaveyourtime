package io.morgan.idonthaveyourtime.core.domain.usecase

import io.morgan.idonthaveyourtime.core.domain.repository.SessionRepository
import io.morgan.idonthaveyourtime.core.model.ProcessingSession
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

class ObserveRecentSessionsUseCase @Inject constructor(
    private val sessionRepository: SessionRepository,
) {
    operator fun invoke(limit: Int = 20): Flow<List<ProcessingSession>> =
        sessionRepository.observeRecentSessions(limit)
}
