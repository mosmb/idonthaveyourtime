package io.morgan.idonthaveyourtime.feature.summarize.impl

import com.google.common.truth.Truth.assertThat
import io.morgan.idonthaveyourtime.core.domain.repository.AudioImportRepository
import io.morgan.idonthaveyourtime.core.domain.repository.ModelRepository
import io.morgan.idonthaveyourtime.core.domain.repository.ProcessingConfigRepository
import io.morgan.idonthaveyourtime.core.domain.repository.ProcessingQueueRepository
import io.morgan.idonthaveyourtime.core.domain.repository.SessionRepository
import io.morgan.idonthaveyourtime.core.model.ChunkSummary
import io.morgan.idonthaveyourtime.core.domain.usecase.CancelModelDownloadUseCase
import io.morgan.idonthaveyourtime.core.domain.usecase.CancelSessionUseCase
import io.morgan.idonthaveyourtime.core.domain.usecase.DownloadSuggestedModelUseCase
import io.morgan.idonthaveyourtime.core.domain.usecase.EnqueueSharedAudioUseCase
import io.morgan.idonthaveyourtime.core.domain.usecase.GetSuggestedModelsUseCase
import io.morgan.idonthaveyourtime.core.domain.usecase.ObserveModelAvailabilityUseCase
import io.morgan.idonthaveyourtime.core.domain.usecase.ObserveProcessingConfigUseCase
import io.morgan.idonthaveyourtime.core.domain.usecase.ObserveRecentSessionsUseCase
import io.morgan.idonthaveyourtime.core.domain.usecase.ObserveSessionUseCase
import io.morgan.idonthaveyourtime.core.domain.usecase.RequeueSessionUseCase
import io.morgan.idonthaveyourtime.core.domain.usecase.SetProcessingConfigUseCase
import io.morgan.idonthaveyourtime.core.model.ImportedAudio
import io.morgan.idonthaveyourtime.core.model.ModelAvailability
import io.morgan.idonthaveyourtime.core.model.ModelId
import io.morgan.idonthaveyourtime.core.model.ProcessingConfig
import io.morgan.idonthaveyourtime.core.model.ProcessingSession
import io.morgan.idonthaveyourtime.core.model.ProcessingStage
import io.morgan.idonthaveyourtime.core.model.SharedAudioInput
import io.morgan.idonthaveyourtime.core.model.Summary
import io.morgan.idonthaveyourtime.core.model.SuggestedModel
import io.morgan.idonthaveyourtime.core.model.Transcript
import io.morgan.idonthaveyourtime.core.model.TranscriptSegment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SummarizeViewModelTest {

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `onSharedAudiosReceived enqueues and exposes queued session`() = runTest {
        val repository = InMemorySessionRepository()
        val queue = FakeProcessingQueueRepository()
        val processingConfigRepository = FakeProcessingConfigRepository()
        val modelRepository = FakeModelRepository()

        val viewModel = SummarizeViewModel(
            enqueueSharedAudioUseCase = EnqueueSharedAudioUseCase(
                sessionRepository = repository,
                audioImportRepository = FakeAudioImportRepository(),
                processingQueueRepository = queue,
            ),
            observeSessionUseCase = ObserveSessionUseCase(repository),
            observeRecentSessionsUseCase = ObserveRecentSessionsUseCase(repository),
            cancelSessionUseCase = CancelSessionUseCase(
                processingQueueRepository = queue,
                sessionRepository = repository,
            ),
            requeueSessionUseCase = RequeueSessionUseCase(repository, queue),
            observeModelAvailabilityUseCase = ObserveModelAvailabilityUseCase(modelRepository),
            getSuggestedModelsUseCase = GetSuggestedModelsUseCase(),
            downloadSuggestedModelUseCase = DownloadSuggestedModelUseCase(modelRepository),
            cancelModelDownloadUseCase = CancelModelDownloadUseCase(modelRepository),
            observeProcessingConfigUseCase = ObserveProcessingConfigUseCase(processingConfigRepository),
            setProcessingConfigUseCase = SetProcessingConfigUseCase(processingConfigRepository),
        )

        val collectionJob = launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect { }
        }

        viewModel.onSharedAudiosReceived(
            listOf(
                SharedAudioInput(
                    uriToken = "content://audio/1",
                    mimeType = "audio/ogg",
                    displayName = "voice.ogg",
                    sizeBytes = 1024L,
                )
            )
        )

        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertThat(state.recentSessions).hasSize(1)

        val createdSession = state.recentSessions.first()
        viewModel.onSessionSelected(createdSession.id)
        advanceUntilIdle()

        val selectedState = viewModel.uiState.value
        assertThat(selectedState.activeSession).isNotNull()
        assertThat(selectedState.activeSession?.stage).isEqualTo(ProcessingStage.Queued)
        assertThat(queue.enqueuedSessionIds).hasSize(1)

        collectionJob.cancel()
    }

    @Test
    fun `onDownloadSuggestedModel selects LLM file and enqueues download`() = runTest {
        val repository = InMemorySessionRepository()
        val queue = FakeProcessingQueueRepository()
        val processingConfigRepository = FakeProcessingConfigRepository()
        val modelRepository = FakeModelRepository()

        val viewModel = SummarizeViewModel(
            enqueueSharedAudioUseCase = EnqueueSharedAudioUseCase(
                sessionRepository = repository,
                audioImportRepository = FakeAudioImportRepository(),
                processingQueueRepository = queue,
            ),
            observeSessionUseCase = ObserveSessionUseCase(repository),
            observeRecentSessionsUseCase = ObserveRecentSessionsUseCase(repository),
            cancelSessionUseCase = CancelSessionUseCase(
                processingQueueRepository = queue,
                sessionRepository = repository,
            ),
            requeueSessionUseCase = RequeueSessionUseCase(repository, queue),
            observeModelAvailabilityUseCase = ObserveModelAvailabilityUseCase(modelRepository),
            getSuggestedModelsUseCase = GetSuggestedModelsUseCase(),
            downloadSuggestedModelUseCase = DownloadSuggestedModelUseCase(modelRepository),
            cancelModelDownloadUseCase = CancelModelDownloadUseCase(modelRepository),
            observeProcessingConfigUseCase = ObserveProcessingConfigUseCase(processingConfigRepository),
            setProcessingConfigUseCase = SetProcessingConfigUseCase(processingConfigRepository),
        )

        val collectionJob = launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect { }
        }

        advanceUntilIdle()

        val llmModel = viewModel.uiState.value.llmSuggestedModels.first { it.modelId == ModelId.Llm }
        viewModel.onDownloadSuggestedModel(llmModel)
        advanceUntilIdle()

        assertThat(modelRepository.enqueuedModels).contains(llmModel)
        assertThat(viewModel.uiState.value.processingConfig.llmModelFileName).isEqualTo(llmModel.fileName)

        collectionJob.cancel()
    }

    @Test
    fun `onCancelRequested cancels active session`() = runTest {
        val repository = InMemorySessionRepository()
        val queue = FakeProcessingQueueRepository()
        val viewModel = createViewModel(repository, queue)
        val collectionJob = launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect { }
        }

        viewModel.onSharedAudiosReceived(listOf(testSharedAudio()))
        advanceUntilIdle()

        val sessionId = viewModel.uiState.value.recentSessions.first().id
        viewModel.onSessionSelected(sessionId)
        viewModel.onCancelRequested()
        advanceUntilIdle()

        assertThat(queue.cancelledSessionIds).contains(sessionId)
        collectionJob.cancel()
    }

    @Test
    fun `onRetryRequested requeues active session`() = runTest {
        val repository = InMemorySessionRepository()
        val queue = FakeProcessingQueueRepository()
        val viewModel = createViewModel(repository, queue)
        val collectionJob = launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect { }
        }

        viewModel.onSharedAudiosReceived(listOf(testSharedAudio()))
        advanceUntilIdle()

        val sessionId = viewModel.uiState.value.recentSessions.first().id
        viewModel.onSessionSelected(sessionId)
        viewModel.onRetryRequested()
        advanceUntilIdle()

        assertThat(queue.enqueuedSessionIds).hasSize(2)
        assertThat(queue.enqueuedSessionIds.last()).isEqualTo(sessionId)
        collectionJob.cancel()
    }

    private fun createViewModel(
        repository: InMemorySessionRepository,
        queue: FakeProcessingQueueRepository,
    ): SummarizeViewModel {
        val processingConfigRepository = FakeProcessingConfigRepository()
        val modelRepository = FakeModelRepository()
        return SummarizeViewModel(
            enqueueSharedAudioUseCase = EnqueueSharedAudioUseCase(
                sessionRepository = repository,
                audioImportRepository = FakeAudioImportRepository(),
                processingQueueRepository = queue,
            ),
            observeSessionUseCase = ObserveSessionUseCase(repository),
            observeRecentSessionsUseCase = ObserveRecentSessionsUseCase(repository),
            cancelSessionUseCase = CancelSessionUseCase(
                processingQueueRepository = queue,
                sessionRepository = repository,
            ),
            requeueSessionUseCase = RequeueSessionUseCase(repository, queue),
            observeModelAvailabilityUseCase = ObserveModelAvailabilityUseCase(modelRepository),
            getSuggestedModelsUseCase = GetSuggestedModelsUseCase(),
            downloadSuggestedModelUseCase = DownloadSuggestedModelUseCase(modelRepository),
            cancelModelDownloadUseCase = CancelModelDownloadUseCase(modelRepository),
            observeProcessingConfigUseCase = ObserveProcessingConfigUseCase(processingConfigRepository),
            setProcessingConfigUseCase = SetProcessingConfigUseCase(processingConfigRepository),
        )
    }

    private fun testSharedAudio() = SharedAudioInput(
        uriToken = "content://audio/1",
        mimeType = "audio/ogg",
        displayName = "voice.ogg",
        sizeBytes = 1024L,
    )

    private class FakeAudioImportRepository : AudioImportRepository {
        override suspend fun importFromUri(
            sharedAudio: SharedAudioInput,
            sessionId: String,
        ): Result<ImportedAudio> = Result.success(
            ImportedAudio(
                sessionId = sessionId,
                cachedFilePath = "/tmp/$sessionId/import.ogg",
                sourceName = sharedAudio.displayName,
                mimeType = sharedAudio.mimeType,
                sizeBytes = sharedAudio.sizeBytes,
            )
        )
    }

    private class FakeProcessingQueueRepository : ProcessingQueueRepository {
        val enqueuedSessionIds = mutableListOf<String>()
        val cancelledSessionIds = mutableListOf<String>()

        override suspend fun enqueue(sessionId: String): Result<Unit> = runCatching {
            enqueuedSessionIds += sessionId
        }

        override suspend fun cancel(sessionId: String): Result<Unit> = runCatching {
            cancelledSessionIds += sessionId
        }
    }

    private class FakeModelRepository : ModelRepository {
        val enqueuedModels = mutableListOf<SuggestedModel>()
        val cancelledModelIds = mutableListOf<ModelId>()
        private val availability = MutableStateFlow<ModelAvailability>(ModelAvailability.Ready)

        override fun observeAvailability(modelId: ModelId): Flow<ModelAvailability> =
            availability

        override suspend fun getModelPath(modelId: ModelId): Result<String> =
            Result.success("/tmp/model")

        override suspend fun enqueueDownload(model: SuggestedModel): Result<Unit> = runCatching {
            enqueuedModels += model
        }

        override suspend fun cancelDownload(modelId: ModelId): Result<Unit> = runCatching {
            cancelledModelIds += modelId
        }
    }

    private class FakeProcessingConfigRepository : ProcessingConfigRepository {
        private val config = MutableStateFlow(ProcessingConfig())

        override fun observeConfig(): Flow<ProcessingConfig> = config

        override suspend fun getConfig(): ProcessingConfig = config.value

        override suspend fun setConfig(config: ProcessingConfig): Result<Unit> = runCatching {
            this.config.value = config
        }
    }

    private class InMemorySessionRepository : SessionRepository {
        private val sessions = MutableStateFlow<Map<String, ProcessingSession>>(emptyMap())
        private val inputPaths = mutableMapOf<String, String>()
        private val wavPaths = mutableMapOf<String, String>()

        override fun observeSession(sessionId: String): Flow<ProcessingSession?> =
            sessions.map { state -> state[sessionId] }

        override fun observeRecentSessions(limit: Int): Flow<List<ProcessingSession>> =
            sessions.map { state -> state.values.sortedByDescending { it.createdAtEpochMs }.take(limit) }

        override fun observeChunkSummaries(sessionId: String): Flow<List<ChunkSummary>> = emptyFlow()

        override suspend fun getSession(sessionId: String): ProcessingSession? = sessions.value[sessionId]

        override suspend fun createSession(session: ProcessingSession, inputFilePath: String): Result<Unit> = runCatching {
            sessions.value = sessions.value + (session.id to session)
            inputPaths[session.id] = inputFilePath
        }

        override suspend fun updateStage(sessionId: String, stage: ProcessingStage, progress: Float): Result<Unit> = runCatching {
            val existing = sessions.value[sessionId] ?: return@runCatching
            sessions.value = sessions.value + (sessionId to existing.copy(stage = stage, progress = progress))
        }

        override suspend fun setWavFilePath(sessionId: String, wavFilePath: String): Result<Unit> = runCatching {
            wavPaths[sessionId] = wavFilePath
        }

        override suspend fun setTranscript(sessionId: String, transcript: Transcript): Result<Unit> = runCatching {
            val existing = sessions.value[sessionId] ?: return@runCatching
            sessions.value = sessions.value + (
                sessionId to existing.copy(
                    transcript = transcript.text,
                    languageCode = transcript.languageCode,
                )
            )
        }

        override suspend fun upsertTranscriptSegment(sessionId: String, index: Int, segment: TranscriptSegment): Result<Unit> =
            Result.success(Unit)

        override suspend fun upsertChunkSummary(sessionId: String, chunk: ChunkSummary): Result<Unit> =
            Result.success(Unit)

        override suspend fun setSummaryPartial(sessionId: String, summaryText: String): Result<Unit> =
            Result.success(Unit)

        override suspend fun getInputFilePath(sessionId: String): String? = inputPaths[sessionId]

        override suspend fun getWavFilePath(sessionId: String): String? = wavPaths[sessionId]

        override suspend fun setSuccess(sessionId: String, transcript: Transcript, summary: Summary): Result<Unit> = runCatching {
            val existing = sessions.value[sessionId] ?: return@runCatching
            sessions.value = sessions.value + (
                sessionId to existing.copy(
                    stage = ProcessingStage.Success,
                    transcript = transcript.text,
                    summary = summary.text,
                    languageCode = transcript.languageCode,
                    progress = 1f,
                )
            )
        }

        override suspend fun setError(sessionId: String, errorCode: String, errorMessage: String): Result<Unit> = runCatching {
            val existing = sessions.value[sessionId] ?: return@runCatching
            sessions.value = sessions.value + (
                sessionId to existing.copy(
                    stage = ProcessingStage.Error,
                    errorCode = errorCode,
                    errorMessage = errorMessage,
                )
            )
        }

        override suspend fun markCancelled(sessionId: String): Result<Unit> = runCatching {
            val existing = sessions.value[sessionId] ?: return@runCatching
            sessions.value = sessions.value + (sessionId to existing.copy(stage = ProcessingStage.Cancelled))
        }
    }
}
