package io.morgan.idonthaveyourtime.core.data.di

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.morgan.idonthaveyourtime.core.data.datasource.audio.AudioImporterLocalDataSource
import io.morgan.idonthaveyourtime.core.data.datasource.audio.impl.ContentResolverAudioImporterLocalDataSource
import io.morgan.idonthaveyourtime.core.data.datasource.model.ModelDownloaderRemoteDataSource
import io.morgan.idonthaveyourtime.core.data.datasource.model.ModelLocatorLocalDataSource
import io.morgan.idonthaveyourtime.core.data.datasource.model.ModelStoreLocalDataSource
import io.morgan.idonthaveyourtime.core.data.datasource.model.impl.FilesAndAssetsModelStoreLocalDataSource
import io.morgan.idonthaveyourtime.core.data.datasource.model.impl.ModelManagerModelLocatorLocalDataSource
import io.morgan.idonthaveyourtime.core.data.datasource.model.impl.WorkManagerModelDownloaderRemoteDataSource
import io.morgan.idonthaveyourtime.core.data.datasource.processing.ProcessingQueueLocalDataSource
import io.morgan.idonthaveyourtime.core.data.datasource.processing.TempFileCleanerLocalDataSource
import io.morgan.idonthaveyourtime.core.data.datasource.summarization.SummarizerEngineLocalDataSource
import io.morgan.idonthaveyourtime.core.data.datasource.processing.impl.CacheTempFileCleanerLocalDataSource
import io.morgan.idonthaveyourtime.core.data.datasource.processing.impl.WorkManagerProcessingQueueLocalDataSource
import io.morgan.idonthaveyourtime.core.data.datasource.settings.ProcessingConfigLocalDataSource
import io.morgan.idonthaveyourtime.core.data.datasource.settings.impl.DataStoreProcessingConfigLocalDataSource
import io.morgan.idonthaveyourtime.core.data.datasource.summarization.impl.RoutingSummarizerEngineLocalDataSource
import io.morgan.idonthaveyourtime.core.data.repository.DefaultAudioImportRepository
import io.morgan.idonthaveyourtime.core.data.repository.DefaultAudioProcessingRepository
import io.morgan.idonthaveyourtime.core.data.repository.DefaultModelRepository
import io.morgan.idonthaveyourtime.core.data.repository.DefaultProcessingConfigRepository
import io.morgan.idonthaveyourtime.core.data.repository.DefaultProcessingQueueRepository
import io.morgan.idonthaveyourtime.core.data.repository.DefaultSummarizationRepository
import io.morgan.idonthaveyourtime.core.data.repository.DefaultTranscriptionRepository
import io.morgan.idonthaveyourtime.core.data.repository.RoomSessionRepository
import io.morgan.idonthaveyourtime.core.database.AppDatabase
import io.morgan.idonthaveyourtime.core.database.dao.ChunkSummaryDao
import io.morgan.idonthaveyourtime.core.database.dao.SessionDao
import io.morgan.idonthaveyourtime.core.database.dao.TranscriptSegmentDao
import io.morgan.idonthaveyourtime.core.domain.repository.AudioImportRepository
import io.morgan.idonthaveyourtime.core.domain.repository.AudioProcessingRepository
import io.morgan.idonthaveyourtime.core.domain.repository.ModelRepository
import io.morgan.idonthaveyourtime.core.domain.repository.ProcessingConfigRepository
import io.morgan.idonthaveyourtime.core.domain.repository.ProcessingQueueRepository
import io.morgan.idonthaveyourtime.core.domain.repository.SessionRepository
import io.morgan.idonthaveyourtime.core.domain.repository.SummarizationRepository
import io.morgan.idonthaveyourtime.core.domain.repository.TranscriptionRepository
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal abstract class DataSourceBindingsModule {

    @Binds
    @Singleton
    abstract fun bindAudioImporter(impl: ContentResolverAudioImporterLocalDataSource): AudioImporterLocalDataSource

    @Binds
    @Singleton
    abstract fun bindProcessingQueue(impl: WorkManagerProcessingQueueLocalDataSource): ProcessingQueueLocalDataSource

    @Binds
    @Singleton
    abstract fun bindTempFileCleaner(impl: CacheTempFileCleanerLocalDataSource): TempFileCleanerLocalDataSource

    @Binds
    @Singleton
    abstract fun bindLocalModelStore(impl: FilesAndAssetsModelStoreLocalDataSource): ModelStoreLocalDataSource

    @Binds
    @Singleton
    abstract fun bindModelLocator(impl: ModelManagerModelLocatorLocalDataSource): ModelLocatorLocalDataSource

    @Binds
    @Singleton
    abstract fun bindModelDownloader(impl: WorkManagerModelDownloaderRemoteDataSource): ModelDownloaderRemoteDataSource

    @Binds
    @Singleton
    abstract fun bindProcessingConfigStore(impl: DataStoreProcessingConfigLocalDataSource): ProcessingConfigLocalDataSource

    @Binds
    @Singleton
    abstract fun bindSummarizerEngine(impl: RoutingSummarizerEngineLocalDataSource): SummarizerEngineLocalDataSource
}

