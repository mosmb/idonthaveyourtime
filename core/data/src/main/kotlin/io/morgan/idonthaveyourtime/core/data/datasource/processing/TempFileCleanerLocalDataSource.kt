package io.morgan.idonthaveyourtime.core.data.datasource.processing

/**
 * Cleans up temporary session files created during processing.
 *
 * This is a **local** data source: implementations must not perform network I/O.
 */
interface TempFileCleanerLocalDataSource {
    suspend fun cleanupSessionFiles(sessionId: String): Result<Unit>
}

