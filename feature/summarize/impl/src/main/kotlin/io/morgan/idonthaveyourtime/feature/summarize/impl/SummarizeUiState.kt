package io.morgan.idonthaveyourtime.feature.summarize.impl

import io.morgan.idonthaveyourtime.core.model.ModelAvailability
import io.morgan.idonthaveyourtime.core.model.ProcessingConfig
import io.morgan.idonthaveyourtime.core.model.ProcessingSession
import io.morgan.idonthaveyourtime.core.model.SuggestedModel

data class SummarizeUiState(
    val activeSession: ProcessingSession? = null,
    val recentSessions: List<ProcessingSession> = emptyList(),
    val transcriptionAvailability: ModelAvailability = ModelAvailability.Missing,
    val llmAvailability: ModelAvailability = ModelAvailability.Missing,
    val transcriptionSuggestedModels: List<SuggestedModel> = emptyList(),
    val llmSuggestedModels: List<SuggestedModel> = emptyList(),
    val processingConfig: ProcessingConfig = ProcessingConfig(),
    val transientMessage: String? = null,
)
