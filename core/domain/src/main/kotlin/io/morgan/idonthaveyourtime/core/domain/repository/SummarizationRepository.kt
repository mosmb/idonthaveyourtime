package io.morgan.idonthaveyourtime.core.domain.repository

import io.morgan.idonthaveyourtime.core.model.Summary

/**
 * Generates summary bullets from transcript text.
 *
 * The summarization flow is a map/reduce over transcript chunks, to support incremental updates and
 * large inputs.
 */
interface SummarizationRepository {
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
