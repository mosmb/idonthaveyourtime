package io.morgan.idonthaveyourtime.core.data.datasource.model

import io.morgan.idonthaveyourtime.core.model.ModelAvailability
import io.morgan.idonthaveyourtime.core.model.ModelId
import kotlinx.coroutines.flow.Flow

/**
 * Resolves local model availability and file paths for a given [ModelId].
 *
 * This is a **local** data source: implementations must not perform network I/O.
 */
interface ModelLocatorLocalDataSource {
    fun observeAvailability(modelId: ModelId): Flow<ModelAvailability>
    suspend fun getModelPath(modelId: ModelId): Result<String>
}

