package io.morgan.idonthaveyourtime.core.data.datasource.processing.impl

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import io.morgan.idonthaveyourtime.core.data.datasource.processing.TempFileCleanerLocalDataSource
import io.morgan.idonthaveyourtime.core.data.di.IoDispatcher
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import timber.log.Timber

internal class CacheTempFileCleanerLocalDataSource @Inject constructor(
    @ApplicationContext private val context: Context,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : TempFileCleanerLocalDataSource {
    override suspend fun cleanupSessionFiles(sessionId: String): Result<Unit> =
        withContext(ioDispatcher) {
            runCatching {
                val sessionDirectory = File(context.cacheDir, "sessions/$sessionId")
                if (!sessionDirectory.exists()) {
                    Timber.tag(TAG).d("Cleanup skip sessionId=%s (missing dir)", sessionId)
                    return@runCatching
                }

                Timber.tag(TAG).i("Cleanup start sessionId=%s dir=%s", sessionId, sessionDirectory.absolutePath)
                sessionDirectory.deleteRecursively()
                Timber.tag(TAG).i("Cleanup done sessionId=%s", sessionId)
            }
        }

    private companion object {
        const val TAG = "TempFileCleaner"
    }
}
