package io.morgan.idonthaveyourtime.core.model

sealed interface ModelAvailability {
    data object Ready : ModelAvailability
    data object Missing : ModelAvailability
    data class Downloading(val progress: Float) : ModelAvailability
    data class Failed(val message: String) : ModelAvailability
}
