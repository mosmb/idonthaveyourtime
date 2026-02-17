package io.morgan.idonthaveyourtime.core.domain.usecase

import io.morgan.idonthaveyourtime.core.domain.repository.ProcessingConfigRepository
import io.morgan.idonthaveyourtime.core.model.ProcessingConfig
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

class ObserveProcessingConfigUseCase @Inject constructor(
    private val processingConfigRepository: ProcessingConfigRepository,
) {
    operator fun invoke(): Flow<ProcessingConfig> =
        processingConfigRepository.observeConfig()
}
