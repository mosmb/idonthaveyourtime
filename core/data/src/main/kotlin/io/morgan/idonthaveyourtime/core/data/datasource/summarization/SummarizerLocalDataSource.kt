package io.morgan.idonthaveyourtime.core.data.datasource.summarization

import io.morgan.idonthaveyourtime.core.model.Summary

/**
 * Produces summary bullets from transcript text.
 *
 * This is a **local** data source: implementations must not perform network I/O.
 */
interface SummarizerLocalDataSource {
    suspend fun mapChunk(
        transcriptChunk: String,
        languageCode: String?,
    ): Result<String>

    suspend fun reduce(
        chunkBulletSummaries: List<String>,
        languageCode: String?,
    ): Result<Summary>
}

