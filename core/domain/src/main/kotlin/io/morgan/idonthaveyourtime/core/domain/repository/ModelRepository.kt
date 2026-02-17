package io.morgan.idonthaveyourtime.core.domain.repository

import io.morgan.idonthaveyourtime.core.model.ModelAvailability
import io.morgan.idonthaveyourtime.core.model.ModelId
import io.morgan.idonthaveyourtime.core.model.SuggestedModel
import kotlinx.coroutines.flow.Flow

/**
 * Provides access to local model availability and triggers model downloads.
 *
 * Implementations decide how models are stored, resolved, and downloaded (if missing).
 */
interface ModelRepository {
    fun observeAvailability(modelId: ModelId): Flow<ModelAvailability>
    suspend fun getModelPath(modelId: ModelId): Result<String>
    suspend fun enqueueDownload(model: SuggestedModel): Result<Unit>
    suspend fun cancelDownload(modelId: ModelId): Result<Unit>
}
