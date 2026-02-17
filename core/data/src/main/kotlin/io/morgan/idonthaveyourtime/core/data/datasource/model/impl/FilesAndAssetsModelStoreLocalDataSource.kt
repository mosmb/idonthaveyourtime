package io.morgan.idonthaveyourtime.core.data.datasource.model.impl

import android.content.Context
import android.os.FileObserver
import dagger.hilt.android.qualifiers.ApplicationContext
import io.morgan.idonthaveyourtime.core.data.datasource.model.ModelStoreLocalDataSource
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.onStart
import timber.log.Timber

internal class FilesAndAssetsModelStoreLocalDataSource @Inject constructor(
    @ApplicationContext private val context: Context,
) : ModelStoreLocalDataSource {

    override fun observeModelsDirectoryChanges(): Flow<Unit> = callbackFlow {
        val directory = modelsDirectory()
        val observer = object : FileObserver(
            directory,
            CREATE or MOVED_TO or CLOSE_WRITE or DELETE or MOVED_FROM,
        ) {
            override fun onEvent(event: Int, path: String?) {
                trySend(Unit)
            }
        }

        observer.startWatching()
        awaitClose { observer.stopWatching() }
    }.onStart { emit(Unit) }

    override fun findModelFileInUserStorage(fileNames: List<String>): File? {
        val directory = modelsDirectory()
        return fileNames
            .asSequence()
            .map { modelFileName -> File(directory, modelFileName) }
            .firstOrNull { modelFile -> modelFile.exists() && modelFile.length() > 0L }
    }

    override fun hasBundledAssetModel(fileNames: List<String>): Boolean =
        assetLookupCandidates(fileNames)
            .any { (assetPath, _) ->
                runCatching { context.assets.open(assetPath).use { } }.isSuccess
            }

    override fun extractBundledAssetModel(fileNames: List<String>): File? {
        for ((assetPath, modelFileName) in assetLookupCandidates(fileNames)) {
            val targetDirectory = modelsDirectory()
            val targetFile = File(targetDirectory, modelFileName)
            val tempFile = File(targetDirectory, "$modelFileName.asset.part")

            if (targetFile.exists() && targetFile.length() > 0L) {
                Timber.tag(TAG).d(
                    "Model extract skip target=%s sizeBytes=%d",
                    targetFile.absolutePath,
                    targetFile.length(),
                )
                return targetFile
            }

            val extracted = runCatching {
                tempFile.delete()

                Timber.tag(TAG).i(
                    "Model extract start asset=%s temp=%s target=%s",
                    assetPath,
                    tempFile.absolutePath,
                    targetFile.absolutePath,
                )

                val assetFileDescriptor = runCatching { context.assets.openFd(assetPath) }.getOrNull()
                val inputStream = assetFileDescriptor?.createInputStream()
                    ?: context.assets.open(assetPath)
                try {
                    inputStream.use { input ->
                        tempFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                } finally {
                    runCatching { assetFileDescriptor?.close() }
                }

                require(tempFile.exists()) { "Model extract temp file missing" }
                require(tempFile.length() > 0L) { "Model extract temp file empty" }

                if (targetFile.exists()) {
                    targetFile.delete()
                }

                if (!tempFile.renameTo(targetFile)) {
                    tempFile.copyTo(targetFile, overwrite = true)
                    tempFile.delete()
                }

                require(targetFile.exists()) { "Model extract target file missing" }
                require(targetFile.length() > 0L) { "Model extract target file empty" }

                Timber.tag(TAG).i(
                    "Model extract done target=%s sizeBytes=%d",
                    targetFile.absolutePath,
                    targetFile.length(),
                )

                targetFile
            }.getOrElse { throwable ->
                tempFile.delete()
                Timber.tag(TAG).w(throwable, "Model extract failed asset=%s", assetPath)
                null
            }

            if (extracted != null) {
                return extracted
            }
        }

        return null
    }

    override fun modelsDirectory(): File =
        File(context.filesDir, MODELS_DIRECTORY).apply { mkdirs() }

    private fun assetLookupCandidates(fileNames: List<String>): List<Pair<String, String>> =
        fileNames.flatMap { modelFileName ->
            listOf(
                modelFileName to modelFileName,
                "models/$modelFileName" to modelFileName,
            )
        }

    private companion object {
        const val TAG = "LocalModelStore"
        const val MODELS_DIRECTORY = "models"
    }
}
