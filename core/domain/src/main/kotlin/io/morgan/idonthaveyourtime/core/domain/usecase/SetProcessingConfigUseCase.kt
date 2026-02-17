package io.morgan.idonthaveyourtime.core.domain.usecase

import io.morgan.idonthaveyourtime.core.domain.repository.ProcessingConfigRepository
import io.morgan.idonthaveyourtime.core.model.ProcessingConfig
import javax.inject.Inject

class SetProcessingConfigUseCase @Inject constructor(
    private val processingConfigRepository: ProcessingConfigRepository,
) {
    suspend operator fun invoke(config: ProcessingConfig): Result<Unit> =
        processingConfigRepository.setConfig(config)
}
