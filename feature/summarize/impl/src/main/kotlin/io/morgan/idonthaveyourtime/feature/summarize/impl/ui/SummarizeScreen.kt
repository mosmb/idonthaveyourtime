package io.morgan.idonthaveyourtime.feature.summarize.impl.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.morgan.idonthaveyourtime.core.designsystem.IdntTheme
import io.morgan.idonthaveyourtime.core.designsystem.IdonthaveyourtimeTheme
import io.morgan.idonthaveyourtime.core.designsystem.components.IdntButton
import io.morgan.idonthaveyourtime.core.designsystem.components.IdntButtonVariant
import io.morgan.idonthaveyourtime.core.designsystem.components.IdntCard
import io.morgan.idonthaveyourtime.core.designsystem.components.IdntChoiceChip
import io.morgan.idonthaveyourtime.core.designsystem.components.IdntChip
import io.morgan.idonthaveyourtime.core.designsystem.components.IdntDialog
import io.morgan.idonthaveyourtime.core.designsystem.components.IdntModalSheet
import io.morgan.idonthaveyourtime.core.designsystem.components.IdntProgressBar
import io.morgan.idonthaveyourtime.core.designsystem.components.IdntRadioRow
import io.morgan.idonthaveyourtime.core.designsystem.components.IdntSurface
import io.morgan.idonthaveyourtime.core.designsystem.components.IdntText
import io.morgan.idonthaveyourtime.core.designsystem.components.IdntToastHost
import io.morgan.idonthaveyourtime.core.designsystem.components.IdntTopBar
import io.morgan.idonthaveyourtime.core.designsystem.reducedMotionEnabled
import io.morgan.idonthaveyourtime.core.model.ModelAvailability
import io.morgan.idonthaveyourtime.core.model.ModelId
import io.morgan.idonthaveyourtime.core.model.ProcessingConfig
import io.morgan.idonthaveyourtime.core.model.ProcessingSession
import io.morgan.idonthaveyourtime.core.model.ProcessingStage
import io.morgan.idonthaveyourtime.core.model.SharedAudioInput
import io.morgan.idonthaveyourtime.core.model.SuggestedModel
import io.morgan.idonthaveyourtime.core.model.SummarizerModelFormat
import io.morgan.idonthaveyourtime.core.model.SummarizerRuntime
import io.morgan.idonthaveyourtime.core.model.TranscriptionModelFormat
import io.morgan.idonthaveyourtime.core.model.TranscriptionRuntime
import io.morgan.idonthaveyourtime.feature.summarize.impl.R
import io.morgan.idonthaveyourtime.feature.summarize.impl.SummarizeUiState
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun SummarizeScreen(
    uiState: StateFlow<SummarizeUiState>,
    onSessionSelected: (String) -> Unit,
    onSharedAudiosReceived: (List<SharedAudioInput>) -> Unit,
    onProcessingConfigChanged: (ProcessingConfig) -> Unit,
    onDownloadSuggestedModel: (SuggestedModel) -> Unit,
    onCancelDownload: (ModelId) -> Unit,
    onCancelRequested: () -> Unit,
    onRetryRequested: () -> Unit,
    onDismissMessage: () -> Unit,
) {
    val state by uiState.collectAsState()
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var settingsOpen by rememberSaveable { mutableStateOf(false) }
    var downloadDialogModelId by rememberSaveable { mutableStateOf<ModelId?>(null) }
    var toastMessage by remember { mutableStateOf<String?>(null) }

    val copiedMessage = stringResource(R.string.copied)

    val audioPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri ->
            if (uri == null) return@rememberLauncherForActivityResult
            onSharedAudiosReceived(
                listOf(
                    SharedAudioInput(
                        uriToken = uri.toString(),
                        mimeType = context.contentResolver.getType(uri),
                        displayName = null,
                        sizeBytes = null,
                    )
                )
            )
        },
    )

    val transcriptionImportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri ->
            if (uri == null) return@rememberLauncherForActivityResult
            coroutineScope.launch {
                importModelFile(
                    context = context,
                    uri = uri,
                    allowedExtensions = TRANSCRIPTION_FILE_EXTENSIONS,
                    defaultFileName = state.processingConfig.transcriptionModelFileName,
                ).fold(
                    onSuccess = { fileName ->
                        toastMessage = "Imported transcription model as $fileName"
                        if (fileName != state.processingConfig.transcriptionModelFileName) {
                            onProcessingConfigChanged(
                                state.processingConfig.copy(transcriptionModelFileName = fileName),
                            )
                        }
                    },
                    onFailure = { throwable ->
                        toastMessage = throwable.message ?: "Unable to import transcription model"
                    },
                )
            }
        },
    )

    val llmImportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri ->
            if (uri == null) return@rememberLauncherForActivityResult
            coroutineScope.launch {
                importModelFile(
                    context = context,
                    uri = uri,
                    allowedExtensions = LLM_FILE_EXTENSIONS,
                    defaultFileName = state.processingConfig.llmModelFileName,
                ).fold(
                    onSuccess = { fileName ->
                        toastMessage = "Imported LLM model as $fileName"
                        if (fileName != state.processingConfig.llmModelFileName) {
                            onProcessingConfigChanged(state.processingConfig.copy(llmModelFileName = fileName))
                        }
                    },
                    onFailure = { throwable ->
                        toastMessage = throwable.message ?: "Unable to import LLM model"
                    },
                )
            }
        },
    )

    LaunchedEffect(state.transientMessage) {
        val message = state.transientMessage ?: return@LaunchedEffect
        toastMessage = message
        onDismissMessage()
    }

    BackHandler(enabled = settingsOpen) {
        settingsOpen = false
    }

    val clipboard = remember(context) {
        context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    }

    SummarizeScreenContent(
        state = state,
        settingsOpen = settingsOpen,
        downloadDialogModelId = downloadDialogModelId,
        toastMessage = toastMessage,
        onSettingsOpen = { settingsOpen = true },
        onSettingsDismiss = { settingsOpen = false },
        onSessionSelected = onSessionSelected,
        onPickAudioRequested = { audioPickerLauncher.launch(arrayOf("audio/*")) },
        onCancelRequested = onCancelRequested,
        onRetryRequested = onRetryRequested,
        onCopySummary = { summary ->
            clipboard.setPrimaryClip(ClipData.newPlainText("summary", summary))
            toastMessage = copiedMessage
        },
        onShareSummary = { summary -> shareText(context, summary) },
        onImportTranscriptionRequested = { transcriptionImportLauncher.launch(arrayOf("*/*")) },
        onImportLlmRequested = { llmImportLauncher.launch(arrayOf("*/*")) },
        onOpenTranscriptionDownloadPicker = { downloadDialogModelId = ModelId.Transcription },
        onOpenLlmDownloadPicker = { downloadDialogModelId = ModelId.Llm },
        onCancelDownloadRequested = onCancelDownload,
        onConfigChanged = onProcessingConfigChanged,
        onDownloadModelRequested = { model ->
            onDownloadSuggestedModel(model)
            downloadDialogModelId = null
        },
        onDismissDownloadDialog = { downloadDialogModelId = null },
        onDismissToast = { toastMessage = null },
    )
}

