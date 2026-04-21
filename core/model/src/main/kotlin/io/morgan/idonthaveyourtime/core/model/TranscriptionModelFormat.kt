package io.morgan.idonthaveyourtime.core.model

enum class TranscriptionModelFormat(
    val displayName: String,
    val fileExtension: String,
) {
    LiteRtLm("LiteRT-LM (.litertlm)", "litertlm"),
    WhisperBin("Whisper (.bin)", "bin"),
    ;

    companion object {
        fun fromFileName(fileName: String?): TranscriptionModelFormat? {
            val extension = fileName
                ?.substringAfterLast('.', missingDelimiterValue = "")
                ?.lowercase()
                ?.trim()
                .orEmpty()
            if (extension.isEmpty()) {
                return null
            }
            return entries.firstOrNull { format -> format.fileExtension == extension }
        }
    }
}
