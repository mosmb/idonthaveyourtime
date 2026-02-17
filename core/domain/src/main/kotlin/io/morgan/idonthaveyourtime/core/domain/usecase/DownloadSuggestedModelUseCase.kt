package io.morgan.idonthaveyourtime.core.domain.usecase

import io.morgan.idonthaveyourtime.core.domain.repository.ModelRepository
import io.morgan.idonthaveyourtime.core.model.SuggestedModel
import javax.inject.Inject

class DownloadSuggestedModelUseCase @Inject constructor(
    private val modelRepository: ModelRepository,
) {
    suspend operator fun invoke(model: SuggestedModel): Result<Unit> =
        modelRepository.enqueueDownload(model)
}
