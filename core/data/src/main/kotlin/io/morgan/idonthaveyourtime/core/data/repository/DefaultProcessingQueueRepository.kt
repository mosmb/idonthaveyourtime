package io.morgan.idonthaveyourtime.core.data.repository

import io.morgan.idonthaveyourtime.core.data.datasource.processing.ProcessingQueueLocalDataSource
import io.morgan.idonthaveyourtime.core.domain.repository.ProcessingQueueRepository
import javax.inject.Inject

internal class DefaultProcessingQueueRepository @Inject constructor(
    private val processingQueue: ProcessingQueueLocalDataSource,
) : ProcessingQueueRepository {
    override suspend fun enqueue(sessionId: String): Result<Unit> =
        processingQueue.enqueue(sessionId)

    override suspend fun cancel(sessionId: String): Result<Unit> =
        processingQueue.cancel(sessionId)
}
