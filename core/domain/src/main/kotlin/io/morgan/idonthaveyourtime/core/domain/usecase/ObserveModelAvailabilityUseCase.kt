package io.morgan.idonthaveyourtime.core.domain.usecase

import io.morgan.idonthaveyourtime.core.domain.repository.ModelRepository
import io.morgan.idonthaveyourtime.core.model.ModelAvailability
import io.morgan.idonthaveyourtime.core.model.ModelId
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

class ObserveModelAvailabilityUseCase @Inject constructor(
    private val modelRepository: ModelRepository,
) {
    operator fun invoke(modelId: ModelId): Flow<ModelAvailability> =
        modelRepository.observeAvailability(modelId)
}