@Composable
private fun SummarizeScreenContent(
    state: SummarizeUiState,
    settingsOpen: Boolean,
    downloadDialogModelId: ModelId?,
    toastMessage: String?,
    onSettingsOpen: () -> Unit,
    onSettingsDismiss: () -> Unit,
    onSessionSelected: (String) -> Unit,
    onPickAudioRequested: () -> Unit,
    onCancelRequested: () -> Unit,
    onRetryRequested: () -> Unit,
    onCopySummary: (String) -> Unit,
    onShareSummary: (String) -> Unit,
    onImportTranscriptionRequested: () -> Unit,
    onImportLlmRequested: () -> Unit,
    onOpenTranscriptionDownloadPicker: () -> Unit,
    onOpenLlmDownloadPicker: () -> Unit,
    onCancelDownloadRequested: (ModelId) -> Unit,
    onConfigChanged: (ProcessingConfig) -> Unit,
    onDownloadModelRequested: (SuggestedModel) -> Unit,
    onDismissDownloadDialog: () -> Unit,
    onDismissToast: () -> Unit,
) {
    val reducedMotion = reducedMotionEnabled()
    val backgroundScale by animateFloatAsState(
        targetValue = if (settingsOpen && !reducedMotion) 0.985f else 1f,
        animationSpec = if (reducedMotion) snap() else tween(durationMillis = IdntTheme.motion.standardMs),
        label = "backgroundScale",
    )
    val modelsReady = state.transcriptionAvailability == ModelAvailability.Ready &&
        state.llmAvailability == ModelAvailability.Ready

    IdntSurface(safeDrawingPadding = false) {
        Box(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .graphicsLayer {
                        scaleX = backgroundScale
                        scaleY = backgroundScale
                    },
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    IdntTopBar(
                        title = stringResource(R.string.screen_title),
                        actionText = stringResource(R.string.settings),
                        onActionClick = onSettingsOpen,
                    )

                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            horizontal = IdntTheme.spacing.m,
                            vertical = IdntTheme.spacing.l,
                        ),
                        verticalArrangement = Arrangement.spacedBy(IdntTheme.spacing.m),
                    ) {
                        if (!modelsReady) {
                            item {
                                ModelSetupBanner(onOpenSettings = onSettingsOpen)
                            }
                        }

                        item {
                            CurrentSessionCard(
                                session = state.activeSession,
                                config = state.processingConfig,
                                onPickAudioRequested = onPickAudioRequested,
                                onCancelRequested = onCancelRequested,
                                onRetryRequested = onRetryRequested,
                                onCopySummary = onCopySummary,
                                onShareSummary = onShareSummary,
                            )
                        }

                        item {
                            IdntText(
                                text = stringResource(R.string.history_title),
                                style = IdntTheme.typography.titleM,
                            )
                        }

                        items(
                            items = state.recentSessions,
                            key = { session -> session.id },
                        ) { session ->
                            HistoryRow(
                                session = session,
                                selected = session.id == state.activeSession?.id,
                                onClick = { onSessionSelected(session.id) },
                            )
                        }
                    }
                }
            }

            IdntModalSheet(
                visible = settingsOpen,
                onDismissRequest = onSettingsDismiss,
            ) {
                SettingsSheetContent(
                    state = state,
                    onImportTranscriptionRequested = onImportTranscriptionRequested,
                    onImportLlmRequested = onImportLlmRequested,
                    onDownloadTranscriptionRequested = onOpenTranscriptionDownloadPicker,
                    onDownloadLlmRequested = onOpenLlmDownloadPicker,
                    onCancelDownloadRequested = onCancelDownloadRequested,
                    onConfigChanged = onConfigChanged,
                )
            }

            downloadDialogModelId?.let { modelId ->
                DownloadModelDialog(
                    modelId = modelId,
                    models = when (modelId) {
                        ModelId.Transcription -> state.transcriptionSuggestedModels
                        ModelId.Llm -> state.llmSuggestedModels
                    },
                    onDismiss = onDismissDownloadDialog,
                    onDownloadRequested = onDownloadModelRequested,
                )
            }

            IdntToastHost(
                message = toastMessage,
                onDismissRequest = onDismissToast,
            )
        }
    }
}

