package io.morgan.idonthaveyourtime.core.data.datasource.summarization

import io.morgan.idonthaveyourtime.core.model.SummarizerEngineCapability
import io.morgan.idonthaveyourtime.core.model.SummarizerEngineProbeResult
import io.morgan.idonthaveyourtime.core.model.Summary

/**
 * Summarization engine contract for all on-device runtimes.
 *
 * Implementations must stay fully local and must not perform network I/O.
 */
interface SummarizerEngineLocalDataSource {
    fun capability(): SummarizerEngineCapability

    suspend fun probe(): Result<SummarizerEngineProbeResult>

    suspend fun prewarm(): Result<Unit>

    suspend fun mapChunk(
        transcriptChunk: String,
        languageCode: String?,
        onPartialResult: (String) -> Unit = {},
    ): Result<String>

    suspend fun reduce(
        chunkBulletSummaries: List<String>,
        languageCode: String?,
        onPartialResult: (String) -> Unit = {},
    ): Result<Summary>
}
