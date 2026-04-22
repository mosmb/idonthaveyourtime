package io.morgan.idonthaveyourtime.core.data.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import io.morgan.idonthaveyourtime.core.model.ModelId
import java.io.File
import java.io.IOException
import kotlin.math.absoluteValue
import kotlin.math.roundToInt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber

@HiltWorker
class DownloadModelWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParameters: WorkerParameters,
    private val service: ModelDownloadService,
) : CoroutineWorker(appContext, workerParameters) {

    override suspend fun doWork(): Result {
        val modelIdRaw = inputData.getString(KEY_MODEL_ID)
        val modelId = resolveSupportedDownloadModelId(modelIdRaw)
            .getOrElse { throwable -> return failure(throwable.message ?: "Unsupported model id") }
        val displayName = inputData.getString(KEY_DISPLAY_NAME) ?: modelId.name
        val url = inputData.getString(KEY_URL)
            ?: return failure("Missing download url")
        val targetFileName = inputData.getString(KEY_TARGET_FILE_NAME)
            ?: return failure("Missing target file name")
        val notificationId = BASE_NOTIFICATION_ID + (modelId.ordinal % 1000)

        setForeground(createForegroundInfo(displayName, notificationId, progressPercent = null))
        setProgress(Data.Builder().putInt(KEY_PROGRESS_PERCENT, 0).build())

        val modelsDir = File(applicationContext.filesDir, MODELS_DIRECTORY).apply { mkdirs() }
        val tempFile = File(modelsDir, "$targetFileName.part")
        val finalFile = File(modelsDir, targetFileName)

        return try {
            Timber.tag(TAG).i(
                "Download start modelId=%s displayName=%s url=%s target=%s",
                modelId.name,
                displayName,
                url,
                finalFile.absolutePath,
            )

            downloadToTempFile(
                url = url,
                tempFile = tempFile,
                displayName = displayName,
                notificationId = notificationId,
            )

            require(tempFile.exists()) { "Downloaded file missing after transfer" }
            require(tempFile.length() > 0L) { "Downloaded file is empty" }

            if (finalFile.exists()) {
                finalFile.delete()
            }

            if (!tempFile.renameTo(finalFile)) {
                tempFile.copyTo(finalFile, overwrite = true)
                tempFile.delete()
            }

            setProgress(Data.Builder().putInt(KEY_PROGRESS_PERCENT, 100).build())
            setForeground(createForegroundInfo(displayName, notificationId, progressPercent = 100))

            Timber.tag(TAG).i(
                "Download done modelId=%s path=%s sizeBytes=%d",
                modelId.name,
                finalFile.absolutePath,
                finalFile.length(),
            )

            Result.success()
        } catch (cancelled: CancellationException) {
            tempFile.delete()
            throw cancelled
        } catch (throwable: Throwable) {
            tempFile.delete()
            Timber.tag(TAG).e(throwable, "Download failed modelId=%s", modelId.name)
            failure(throwable.message ?: "Download failed")
        }
    }

    private suspend fun downloadToTempFile(
        url: String,
        tempFile: File,
        displayName: String,
        notificationId: Int,
    ) {
        withContext(Dispatchers.IO) {
            val response = service.download(url)
            if (!response.isSuccessful) {
                val errorBody = runCatching { response.errorBody()?.string()?.trim()?.take(200) }.getOrNull()
                throw IOException("HTTP ${response.code()} ${response.message()}".trim() + (errorBody?.let { ": $it" } ?: ""))
            }

            val body = response.body() ?: throw IOException("Empty response body")
            body.use { responseBody ->
                val contentLength = responseBody.contentLength().coerceAtLeast(-1L)
                val indeterminate = contentLength <= 0L

                setForeground(createForegroundInfo(displayName, notificationId, progressPercent = null, indeterminate = indeterminate))

                tempFile.outputStream().use { output ->
                    responseBody.byteStream().use { input ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        var totalRead = 0L
                        var lastBucket = -1
                        var lastBytesUpdate = 0L

                        while (true) {
                            if (isStopped) throw CancellationException("Stopped")
                            val read = input.read(buffer)
                            if (read == -1) break
                            output.write(buffer, 0, read)
                            totalRead += read

                            if (contentLength > 0L) {
                                val percent = ((totalRead.toDouble() / contentLength.toDouble()) * 100.0)
                                    .roundToInt()
                                    .coerceIn(0, 100)
                                val bucket = (percent / 5) * 5
                                if (bucket != lastBucket) {
                                    lastBucket = bucket
                                    setProgress(Data.Builder().putInt(KEY_PROGRESS_PERCENT, percent).build())
                                    setForeground(createForegroundInfo(displayName, notificationId, progressPercent = percent))
                                }
                            } else {
                                val step = 512 * 1024L
                                if (totalRead - lastBytesUpdate >= step) {
                                    lastBytesUpdate = totalRead
                                    setProgress(Data.Builder().putInt(KEY_PROGRESS_PERCENT, 0).build())
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun createForegroundInfo(
        displayName: String,
        notificationId: Int,
        progressPercent: Int?,
        indeterminate: Boolean = progressPercent == null,
    ): ForegroundInfo {
        ensureNotificationChannel()

        val builder = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("Downloading model")
            .setContentText(displayName)
            .setOngoing(true)

        if (progressPercent != null) {
            builder.setProgress(100, progressPercent.coerceIn(0, 100), false)
        } else {
            builder.setProgress(0, 0, indeterminate)
        }

        val notification = builder.build()
        return ForegroundInfo(
            notificationId,
            notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
        )
    }

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Model downloads",
            NotificationManager.IMPORTANCE_LOW,
        )
        manager.createNotificationChannel(channel)
    }

    private fun failure(message: String): Result =
        Result.failure(
            Data.Builder()
                .putString(KEY_ERROR_MESSAGE, message)
                .build()
        )

    companion object {
        const val KEY_MODEL_ID = "model_id"
        const val KEY_DISPLAY_NAME = "display_name"
        const val KEY_URL = "url"
        const val KEY_TARGET_FILE_NAME = "target_file_name"

        const val KEY_PROGRESS_PERCENT = "progress_percent"
        const val KEY_ERROR_MESSAGE = "error_message"

        private const val TAG = "DownloadModelWorker"

        private const val MODELS_DIRECTORY = "models"

        private const val CHANNEL_ID = "model_download_channel"
        private const val BASE_NOTIFICATION_ID = 2000
    }
}

internal fun resolveSupportedDownloadModelId(modelIdRaw: String?): Result<ModelId> = runCatching {
    val rawModelId = modelIdRaw?.trim()?.takeIf { it.isNotEmpty() }
        ?: throw IllegalArgumentException("Missing model id")

    ModelId.entries.firstOrNull { modelId -> modelId.name == rawModelId }
        ?: throw IllegalArgumentException("Unsupported model id: $rawModelId")
}
