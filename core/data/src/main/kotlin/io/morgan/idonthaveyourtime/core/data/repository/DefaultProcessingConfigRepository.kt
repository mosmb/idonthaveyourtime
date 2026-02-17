package io.morgan.idonthaveyourtime.core.data.repository

import io.morgan.idonthaveyourtime.core.data.datasource.settings.ProcessingConfigLocalDataSource
import io.morgan.idonthaveyourtime.core.domain.repository.ProcessingConfigRepository
import io.morgan.idonthaveyourtime.core.model.ProcessingConfig
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

internal class DefaultProcessingConfigRepository @Inject constructor(
    private val processingConfigStore: ProcessingConfigLocalDataSource,
) : ProcessingConfigRepository {
    override fun observeConfig(): Flow<ProcessingConfig> =
        processingConfigStore.observeConfig()

    override suspend fun getConfig(): ProcessingConfig =
        processingConfigStore.getConfig()

    override suspend fun setConfig(config: ProcessingConfig): Result<Unit> =
        processingConfigStore.setConfig(config)
}
