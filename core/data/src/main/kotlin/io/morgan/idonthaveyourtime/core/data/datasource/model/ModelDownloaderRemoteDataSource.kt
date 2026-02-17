package io.morgan.idonthaveyourtime.core.data.datasource.model

import io.morgan.idonthaveyourtime.core.model.ModelId
import io.morgan.idonthaveyourtime.core.model.RemoteModelDownloadState
import io.morgan.idonthaveyourtime.core.model.SuggestedModel
import kotlinx.coroutines.flow.Flow

/**
 * Downloads model files from a remote source and exposes download progress.
 *
 * This is a **remote** data source: implementations may perform network I/O.
 */
interface ModelDownloaderRemoteDataSource {
    suspend fun enqueue(model: SuggestedModel): Result<Unit>
    suspend fun cancel(modelId: ModelId): Result<Unit>
    fun observeDownloadState(modelId: ModelId): Flow<RemoteModelDownloadState>
}