@Composable
private fun ModelSetupBanner(onOpenSettings: () -> Unit) {
    IdntCard(containerColor = IdntTheme.colors.surfaceMuted) {
        IdntText(text = stringResource(R.string.model_setup_required), style = IdntTheme.typography.titleM)
        IdntText(
            text = stringResource(R.string.models_hint),
            style = IdntTheme.typography.bodyS,
            color = IdntTheme.colors.textSecondary,
        )
        IdntButton(
            text = stringResource(R.string.open_settings),
            onClick = onOpenSettings,
            variant = IdntButtonVariant.Primary,
        )
    }
}

@Composable
private fun CurrentSessionCard(
    session: ProcessingSession?,
    config: ProcessingConfig,
    onPickAudioRequested: () -> Unit,
    onCancelRequested: () -> Unit,
    onRetryRequested: () -> Unit,
    onCopySummary: (String) -> Unit,
    onShareSummary: (String) -> Unit,
) {
    if (session == null) {
        IdntCard {
            IdntText(text = stringResource(R.string.idle_title), style = IdntTheme.typography.titleM)
            IdntText(
                text = stringResource(R.string.idle_description),
                style = IdntTheme.typography.body,
                color = IdntTheme.colors.textSecondary,
            )
            IdntButton(
                text = stringResource(R.string.pick_audio_file),
                onClick = onPickAudioRequested,
                variant = IdntButtonVariant.Primary,
            )
        }
        return
    }

    val reducedMotion = reducedMotionEnabled()
    val stage = session.stage

    val statusEnter: EnterTransition = if (reducedMotion) {
        EnterTransition.None
    } else {
        fadeIn(tween(IdntTheme.motion.quickMs)) + slideInVertically(
            initialOffsetY = { fullHeight -> fullHeight / 3 },
            animationSpec = tween(IdntTheme.motion.quickMs),
        )
    }
    val statusExit: ExitTransition = if (reducedMotion) {
        ExitTransition.None
    } else {
        fadeOut(tween(IdntTheme.motion.quickMs)) + slideOutVertically(
            targetOffsetY = { fullHeight -> -fullHeight / 3 },
            animationSpec = tween(IdntTheme.motion.quickMs),
        )
    }

    IdntCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IdntText(
                text = session.sourceName ?: stringResource(R.string.unknown_source),
                style = IdntTheme.typography.titleM,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            val chipColors = chipColorsForStage(stage)
            IdntChip(
                text = stageLabel(stage),
                containerColor = chipColors.container,
                contentColor = chipColors.content,
                borderColor = chipColors.border,
                modifier = Modifier.padding(start = 12.dp),
            )
        }

        AnimatedContent(
            targetState = stage,
            transitionSpec = { statusEnter togetherWith statusExit },
            label = "stageStatus",
        ) { animatedStage ->
            IdntText(
                text = stageStatusText(animatedStage),
                style = IdntTheme.typography.body,
                color = IdntTheme.colors.textSecondary,
            )
        }

        IdntText(
            text = transcriptionStatusLabel(session = session, config = config),
            style = IdntTheme.typography.bodyS,
            color = IdntTheme.colors.textSecondary,
        )
        session.transcriptionDiagnostics
            ?.fallbackReason
            ?.takeIf { it.isNotBlank() }
            ?.let { fallbackReason ->
                IdntText(
                    text = "Fallback: $fallbackReason",
                    style = IdntTheme.typography.bodyS,
                    color = IdntTheme.colors.textSecondary,
                )
            }

        IdntText(
            text = configuredSummarizerLabel(config),
            style = IdntTheme.typography.bodyS,
            color = IdntTheme.colors.textSecondary,
        )

        val transcript = session.transcript.orEmpty().trim()
        if (transcript.isNotBlank()) {
            IdntCard(
                containerColor = IdntTheme.colors.surfaceMuted,
                contentPadding = PaddingValues(12.dp),
            ) {
                IdntText(
                    text = stringResource(R.string.transcript_title),
                    style = IdntTheme.typography.titleM,
                )
                SelectionContainer {
                    IdntText(
                        text = transcript,
                        style = IdntTheme.typography.bodyS,
                        color = IdntTheme.colors.textSecondary,
                    )
                }
            }
        }

        if (stage in processingStages) {
            val progress = session.progress.takeIf { it > 0f }
            IdntProgressBar(progress = progress)
            IdntButton(
                text = stringResource(R.string.cancel),
                onClick = onCancelRequested,
                variant = IdntButtonVariant.Ghost,
            )
        }

        when (stage) {
            ProcessingStage.Success -> {
                val summary = session.summary.orEmpty().trim()
                if (summary.isNotBlank()) {
                    AnimatedVisibility(
                        visible = true,
                        enter = if (reducedMotion) EnterTransition.None else fadeIn(tween(IdntTheme.motion.standardMs)),
                        exit = ExitTransition.None,
                    ) {
                        SummaryCard(
                            title = stringResource(R.string.summary_title),
                            summary = summary,
                            onCopyRequested = { onCopySummary(summary) },
                            onShareRequested = { onShareSummary(summary) },
                        )
                    }
                } else {
                    IdntText(
                        text = stringResource(R.string.summary_missing),
                        style = IdntTheme.typography.bodyS,
                        color = IdntTheme.colors.textSecondary,
                    )
                }
            }

            ProcessingStage.Error,
            ProcessingStage.Cancelled,
            -> {
                val message = session.errorMessage ?: stringResource(R.string.error_unknown)
                IdntText(text = message, style = IdntTheme.typography.body, color = IdntTheme.colors.danger)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    IdntButton(
                        text = stringResource(R.string.retry),
                        onClick = onRetryRequested,
                        variant = IdntButtonVariant.Primary,
                    )
                    IdntButton(
                        text = stringResource(R.string.dismiss),
                        onClick = onCancelRequested,
                        variant = IdntButtonVariant.Ghost,
                    )
                }
            }

            else -> Unit
        }

        if (stage == ProcessingStage.Summarizing) {
            val liveSummary = session.summary.orEmpty().trim()
            if (liveSummary.isNotBlank()) {
                SummaryCard(
                    title = stringResource(R.string.live_summary_title),
                    summary = liveSummary,
                    onCopyRequested = { onCopySummary(liveSummary) },
                    onShareRequested = { onShareSummary(liveSummary) },
                )
            }
        }
    }
}

