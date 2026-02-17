package io.morgan.idonthaveyourtime.core.model

sealed interface LanguageHint {
    data object Auto : LanguageHint
    data class Fixed(val languageCode: String) : LanguageHint
}
