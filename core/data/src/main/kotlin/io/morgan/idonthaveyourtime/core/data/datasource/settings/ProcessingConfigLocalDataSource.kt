package io.morgan.idonthaveyourtime.core.data.datasource.settings

import io.morgan.idonthaveyourtime.core.model.ProcessingConfig
import kotlinx.coroutines.flow.Flow

/**
 * Stores and exposes the processing configuration.
 *
 * This is a **local** data source: implementations must not perform network I/O.
 */
interface ProcessingConfigLocalDataSource {
    fun observeConfig(): Flow<ProcessingConfig>
    suspend fun getConfig(): ProcessingConfig
    suspend fun setConfig(config: ProcessingConfig): Result<Unit>
}