@Composable
private fun SummaryCard(
    title: String,
    summary: String,
    onCopyRequested: () -> Unit,
    onShareRequested: () -> Unit,
) {
    IdntCard(containerColor = IdntTheme.colors.accentMuted, contentPadding = PaddingValues(12.dp)) {
        IdntText(text = title, style = IdntTheme.typography.titleM)
        SelectionContainer {
            IdntText(text = summary, style = IdntTheme.typography.body)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            IdntButton(
                text = stringResource(R.string.copy_summary),
                onClick = onCopyRequested,
                variant = IdntButtonVariant.Ghost,
            )
            IdntButton(
                text = stringResource(R.string.share_summary),
                onClick = onShareRequested,
                variant = IdntButtonVariant.Secondary,
            )
        }
    }
}

@Composable
private fun HistoryRow(
    session: ProcessingSession,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val containerColor = if (selected) IdntTheme.colors.accentMuted else IdntTheme.colors.surface

    IdntCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                indication = null,
                interactionSource = interactionSource,
                onClick = onClick,
            ),
        containerColor = containerColor,
        contentPadding = PaddingValues(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IdntText(
                text = session.sourceName ?: stringResource(R.string.unknown_source),
                style = IdntTheme.typography.body,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            IdntText(
                text = stageLabel(session.stage),
                style = IdntTheme.typography.bodyS,
                color = IdntTheme.colors.textSecondary,
                modifier = Modifier.padding(start = 12.dp),
                maxLines = 1,
            )
        }

        val preview = session.summary.orEmpty().trim()
        if (preview.isNotBlank()) {
            IdntText(
                text = preview,
                style = IdntTheme.typography.bodyS,
                color = IdntTheme.colors.textSecondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun SettingsSheetContent(
    state: SummarizeUiState,
    onImportTranscriptionRequested: () -> Unit,
    onImportLlmRequested: () -> Unit,
    onDownloadTranscriptionRequested: () -> Unit,
    onDownloadLlmRequested: () -> Unit,
    onCancelDownloadRequested: (ModelId) -> Unit,
    onConfigChanged: (ProcessingConfig) -> Unit,
) {
    val config = state.processingConfig
    val preset = qualityPresetFor(config)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(IdntTheme.spacing.l),
    ) {
        IdntText(text = stringResource(R.string.settings), style = IdntTheme.typography.titleL)

        Column(verticalArrangement = Arrangement.spacedBy(IdntTheme.spacing.s)) {
            IdntText(text = stringResource(R.string.models_title), style = IdntTheme.typography.titleM)
            ModelRow(
                label = stringResource(R.string.transcription_model),
                availability = state.transcriptionAvailability,
                selectedFileName = config.transcriptionModelFileName,
                onImportRequested = onImportTranscriptionRequested,
                onDownloadRequested = onDownloadTranscriptionRequested,
                onCancelDownloadRequested = { onCancelDownloadRequested(ModelId.Transcription) },
            )
            ModelRow(
                label = stringResource(R.string.llm_model),
                availability = state.llmAvailability,
                selectedFileName = config.llmModelFileName,
                onImportRequested = onImportLlmRequested,
                onDownloadRequested = onDownloadLlmRequested,
                onCancelDownloadRequested = { onCancelDownloadRequested(ModelId.Llm) },
            )
            IdntText(
                text = stringResource(R.string.models_hint),
                style = IdntTheme.typography.bodyS,
                color = IdntTheme.colors.textSecondary,
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(IdntTheme.spacing.s)) {
            IdntText(text = stringResource(R.string.quality_title), style = IdntTheme.typography.titleM)

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                IdntChoiceChip(
                    text = stringResource(R.string.preset_fast),
                    selected = preset == QualityPreset.Fast,
                    onClick = { onConfigChanged(applyQualityPreset(config, QualityPreset.Fast)) },
                )
                IdntChoiceChip(
                    text = stringResource(R.string.preset_balanced),
                    selected = preset == QualityPreset.Balanced,
                    onClick = { onConfigChanged(applyQualityPreset(config, QualityPreset.Balanced)) },
                )
                IdntChoiceChip(
                    text = stringResource(R.string.preset_quality),
                    selected = preset == QualityPreset.Quality,
                    onClick = { onConfigChanged(applyQualityPreset(config, QualityPreset.Quality)) },
                )
            }

            if (preset == null) {
                IdntText(
                    text = stringResource(R.string.preset_custom),
                    style = IdntTheme.typography.bodyS,
                    color = IdntTheme.colors.textSecondary,
                )
            }

            IdntText(text = stringResource(R.string.transcription_model), style = IdntTheme.typography.titleM)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                state.transcriptionSuggestedModels.forEach { model ->
                    IdntRadioRow(
                        selected = config.transcriptionModelFileName == model.fileName,
                        label = model.displayName,
                        description = suggestedModelDescription(model),
                        onClick = { onConfigChanged(config.copy(transcriptionModelFileName = model.fileName)) },
                    )
                }
            }

            val modelFormat = SummarizerModelFormat.fromFileName(config.llmModelFileName)
            IdntText(text = stringResource(R.string.model_format_title), style = IdntTheme.typography.titleM)
            IdntText(
                text = modelFormat?.displayName ?: "Unknown",
                style = IdntTheme.typography.bodyS,
                color = IdntTheme.colors.textSecondary,
            )

            IdntText(text = stringResource(R.string.llm_model), style = IdntTheme.typography.titleM)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                state.llmSuggestedModels.forEach { model ->
                    IdntRadioRow(
                        selected = config.llmModelFileName == model.fileName,
                        label = model.displayName,
                        description = suggestedModelDescription(model),
                        onClick = { onConfigChanged(config.copy(llmModelFileName = model.fileName)) },
                    )
                }
            }
        }
    }
}

