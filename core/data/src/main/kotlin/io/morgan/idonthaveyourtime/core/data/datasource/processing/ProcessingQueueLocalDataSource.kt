package io.morgan.idonthaveyourtime.core.data.datasource.processing

/**
 * Enqueues and cancels processing work for a session.
 *
 * This is a **local** data source: implementations must not perform network I/O.
 */
interface ProcessingQueueLocalDataSource {
    suspend fun enqueue(sessionId: String): Result<Unit>
    suspend fun cancel(sessionId: String): Result<Unit>
}