@Module
@InstallIn(SingletonComponent::class)
internal abstract class RepositoryBindingsModule {

    @Binds
    @Singleton
    abstract fun bindSessionRepository(impl: RoomSessionRepository): SessionRepository

    @Binds
    @Singleton
    abstract fun bindAudioImportRepository(impl: DefaultAudioImportRepository): AudioImportRepository

    @Binds
    @Singleton
    abstract fun bindProcessingQueueRepository(impl: DefaultProcessingQueueRepository): ProcessingQueueRepository

    @Binds
    @Singleton
    abstract fun bindProcessingConfigRepository(impl: DefaultProcessingConfigRepository): ProcessingConfigRepository

    @Binds
    @Singleton
    abstract fun bindAudioProcessingRepository(impl: DefaultAudioProcessingRepository): AudioProcessingRepository

    @Binds
    @Singleton
    abstract fun bindTranscriptionRepository(impl: DefaultTranscriptionRepository): TranscriptionRepository

    @Binds
    @Singleton
    abstract fun bindSummarizationRepository(impl: DefaultSummarizationRepository): SummarizationRepository

    @Binds
    @Singleton
    abstract fun bindModelRepository(impl: DefaultModelRepository): ModelRepository
}

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "processing.db")
            .addMigrations(
                object : Migration(1, 2) {
                    override fun migrate(db: SupportSQLiteDatabase) {
                        db.execSQL(
                            """
                            CREATE TABLE IF NOT EXISTS transcript_segments (
                                session_id TEXT NOT NULL,
                                segment_index INTEGER NOT NULL,
                                start_ms INTEGER NOT NULL,
                                end_ms INTEGER NOT NULL,
                                text TEXT NOT NULL,
                                PRIMARY KEY(session_id, segment_index)
                            )
                            """.trimIndent()
                        )
                        db.execSQL(
                            """
                            CREATE TABLE IF NOT EXISTS chunk_summaries (
                                session_id TEXT NOT NULL,
                                chunk_index INTEGER NOT NULL,
                                start_ms INTEGER NOT NULL,
                                end_ms INTEGER NOT NULL,
                                bullets_text TEXT NOT NULL,
                                PRIMARY KEY(session_id, chunk_index)
                            )
                            """.trimIndent()
                        )
                    }
                },
                object : Migration(2, 3) {
                    override fun migrate(db: SupportSQLiteDatabase) {
                        db.execSQL("ALTER TABLE processing_sessions ADD COLUMN transcription_runtime TEXT")
                        db.execSQL("ALTER TABLE processing_sessions ADD COLUMN transcription_backend_name TEXT")
                        db.execSQL("ALTER TABLE processing_sessions ADD COLUMN transcription_model_file_name TEXT")
                        db.execSQL("ALTER TABLE processing_sessions ADD COLUMN transcription_warm_start INTEGER")
                        db.execSQL("ALTER TABLE processing_sessions ADD COLUMN transcription_model_load_ms INTEGER")
                        db.execSQL("ALTER TABLE processing_sessions ADD COLUMN transcription_first_text_ms INTEGER")
                        db.execSQL("ALTER TABLE processing_sessions ADD COLUMN transcription_total_ms INTEGER")
                        db.execSQL("ALTER TABLE processing_sessions ADD COLUMN transcription_audio_duration_ms INTEGER")
                        db.execSQL("ALTER TABLE processing_sessions ADD COLUMN transcription_audio_seconds_per_wall_second REAL")
                        db.execSQL("ALTER TABLE processing_sessions ADD COLUMN transcription_fallback_reason TEXT")
                        db.execSQL("ALTER TABLE processing_sessions ADD COLUMN transcription_failure_reason TEXT")
                        db.execSQL("ALTER TABLE processing_sessions ADD COLUMN transcription_device_label TEXT")
                    }
                }
            )
            .build()

    @Provides
    fun provideSessionDao(database: AppDatabase): SessionDao = database.sessionDao()

    @Provides
    fun provideTranscriptSegmentDao(database: AppDatabase): TranscriptSegmentDao = database.transcriptSegmentDao()

    @Provides
    fun provideChunkSummaryDao(database: AppDatabase): ChunkSummaryDao = database.chunkSummaryDao()
}
