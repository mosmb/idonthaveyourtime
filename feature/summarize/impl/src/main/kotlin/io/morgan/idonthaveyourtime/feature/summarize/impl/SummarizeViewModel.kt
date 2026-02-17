package io.morgan.idonthaveyourtime.feature.summarize.impl

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.morgan.idonthaveyourtime.core.domain.usecase.CancelSessionUseCase
import io.morgan.idonthaveyourtime.core.domain.usecase.CancelModelDownloadUseCase
import io.morgan.idonthaveyourtime.core.domain.usecase.DownloadSuggestedModelUseCase
import io.morgan.idonthaveyourtime.core.domain.usecase.EnqueueSharedAudioUseCase
import io.morgan.idonthaveyourtime.core.domain.usecase.GetSuggestedModelsUseCase
import io.morgan.idonthaveyourtime.core.domain.usecase.ObserveModelAvailabilityUseCase
import io.morgan.idonthaveyourtime.core.domain.usecase.ObserveProcessingConfigUseCase
import io.morgan.idonthaveyourtime.core.domain.usecase.ObserveRecentSessionsUseCase
import io.morgan.idonthaveyourtime.core.domain.usecase.ObserveSessionUseCase
import io.morgan.idonthaveyourtime.core.domain.usecase.RequeueSessionUseCase
import io.morgan.idonthaveyourtime.core.domain.usecase.SetProcessingConfigUseCase
import io.morgan.idonthaveyourtime.core.model.ModelId
import io.morgan.idonthaveyourtime.core.model.ProcessingConfig
import io.morgan.idonthaveyourtime.core.model.SharedAudioInput
import io.morgan.idonthaveyourtime.core.model.SuggestedModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
@OptIn(ExperimentalCoroutinesApi::class)
class SummarizeViewModel @Inject constructor(
    private val enqueueSharedAudioUseCase: EnqueueSharedAudioUseCase,
    private val observeSessionUseCase: ObserveSessionUseCase,
    observeRecentSessionsUseCase: ObserveRecentSessionsUseCase,
    private val cancelSessionUseCase: CancelSessionUseCase,
    private val requeueSessionUseCase: RequeueSessionUseCase,
    observeModelAvailabilityUseCase: ObserveModelAvailabilityUseCase,
    private val getSuggestedModelsUseCase: GetSuggestedModelsUseCase,
    private val downloadSuggestedModelUseCase: DownloadSuggestedModelUseCase,
    private val cancelModelDownloadUseCase: CancelModelDownloadUseCase,
    observeProcessingConfigUseCase: ObserveProcessingConfigUseCase,
    private val setProcessingConfigUseCase: SetProcessingConfigUseCase,
) : ViewModel() {

    private val activeSessionId = MutableStateFlow<String?>(null)
    private val transientMessage = MutableStateFlow<String?>(null)

    private val whisperSuggestedModels: List<SuggestedModel> = getSuggestedModelsUseCase(ModelId.Whisper)
    private val llmSuggestedModels: List<SuggestedModel> = getSuggestedModelsUseCase(ModelId.Llm)

    private val activeSessionFlow = activeSessionId.flatMapLatest { sessionId ->
        if (sessionId == null) {
            flowOf(null)
        } else {
            observeSessionUseCase(sessionId)
        }
    }

    private val recentSessionsFlow = observeRecentSessionsUseCase()
    private val whisperModelFlow = observeModelAvailabilityUseCase(ModelId.Whisper)
    private val llmModelFlow = observeModelAvailabilityUseCase(ModelId.Llm)
    private val processingConfigFlow = observeProcessingConfigUseCase()

    private val baseUiState = combine(
        activeSessionFlow,
        recentSessionsFlow,
        whisperModelFlow,
        llmModelFlow,
        processingConfigFlow,
    ) { activeSession, recentSessions, whisperAvailability, llmAvailability, processingConfig ->
        SummarizeUiState(
            activeSession = activeSession,
            recentSessions = recentSessions,
            whisperAvailability = whisperAvailability,
            llmAvailability = llmAvailability,
            whisperSuggestedModels = whisperSuggestedModels,
            llmSuggestedModels = llmSuggestedModels,
            processingConfig = processingConfig,
        )
    }

    val uiState: StateFlow<SummarizeUiState> = combine(
        baseUiState,
        transientMessage,
    ) { base, message ->
        base.copy(transientMessage = message)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SummarizeUiState(),
    )

    fun onSharedAudiosReceived(sharedAudios: List<SharedAudioInput>) {
        if (sharedAudios.isEmpty()) {
            return
        }

        viewModelScope.launch {
            enqueueSharedAudioUseCase(sharedAudios)
                .onSuccess { sessionIds ->
                    activeSessionId.value = sessionIds.firstOrNull()
                    transientMessage.value = null
                }
                .onFailure { throwable ->
                    transientMessage.value = throwable.message ?: "Import failed"
                }
        }
    }

    fun onSessionSelected(sessionId: String) {
        activeSessionId.value = sessionId
    }

    fun onCancelRequested() {
        val sessionId = activeSessionId.value ?: return
        viewModelScope.launch {
            cancelSessionUseCase(sessionId)
                .onFailure { throwable ->
                    transientMessage.value = throwable.message ?: "Unable to cancel"
                }
        }
    }

    fun onRetryRequested() {
        val sessionId = activeSessionId.value ?: return
        viewModelScope.launch {
            requeueSessionUseCase(sessionId)
                .onSuccess {
                    transientMessage.value = null
                }
                .onFailure { throwable ->
                    transientMessage.value = throwable.message ?: "Unable to retry"
                }
        }
    }

    fun clearMessage() {
        transientMessage.value = null
    }

    fun onProcessingConfigChanged(config: ProcessingConfig) {
        viewModelScope.launch {
            setProcessingConfigUseCase(config)
                .onFailure { throwable ->
                    transientMessage.value = throwable.message ?: "Unable to save settings"
                }
        }
    }

    fun onDownloadSuggestedModel(model: SuggestedModel) {
        viewModelScope.launch {
            if (model.modelId == ModelId.Llm) {
                val currentConfig = uiState.value.processingConfig
                setProcessingConfigUseCase(currentConfig.copy(llmModelFileName = model.fileName))
                    .onFailure { throwable ->
                        transientMessage.value = throwable.message ?: "Unable to save model selection"
                    }
            }

            downloadSuggestedModelUseCase(model)
                .onSuccess {
                    transientMessage.value = "Downloading ${model.displayName}"
                }
                .onFailure { throwable ->
                    transientMessage.value = throwable.message ?: "Unable to start download"
                }
        }
    }

    fun onCancelDownload(modelId: ModelId) {
        viewModelScope.launch {
            cancelModelDownloadUseCase(modelId)
                .onFailure { throwable ->
                    transientMessage.value = throwable.message ?: "Unable to cancel download"
                }
        }
    }
}
