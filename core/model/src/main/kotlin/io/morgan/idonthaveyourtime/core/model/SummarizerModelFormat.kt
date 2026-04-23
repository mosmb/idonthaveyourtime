package io.morgan.idonthaveyourtime.core.model

enum class SummarizerModelFormat(
    val displayName: String,
    val fileExtension: String,
) {
    LiteRtLm(
        displayName = "LiteRT-LM (.litertlm)",
        fileExtension = "litertlm",
    ),
    Task(
        displayName = "MediaPipe (.task)",
        fileExtension = "task",
    ),
    ;

    companion object {
        fun fromFileName(fileName: String?): SummarizerModelFormat? {
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