@Composable
private fun ModelRow(
    label: String,
    availability: ModelAvailability,
    selectedFileName: String,
    onImportRequested: () -> Unit,
    onDownloadRequested: () -> Unit,
    onCancelDownloadRequested: () -> Unit,
) {
    IdntCard(containerColor = IdntTheme.colors.surfaceMuted, contentPadding = PaddingValues(12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IdntText(text = label, style = IdntTheme.typography.body)
            IdntText(
                text = modelStatusText(availability),
                style = IdntTheme.typography.bodyS,
                color = IdntTheme.colors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        IdntText(
            text = selectedFileName,
            style = IdntTheme.typography.mono,
            color = IdntTheme.colors.textSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        when (availability) {
            ModelAvailability.Ready -> Unit
            is ModelAvailability.Downloading -> {
                IdntProgressBar(progress = availability.progress.coerceIn(0f, 1f))
                IdntButton(
                    text = stringResource(R.string.cancel_download),
                    onClick = onCancelDownloadRequested,
                    variant = IdntButtonVariant.Ghost,
                )
            }

            ModelAvailability.Missing,
            is ModelAvailability.Failed,
            -> {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    IdntButton(
                        text = stringResource(R.string.import_model),
                        onClick = onImportRequested,
                        variant = IdntButtonVariant.Ghost,
                    )
                    IdntButton(
                        text = stringResource(R.string.download_from_huggingface),
                        onClick = onDownloadRequested,
                        variant = IdntButtonVariant.Secondary,
                    )
                }
            }
        }
    }
}

@Composable
private fun DownloadModelDialog(
    modelId: ModelId,
    models: List<SuggestedModel>,
    onDismiss: () -> Unit,
    onDownloadRequested: (SuggestedModel) -> Unit,
) {
    val title = when (modelId) {
        ModelId.Transcription -> stringResource(R.string.download_transcription_title)
        ModelId.Llm -> stringResource(R.string.download_llm_title)
    }

    IdntDialog(onDismissRequest = onDismiss) {
        Column(verticalArrangement = Arrangement.spacedBy(IdntTheme.spacing.s)) {
            IdntText(text = title, style = IdntTheme.typography.titleM)
            IdntText(
                text = stringResource(R.string.download_warning_any_network),
                style = IdntTheme.typography.bodyS,
                color = IdntTheme.colors.textSecondary,
            )
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 360.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(vertical = 6.dp),
            ) {
                items(
                    items = models,
                    key = { model -> model.fileName },
                ) { model ->
                    IdntCard(containerColor = IdntTheme.colors.surfaceMuted, contentPadding = PaddingValues(12.dp)) {
                        IdntText(text = model.displayName, style = IdntTheme.typography.body)
                        IdntText(
                            text = model.description,
                            style = IdntTheme.typography.bodyS,
                            color = IdntTheme.colors.textSecondary,
                        )
                        IdntText(
                            text = model.fileName,
                            style = IdntTheme.typography.mono,
                            color = IdntTheme.colors.textSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                        ) {
                            IdntButton(
                                text = stringResource(R.string.download),
                                onClick = { onDownloadRequested(model) },
                                variant = IdntButtonVariant.Primary,
                            )
                        }
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                IdntButton(
                    text = stringResource(R.string.close),
                    onClick = onDismiss,
                    variant = IdntButtonVariant.Ghost,
                )
            }
        }
    }
}

@Composable
private fun modelStatusText(availability: ModelAvailability): String = when (availability) {
    ModelAvailability.Missing -> stringResource(R.string.model_missing)
    ModelAvailability.Ready -> stringResource(R.string.model_ready)
    is ModelAvailability.Downloading -> "${(availability.progress.coerceIn(0f, 1f) * 100).toInt()}%"
    is ModelAvailability.Failed -> availability.message
}

private fun shareText(context: Context, text: String) {
    val intent = Intent(Intent.ACTION_SEND)
        .setType("text/plain")
        .putExtra(Intent.EXTRA_TEXT, text)
    context.startActivity(Intent.createChooser(intent, null))
}

private fun stageLabel(stage: ProcessingStage): String = when (stage) {
    ProcessingStage.Idle -> "Idle"
    ProcessingStage.Importing -> "Importing"
    ProcessingStage.Queued -> "Queued"
    ProcessingStage.Converting -> "Converting"
    ProcessingStage.Transcribing -> "Transcribing"
    ProcessingStage.Summarizing -> "Summarizing"
    ProcessingStage.Success -> "Success"
    ProcessingStage.Error -> "Error"
    ProcessingStage.Cancelled -> "Cancelled"
}

private fun stageStatusText(stage: ProcessingStage): String = when (stage) {
    ProcessingStage.Importing,
    ProcessingStage.Queued,
    ProcessingStage.Converting,
    ProcessingStage.Transcribing,
    ProcessingStage.Summarizing,
    -> "${stageLabel(stage)}…"

    ProcessingStage.Success -> "Complete"
    else -> stageLabel(stage)
}

private data class ChipColors(
    val container: androidx.compose.ui.graphics.Color,
    val content: androidx.compose.ui.graphics.Color,
    val border: androidx.compose.ui.graphics.Color,
)

@Composable
private fun chipColorsForStage(stage: ProcessingStage): ChipColors = when (stage) {
    ProcessingStage.Success -> ChipColors(
        container = IdntTheme.colors.accentMuted,
        content = IdntTheme.colors.textPrimary,
        border = IdntTheme.colors.accent,
    )

    ProcessingStage.Error -> ChipColors(
        container = IdntTheme.colors.danger.copy(alpha = 0.12f),
        content = IdntTheme.colors.danger,
        border = IdntTheme.colors.danger,
    )

    else -> ChipColors(
        container = IdntTheme.colors.surfaceMuted,
        content = IdntTheme.colors.textSecondary,
        border = IdntTheme.colors.outline,
    )
}

private enum class QualityPreset {
    Fast,
    Balanced,
    Quality,
}

private fun qualityPresetFor(config: ProcessingConfig): QualityPreset? = when {
    config.segmentationTargetSpeechMs == 20_000L &&
        config.segmentationOverlapMs == 300L &&
        config.mapEverySegments == 4 ->
        QualityPreset.Fast

    config.segmentationTargetSpeechMs == 15_000L &&
        config.segmentationOverlapMs == 600L &&
        config.mapEverySegments == 3 ->
        QualityPreset.Balanced

    config.segmentationTargetSpeechMs == 20_000L &&
        config.segmentationOverlapMs == 800L &&
        config.mapEverySegments == 2 ->
        QualityPreset.Quality

    else -> null
}

private fun applyQualityPreset(config: ProcessingConfig, preset: QualityPreset): ProcessingConfig = when (preset) {
    QualityPreset.Fast -> config.copy(
        segmentationTargetSpeechMs = 20_000L,
        segmentationOverlapMs = 300L,
        mapEverySegments = 4,
    )

    QualityPreset.Balanced -> config.copy(
        segmentationTargetSpeechMs = 15_000L,
        segmentationOverlapMs = 600L,
        mapEverySegments = 3,
    )

    QualityPreset.Quality -> config.copy(
        segmentationTargetSpeechMs = 20_000L,
        segmentationOverlapMs = 800L,
        mapEverySegments = 2,
    )
}

private val processingStages = setOf(
    ProcessingStage.Importing,
    ProcessingStage.Queued,
    ProcessingStage.Converting,
    ProcessingStage.Transcribing,
    ProcessingStage.Summarizing,
)

private val LLM_FILE_EXTENSIONS = SummarizerModelFormat.entries
    .map { format -> format.fileExtension }
    .toSet()

private val TRANSCRIPTION_FILE_EXTENSIONS = TranscriptionModelFormat.entries
    .map { format -> format.fileExtension }
    .toSet()

@Preview(name = "Summarize Screen", showBackground = true, backgroundColor = 0xFFFAFAFA)
@Composable
private fun SummarizeScreenPreview() {
    IdonthaveyourtimeTheme {
        SummarizeScreenContent(
            state = previewUiState(),
            settingsOpen = false,
            downloadDialogModelId = null,
            toastMessage = null,
            onSettingsOpen = {},
            onSettingsDismiss = {},
            onSessionSelected = {},
            onPickAudioRequested = {},
            onCancelRequested = {},
            onRetryRequested = {},
            onCopySummary = {},
            onShareSummary = {},
            onImportTranscriptionRequested = {},
            onImportLlmRequested = {},
            onOpenTranscriptionDownloadPicker = {},
            onOpenLlmDownloadPicker = {},
            onCancelDownloadRequested = {},
            onConfigChanged = {},
            onDownloadModelRequested = {},
            onDismissDownloadDialog = {},
            onDismissToast = {},
        )
    }
}

private fun previewUiState(): SummarizeUiState {
    val suggestedModels = listOf(
        SuggestedModel(
            modelId = ModelId.Llm,
            displayName = "Gemma 4 E4B IT",
            description = "Larger LiteRT-LM model",
            huggingFaceRepoId = "litert-community/gemma-4-E4B-it-litert-lm",
            fileName = "gemma-4-E4B-it.litertlm",
            summarizerModelFormat = SummarizerModelFormat.LiteRtLm,
        ),
        SuggestedModel(
            modelId = ModelId.Llm,
            displayName = "Gemma 4 E2B IT",
            description = "Preferred LiteRT-LM path",
            huggingFaceRepoId = "litert-community/gemma-4-E2B-it-litert-lm",
            fileName = "gemma-4-E2B-it.litertlm",
            summarizerModelFormat = SummarizerModelFormat.LiteRtLm,
        ),
        SuggestedModel(
            modelId = ModelId.Llm,
            displayName = "Qwen2.5 0.5B Instruct",
            description = "MediaPipe `.task` sample",
            huggingFaceRepoId = "diamondbelema/edu-hive-llm-models",
            fileName = "Qwen2.5-0.5B-Instruct_multi-prefill-seq_q8_ekv1280.task",
            summarizerModelFormat = SummarizerModelFormat.Task,
        ),
    )

    val activeSession = ProcessingSession(
        id = "preview-session",
        createdAtEpochMs = 1_748_700_000_000,
        sourceName = "voice-note-2026-02-27.m4a",
        mimeType = "audio/m4a",
        durationMs = 53_000,
        stage = ProcessingStage.Success,
        progress = 1f,
        transcript = null,
        summary = "Client wants a complete UI reboot: minimalist style, no Material components, and delightful but restrained animations.",
        languageCode = "en",
        errorCode = null,
        errorMessage = null,
    )

    val recentSessions = listOf(
        activeSession,
        activeSession.copy(
            id = "recent-transcribing",
            sourceName = "meeting-update.m4a",
            stage = ProcessingStage.Transcribing,
            progress = 0.42f,
            summary = null,
        ),
        activeSession.copy(
            id = "recent-error",
            sourceName = "memo-failed.m4a",
            stage = ProcessingStage.Error,
            progress = 0f,
            summary = null,
            errorMessage = "Model file is missing",
        ),
    )

    return SummarizeUiState(
        activeSession = activeSession,
        recentSessions = recentSessions,
        transcriptionAvailability = ModelAvailability.Ready,
        llmAvailability = ModelAvailability.Ready,
        transcriptionSuggestedModels = emptyList(),
        llmSuggestedModels = suggestedModels,
        processingConfig = ProcessingConfig(),
        transientMessage = null,
    )
}

private suspend fun importModelFile(
    context: Context,
    uri: Uri,
    allowedFileNames: Set<String> = emptySet(),
    allowedExtensions: Set<String> = emptySet(),
    defaultFileName: String,
): Result<String> = withContext(Dispatchers.IO) {
    runCatching {
        val resolver = context.contentResolver
        val displayName = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
            ?.substringAfterLast('/')
            ?.trim()
            .orEmpty()

        val extension = displayName.substringAfterLast('.', missingDelimiterValue = "")
            .lowercase()
            .trim()

        val targetName = when {
            allowedFileNames.contains(displayName) -> displayName
            allowedExtensions.contains(extension) -> displayName
            allowedFileNames.isNotEmpty() -> defaultFileName
            else -> throw IllegalStateException("Unsupported model file type: $displayName")
        }
        val targetDirectory = File(context.filesDir, "models").apply { mkdirs() }
        val targetFile = File(targetDirectory, targetName)

        resolver.openInputStream(uri)?.use { input ->
            targetFile.outputStream().use { output ->
                input.copyTo(output)
            }
        } ?: throw IllegalStateException("Unable to open selected file")

        targetName
    }
}

private fun suggestedModelDescription(model: SuggestedModel): String = buildString {
    append(model.description)
    model.transcriptionModelFormat?.let { format ->
        append(" • ")
        append(format.displayName)
    }
    model.summarizerModelFormat?.let { format ->
        append(" • ")
        append(format.displayName)
    }
}

private fun transcriptionStatusLabel(
    session: ProcessingSession,
    config: ProcessingConfig,
): String {
    val diagnostics = session.transcriptionDiagnostics
    if (diagnostics != null) {
        return buildString {
            append("Transcription: ")
            append(diagnostics.runtime.displayName)
            diagnostics.backendName
                ?.takeIf { it.isNotBlank() }
                ?.let { backendName ->
                    append(" • ")
                    append(backendName)
                }
            diagnostics.modelFileName
                ?.takeIf { it.isNotBlank() }
                ?.let { modelFileName ->
                    append(" • ")
                    append(modelFileName)
                }
        }
    }

    return configuredTranscriptionLabel(config)
}

internal fun configuredTranscriptionLabel(config: ProcessingConfig): String {
    val modelLabel = config.transcriptionModelFileName.trim()
        .substringAfterLast('/')
        .ifBlank { "unconfigured model" }

    val runtimeLabel = when (TranscriptionModelFormat.fromFileName(config.transcriptionModelFileName)) {
        TranscriptionModelFormat.LiteRtLm -> TranscriptionRuntime.GoogleAiEdgeLiteRtLm.displayName
        null -> "Unconfigured"
    }

    return "Transcription: $runtimeLabel • $modelLabel"
}

private fun configuredSummarizerLabel(config: ProcessingConfig): String {
    val modelLabel = config.llmModelFileName.trim()
        .substringAfterLast('/')
        .ifBlank { "unconfigured model" }
    val runtimeLabel = when (SummarizerModelFormat.fromFileName(config.llmModelFileName)) {
        SummarizerModelFormat.LiteRtLm -> SummarizerRuntime.LiteRtLm.displayName
        SummarizerModelFormat.Task -> SummarizerRuntime.MediaPipeLlmInference.displayName
        null -> "Unconfigured"
    }
    return "Summarizer: $runtimeLabel • $modelLabel"
}
