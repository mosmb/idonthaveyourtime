package io.morgan.idonthaveyourtime.core.data.datasource.processing.impl

import android.content.Context
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import io.morgan.idonthaveyourtime.core.data.worker.ProcessSessionWorker
import io.morgan.idonthaveyourtime.core.data.datasource.processing.ProcessingQueueLocalDataSource
import javax.inject.Inject
import timber.log.Timber

internal class WorkManagerProcessingQueueLocalDataSource @Inject constructor(
    @ApplicationContext context: Context,
) : ProcessingQueueLocalDataSource {

    private val workManager: WorkManager = WorkManager.getInstance(context)

    override suspend fun enqueue(sessionId: String): Result<Unit> =
        runCatching {
            Timber.tag(TAG).i("Queue enqueue sessionId=%s", sessionId)

            val inputData = Data.Builder()
                .putString(ProcessSessionWorker.KEY_SESSION_ID, sessionId)
                .build()

            val request = OneTimeWorkRequestBuilder<ProcessSessionWorker>()
                .addTag(ProcessSessionWorker.WORK_TAG_PREFIX + sessionId)
                .setInputData(inputData)
                .build()

            workManager.enqueueUniqueWork(
                ProcessSessionWorker.UNIQUE_QUEUE_NAME,
                ExistingWorkPolicy.APPEND_OR_REPLACE,
                request,
            )
            Unit
        }.onFailure { throwable ->
            Timber.tag(TAG).e(throwable, "Queue enqueue failed sessionId=%s", sessionId)
        }

    override suspend fun cancel(sessionId: String): Result<Unit> =
        runCatching {
            Timber.tag(TAG).i("Queue cancel sessionId=%s", sessionId)
            workManager.cancelAllWorkByTag(ProcessSessionWorker.WORK_TAG_PREFIX + sessionId)
            Unit
        }.onFailure { throwable ->
            Timber.tag(TAG).e(throwable, "Queue cancel failed sessionId=%s", sessionId)
        }

    private companion object {
        const val TAG = "ProcessingQueue"
    }
}
