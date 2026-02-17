package io.morgan.idonthaveyourtime.core.domain.usecase

import io.morgan.idonthaveyourtime.core.domain.repository.ModelRepository
import io.morgan.idonthaveyourtime.core.model.ModelId
import javax.inject.Inject

class CancelModelDownloadUseCase @Inject constructor(
    private val modelRepository: ModelRepository,
) {
    suspend operator fun invoke(modelId: ModelId): Result<Unit> =
        modelRepository.cancelDownload(modelId)
}
