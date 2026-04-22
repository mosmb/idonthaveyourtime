package io.morgan.idonthaveyourtime.core.data.datasource.model.impl

import io.morgan.idonthaveyourtime.core.data.datasource.model.ModelLocatorLocalDataSource
import io.morgan.idonthaveyourtime.core.data.datasource.model.ModelStoreLocalDataSource
import io.morgan.idonthaveyourtime.core.data.datasource.settings.ProcessingConfigLocalDataSource
import io.morgan.idonthaveyourtime.core.data.datasource.settings.impl.normalizeTranscriptionModelFileName
import io.morgan.idonthaveyourtime.core.model.ModelAvailability
import io.morgan.idonthaveyourtime.core.model.ModelId
import io.morgan.idonthaveyourtime.core.model.ProcessingConfig
import java.io.File
import javax.inject.Inject
import kotlin.system.measureTimeMillis
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import timber.log.Timber

internal class ModelManagerModelLocatorLocalDataSource @Inject constructor(
    private val processingConfigDataSource: ProcessingConfigLocalDataSource,
    private val modelStoreDataSource: ModelStoreLocalDataSource,
) : ModelLocatorLocalDataSource {

    override fun observeAvailability(modelId: ModelId): Flow<ModelAvailability> =
        combine(
            processingConfigDataSource.observeConfig(),
            modelStoreDataSource.observeModelsDirectoryChanges(),
        ) { config, _ ->
            val prioritizedFileNames = prioritizeModelFileNames(modelId, config)
            resolveAvailability(prioritizedFileNames)
        }.distinctUntilChanged()

    override suspend fun getModelPath(modelId: ModelId): Result<String> {
        val processingConfig = processingConfigDataSource.getConfig()
        val prioritizedFileNames = prioritizeModelFileNames(modelId, processingConfig)

        return runCatching {
            Timber.tag(TAG).i("Model resolve start modelId=%s", modelId)

            var source: String? = null
            var modelFile: File? = null
            val resolveMs = measureTimeMillis {
                val fromUserStorage = modelStoreDataSource.findModelFileInUserStorage(prioritizedFileNames)
                if (fromUserStorage != null) {
                    source = "files"
                    modelFile = fromUserStorage
                    return@measureTimeMillis
                }

                val fromAssets = modelStoreDataSource.extractBundledAssetModel(prioritizedFileNames)
                if (fromAssets != null) {
                    source = "assets"
                    modelFile = fromAssets
                }
            }

            val resolved = modelFile ?: throw IllegalStateException(
                "Model file missing for $modelId. Expected one of ${prioritizedFileNames.joinToString()} in ${modelStoreDataSource.modelsDirectory().absolutePath}",
            )

            require(resolved.exists()) { "Model file missing for $modelId at ${resolved.absolutePath}" }
            require(resolved.length() > 0L) { "Model file is empty for $modelId at ${resolved.absolutePath}" }

            Timber.tag(TAG).i(
                "Model resolve done modelId=%s source=%s path=%s sizeBytes=%d resolveMs=%d",
                modelId,
                source ?: "unknown",
                resolved.absolutePath,
                resolved.length(),
                resolveMs,
            )

            resolved.absolutePath
        }.onFailure { throwable ->
            Timber.tag(TAG).e(throwable, "Model resolve failed modelId=%s", modelId)
        }
    }

    private fun resolveAvailability(prioritizedFileNames: List<String>): ModelAvailability {
        if (modelStoreDataSource.findModelFileInUserStorage(prioritizedFileNames) != null) {
            return ModelAvailability.Ready
        }
        if (modelStoreDataSource.hasBundledAssetModel(prioritizedFileNames)) {
            return ModelAvailability.Ready
        }
        return ModelAvailability.Missing
    }

    private fun prioritizeModelFileNames(
        modelId: ModelId,
        config: ProcessingConfig,
    ): List<String> = when (modelId) {
        ModelId.Transcription -> listOf(
            normalizeTranscriptionModelFileName(config.transcriptionModelFileName),
        )

        ModelId.Llm -> listOf(
            config.llmModelFileName.trim().takeIf { it.isNotEmpty() }
                ?: ProcessingConfig().llmModelFileName,
        )
    }

    private companion object {
        const val TAG = "ModelLocator"
    }
}
