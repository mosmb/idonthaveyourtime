package io.morgan.idonthaveyourtime.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import io.morgan.idonthaveyourtime.core.database.dao.ChunkSummaryDao
import io.morgan.idonthaveyourtime.core.database.dao.SessionDao
import io.morgan.idonthaveyourtime.core.database.dao.TranscriptSegmentDao
import io.morgan.idonthaveyourtime.core.database.model.ChunkSummaryEntity
import io.morgan.idonthaveyourtime.core.database.model.SessionEntity
import io.morgan.idonthaveyourtime.core.database.model.TranscriptSegmentEntity

@Database(
    entities = [
        SessionEntity::class,
        TranscriptSegmentEntity::class,
        ChunkSummaryEntity::class,
    ],
    version = 2,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun sessionDao(): SessionDao
    abstract fun transcriptSegmentDao(): TranscriptSegmentDao
    abstract fun chunkSummaryDao(): ChunkSummaryDao
}
