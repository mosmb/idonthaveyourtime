package io.morgan.idonthaveyourtime.core.model

sealed interface RemoteModelDownloadState {
    data object Idle : RemoteModelDownloadState
    data class Downloading(val progress: Float) : RemoteModelDownloadState
    data class Failed(val message: String) : RemoteModelDownloadState
}

