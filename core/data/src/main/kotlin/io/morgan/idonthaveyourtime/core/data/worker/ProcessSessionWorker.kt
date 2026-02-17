package io.morgan.idonthaveyourtime.core.data.worker

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import io.morgan.idonthaveyourtime.core.common.ProcessingException
import io.morgan.idonthaveyourtime.core.data.datasource.processing.TempFileCleanerLocalDataSource
import io.morgan.idonthaveyourtime.core.domain.repository.SessionRepository
import io.morgan.idonthaveyourtime.core.domain.usecase.ProcessSessionUseCase
import io.morgan.idonthaveyourtime.core.model.ProcessingSession
import io.morgan.idonthaveyourtime.core.model.ProcessingStage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import kotlin.system.measureTimeMillis

@HiltWorker
class ProcessSessionWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParameters: WorkerParameters,
    private val processSessionUseCase: ProcessSessionUseCase,
    private val sessionRepository: SessionRepository,
    private val tempFileCleaner: TempFileCleanerLocalDataSource,
) : CoroutineWorker(appContext, workerParameters) {

    override suspend fun doWork(): Result = coroutineScope {
        val sessionId = inputData.getString(KEY_SESSION_ID)
            ?: run {
                Timber.e("ProcessSessionWorker missing session id input")
                return@coroutineScope Result.failure()
            }

        Timber.tag(TAG).i("ProcessSessionWorker start sessionId=%s attempt=%d", sessionId, runAttemptCount)
        setForeground(createForegroundInfo(null))
        Timber.tag(TAG).d("ProcessSessionWorker foreground set sessionId=%s", sessionId)

        val notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val observerJob = launch {
            var lastStage: ProcessingStage? = null
            var lastBucket = -1
            sessionRepository.observeSession(sessionId)
                .filterNotNull()
                .collect { session ->
                    val percent = (session.progress * 100).toInt().coerceIn(0, 100)
                    val bucket = (percent / 5) * 5
                    if (session.stage == lastStage && bucket == lastBucket) {
                        return@collect
                    }

                    lastStage = session.stage
                    lastBucket = bucket
                    notificationManager.notify(
                        NOTIFICATION_ID,
                        buildNotification(session, bucket),
                    )
                }
        }

        var pipelineResult: kotlin.Result<Unit>? = null
        val elapsedMs = measureTimeMillis {
            pipelineResult = runCatching { processSessionUseCase(sessionId) }.getOrElse { throwable ->
                kotlin.Result.failure(throwable)
            }
        }
        val resolvedResult = requireNotNull(pipelineResult) { "ProcessSessionUseCase returned no kotlin.Result" }

        Timber.tag(TAG).i(
            "ProcessSessionWorker finished sessionId=%s elapsedMs=%d stopped=%s",
            sessionId,
            elapsedMs,
            isStopped,
        )

        observerJob.cancel()

        resolvedResult.fold(
            onSuccess = {
                Timber.d("ProcessSessionWorker succeeded for sessionId=%s", sessionId)
                tempFileCleaner.cleanupSessionFiles(sessionId)
                Result.success()
            },
            onFailure = { throwable ->
                Timber.e(throwable, "ProcessSessionWorker failed for sessionId=%s", sessionId)
                if (isStopped || throwable is CancellationException) {
                    Timber.tag(TAG).w("ProcessSessionWorker stopped; marking session cancelled sessionId=%s", sessionId)
                    withContext(NonCancellable) {
                        sessionRepository.markCancelled(sessionId)
                    }
                } else {
                    val code = (throwable as? ProcessingException)?.code ?: "PIPELINE_FAILED"
                    val message = throwable.message ?: "Processing failed"
                    sessionRepository.setError(sessionId, code, message)
                }
                Result.success()
            },
        )
    }

    private fun createForegroundInfo(session: ProcessingSession?): ForegroundInfo {
        ensureNotificationChannel()
        val notification: Notification = buildNotification(session, progressPercent = null)

        return ForegroundInfo(
            NOTIFICATION_ID,
            notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
        )
    }

    private fun buildNotification(
        session: ProcessingSession?,
        progressPercent: Int?,
    ): Notification {
        val title = session?.sourceName?.takeIf { it.isNotBlank() } ?: "Local Audio Summarizer"
        val stage = session?.stage ?: ProcessingStage.Queued

        val text = when (stage) {
            ProcessingStage.Importing -> "Importing audio"
            ProcessingStage.Queued -> "Queued"
            ProcessingStage.Converting -> "Converting to WAV"
            ProcessingStage.Transcribing -> "Transcribing"
            ProcessingStage.Summarizing -> "Summarizing"
            ProcessingStage.Success -> "Done"
            ProcessingStage.Error -> "Failed"
            ProcessingStage.Cancelled -> "Cancelled"
            ProcessingStage.Idle -> "Idle"
        }

        val builder = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle(title)
            .setContentText(text)
            .setOngoing(stage in setOf(
                ProcessingStage.Importing,
                ProcessingStage.Queued,
                ProcessingStage.Converting,
                ProcessingStage.Transcribing,
                ProcessingStage.Summarizing,
            ))

        if (progressPercent != null && stage in setOf(
                ProcessingStage.Converting,
                ProcessingStage.Transcribing,
                ProcessingStage.Summarizing,
            )
        ) {
            builder.setProgress(100, progressPercent.coerceIn(0, 100), false)
        } else if (stage in setOf(
                ProcessingStage.Converting,
                ProcessingStage.Transcribing,
                ProcessingStage.Summarizing,
            )
        ) {
            builder.setProgress(0, 0, true)
        }

        return builder.build()
    }

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return
        }

        val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val existing = manager.getNotificationChannel(CHANNEL_ID)
        if (existing != null) {
            return
        }

        val channel = NotificationChannel(
            CHANNEL_ID,
            "Traitement audio",
            NotificationManager.IMPORTANCE_LOW,
        )
        manager.createNotificationChannel(channel)
    }

    companion object {
        const val KEY_SESSION_ID = "session_id"
        const val UNIQUE_QUEUE_NAME = "audio_processing_queue"
        const val WORK_TAG_PREFIX = "process_session_"

        private const val CHANNEL_ID = "processing_channel"
        private const val NOTIFICATION_ID = 1337
        private const val TAG = "ProcessSessionWorker"
    }
}
