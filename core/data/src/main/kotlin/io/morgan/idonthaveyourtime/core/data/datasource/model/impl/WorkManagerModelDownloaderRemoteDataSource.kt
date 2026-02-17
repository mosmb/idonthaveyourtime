package io.morgan.idonthaveyourtime.core.data.datasource.model.impl

import android.content.Context
import androidx.lifecycle.Observer
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import io.morgan.idonthaveyourtime.core.data.worker.DownloadModelWorker
import io.morgan.idonthaveyourtime.core.data.datasource.model.ModelDownloaderRemoteDataSource
import io.morgan.idonthaveyourtime.core.model.ModelId
import io.morgan.idonthaveyourtime.core.model.RemoteModelDownloadState
import io.morgan.idonthaveyourtime.core.model.SuggestedModel
import javax.inject.Inject
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import timber.log.Timber

internal class WorkManagerModelDownloaderRemoteDataSource @Inject constructor(
    @ApplicationContext context: Context,
) : ModelDownloaderRemoteDataSource {

    private val workManager: WorkManager = WorkManager.getInstance(context)

    override suspend fun enqueue(model: SuggestedModel): Result<Unit> = runCatching {
        Timber.tag(TAG).i(
            "Model download enqueue modelId=%s displayName=%s fileName=%s repo=%s",
            model.modelId,
            model.displayName,
            model.fileName,
            model.huggingFaceRepoId,
        )

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val input = Data.Builder()
            .putString(DownloadModelWorker.KEY_MODEL_ID, model.modelId.name)
            .putString(DownloadModelWorker.KEY_DISPLAY_NAME, model.displayName)
            .putString(DownloadModelWorker.KEY_URL, model.downloadUrl)
            .putString(DownloadModelWorker.KEY_TARGET_FILE_NAME, model.fileName)
            .build()

        val request = OneTimeWorkRequestBuilder<DownloadModelWorker>()
            .setConstraints(constraints)
            .setInputData(input)
            .build()

        workManager.enqueueUniqueWork(
            uniqueWorkName(model.modelId),
            ExistingWorkPolicy.REPLACE,
            request,
        )
        Unit
    }.onFailure { throwable ->
        Timber.tag(TAG).e(throwable, "Model download enqueue failed modelId=%s", model.modelId)
    }

    override suspend fun cancel(modelId: ModelId): Result<Unit> = runCatching {
        Timber.tag(TAG).i("Model download cancel modelId=%s", modelId)
        workManager.cancelUniqueWork(uniqueWorkName(modelId))
        Unit
    }.onFailure { throwable ->
        Timber.tag(TAG).e(throwable, "Model download cancel failed modelId=%s", modelId)
    }

    override fun observeDownloadState(modelId: ModelId): Flow<RemoteModelDownloadState> =
        observeWorkInfosForUniqueWork(uniqueWorkName(modelId))
            .map { workInfos -> workInfos.toRemoteDownloadState() }
            .distinctUntilChanged()
            .catch { emit(RemoteModelDownloadState.Idle) }
            .onStart { emit(RemoteModelDownloadState.Idle) }

    private fun observeWorkInfosForUniqueWork(uniqueWorkName: String): Flow<List<WorkInfo>> = callbackFlow {
        val liveData = workManager.getWorkInfosForUniqueWorkLiveData(uniqueWorkName)
        val observer = Observer<List<WorkInfo>> { infos ->
            trySend(infos ?: emptyList())
        }
        liveData.observeForever(observer)
        awaitClose { liveData.removeObserver(observer) }
    }.onStart { emit(emptyList()) }

    private fun List<WorkInfo>.toRemoteDownloadState(): RemoteModelDownloadState {
        if (any { info -> !info.state.isFinished }) {
            val active = first { info -> !info.state.isFinished }
            val percent = active.progress
                .getInt(DownloadModelWorker.KEY_PROGRESS_PERCENT, 0)
                .coerceIn(0, 100)
            return RemoteModelDownloadState.Downloading(progress = (percent / 100f).coerceIn(0f, 1f))
        }

        val failed = firstOrNull { info -> info.state == WorkInfo.State.FAILED }
        if (failed != null) {
            val message = failed.outputData.getString(DownloadModelWorker.KEY_ERROR_MESSAGE)
                ?: "Download failed"
            return RemoteModelDownloadState.Failed(message)
        }

        return RemoteModelDownloadState.Idle
    }

    private fun uniqueWorkName(modelId: ModelId): String =
        UNIQUE_WORK_PREFIX + modelId.name

    private companion object {
        const val TAG = "RemoteModelDownloader"
        const val UNIQUE_WORK_PREFIX = "download_model_"
    }
}
