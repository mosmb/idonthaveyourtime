package io.morgan.idonthaveyourtime.feature.summarize.impl

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.hilt.navigation.compose.hiltViewModel
import io.morgan.idonthaveyourtime.core.model.SharedAudioInput
import io.morgan.idonthaveyourtime.feature.summarize.impl.ui.SummarizeScreen

@Composable
fun SummarizeRoute(
    incomingSharedAudio: List<SharedAudioInput>,
    onIncomingHandled: () -> Unit,
    viewModel: SummarizeViewModel = hiltViewModel(),
) {
    LaunchedEffect(incomingSharedAudio) {
        if (incomingSharedAudio.isNotEmpty()) {
            viewModel.onSharedAudiosReceived(incomingSharedAudio)
            onIncomingHandled()
        }
    }

    SummarizeScreen(
        uiState = viewModel.uiState,
        onSessionSelected = viewModel::onSessionSelected,
        onSharedAudiosReceived = viewModel::onSharedAudiosReceived,
        onProcessingConfigChanged = viewModel::onProcessingConfigChanged,
        onDownloadSuggestedModel = viewModel::onDownloadSuggestedModel,
        onCancelDownload = viewModel::onCancelDownload,
        onCancelRequested = viewModel::onCancelRequested,
        onRetryRequested = viewModel::onRetryRequested,
        onDismissMessage = viewModel::clearMessage,
    )
}
