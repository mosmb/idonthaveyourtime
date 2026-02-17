package io.morgan.idonthaveyourtime.core.domain.repository

import io.morgan.idonthaveyourtime.core.model.ProcessingConfig
import kotlinx.coroutines.flow.Flow

/**
 * Stores and exposes user-tunable processing configuration.
 *
 * The configuration impacts segmentation, model selection, and quality/speed trade-offs.
 */
interface ProcessingConfigRepository {
    fun observeConfig(): Flow<ProcessingConfig>
    suspend fun getConfig(): ProcessingConfig
    suspend fun setConfig(config: ProcessingConfig): Result<Unit>
}
