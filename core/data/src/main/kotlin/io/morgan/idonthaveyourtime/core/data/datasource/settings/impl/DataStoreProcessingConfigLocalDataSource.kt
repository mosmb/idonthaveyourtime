package io.morgan.idonthaveyourtime.core.data.datasource.settings.impl

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import io.morgan.idonthaveyourtime.core.data.datasource.settings.ProcessingConfigLocalDataSource
import io.morgan.idonthaveyourtime.core.data.di.IoDispatcher
import io.morgan.idonthaveyourtime.core.model.ProcessingConfig
import io.morgan.idonthaveyourtime.core.model.WhisperModelSize
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

private val Context.processingConfigDataStore by preferencesDataStore(name = "processing_config")

@Singleton
internal class DataStoreProcessingConfigLocalDataSource @Inject constructor(
    @ApplicationContext private val context: Context,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : ProcessingConfigLocalDataSource {

    override fun observeConfig(): Flow<ProcessingConfig> =
        context.processingConfigDataStore.data.map { preferences ->
            preferences.toProcessingConfig()
        }

    override suspend fun getConfig(): ProcessingConfig =
        observeConfig().first()

    override suspend fun setConfig(config: ProcessingConfig): Result<Unit> = withContext(ioDispatcher) {
        runCatching {
            context.processingConfigDataStore.edit { preferences ->
                preferences[KEY_WHISPER_MODEL_SIZE] = config.whisperModelSize.name
                preferences[KEY_LLM_MODEL_FILE_NAME] = config.llmModelFileName
                preferences[KEY_SEGMENT_TARGET_MS] = config.segmentationTargetSpeechMs
                preferences[KEY_SEGMENT_OVERLAP_MS] = config.segmentationOverlapMs
                preferences[KEY_MAP_EVERY_SEGMENTS] = config.mapEverySegments
            }
            Unit
        }
    }

    private fun Preferences.toProcessingConfig(): ProcessingConfig {
        val whisperModelSize = get(KEY_WHISPER_MODEL_SIZE)
            ?.let { raw -> runCatching { WhisperModelSize.valueOf(raw) }.getOrNull() }
            ?: ProcessingConfig().whisperModelSize

        val llmModelFileName = get(KEY_LLM_MODEL_FILE_NAME)
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: ProcessingConfig().llmModelFileName

        val targetMs = get(KEY_SEGMENT_TARGET_MS) ?: ProcessingConfig().segmentationTargetSpeechMs
        val overlapMs = get(KEY_SEGMENT_OVERLAP_MS) ?: ProcessingConfig().segmentationOverlapMs
        val mapEvery = get(KEY_MAP_EVERY_SEGMENTS) ?: ProcessingConfig().mapEverySegments

        return ProcessingConfig(
            whisperModelSize = whisperModelSize,
            llmModelFileName = llmModelFileName,
            segmentationTargetSpeechMs = targetMs,
            segmentationOverlapMs = overlapMs,
            mapEverySegments = mapEvery,
        )
    }

    private companion object {
        val KEY_WHISPER_MODEL_SIZE = stringPreferencesKey("whisper_model_size")
        val KEY_LLM_MODEL_FILE_NAME = stringPreferencesKey("llm_model_file_name")
        val KEY_SEGMENT_TARGET_MS = longPreferencesKey("segment_target_ms")
        val KEY_SEGMENT_OVERLAP_MS = longPreferencesKey("segment_overlap_ms")
        val KEY_MAP_EVERY_SEGMENTS = intPreferencesKey("map_every_segments")
    }
}
