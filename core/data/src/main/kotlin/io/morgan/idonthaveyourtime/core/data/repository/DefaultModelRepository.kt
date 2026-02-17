package io.morgan.idonthaveyourtime.core.data.repository

import io.morgan.idonthaveyourtime.core.data.datasource.model.ModelDownloaderRemoteDataSource
import io.morgan.idonthaveyourtime.core.data.datasource.model.ModelLocatorLocalDataSource
import io.morgan.idonthaveyourtime.core.data.datasource.model.ModelStoreLocalDataSource
import io.morgan.idonthaveyourtime.core.domain.repository.ModelRepository
import io.morgan.idonthaveyourtime.core.model.ModelAvailability
import io.morgan.idonthaveyourtime.core.model.ModelId
import io.morgan.idonthaveyourtime.core.model.RemoteModelDownloadState
import io.morgan.idonthaveyourtime.core.model.SuggestedModel
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged

internal class DefaultModelRepository @Inject constructor(
    private val localModelLocator: ModelLocatorLocalDataSource,
    private val remoteModelDownloader: ModelDownloaderRemoteDataSource,
    private val localModelStore: ModelStoreLocalDataSource,
) : ModelRepository {

    override fun observeAvailability(modelId: ModelId): Flow<ModelAvailability> =
        combine(
            localModelLocator.observeAvailability(modelId),
            remoteModelDownloader.observeDownloadState(modelId),
        ) { localAvailability, downloadState ->
            if (localAvailability == ModelAvailability.Ready) {
                ModelAvailability.Ready
            } else {
                when (downloadState) {
                    is RemoteModelDownloadState.Downloading -> ModelAvailability.Downloading(progress = downloadState.progress)
                    is RemoteModelDownloadState.Failed -> ModelAvailability.Failed(downloadState.message)
                    RemoteModelDownloadState.Idle -> ModelAvailability.Missing
                }
            }
        }.distinctUntilChanged()

    override suspend fun getModelPath(modelId: ModelId): Result<String> =
        localModelLocator.getModelPath(modelId)

    override suspend fun enqueueDownload(model: SuggestedModel): Result<Unit> {
        if (localModelStore.findModelFileInUserStorage(listOf(model.fileName)) != null) {
            return Result.success(Unit)
        }
        if (localModelStore.hasBundledAssetModel(listOf(model.fileName))) {
            return Result.success(Unit)
        }
        return remoteModelDownloader.enqueue(model)
    }

    override suspend fun cancelDownload(modelId: ModelId): Result<Unit> =
        remoteModelDownloader.cancel(modelId)
}
