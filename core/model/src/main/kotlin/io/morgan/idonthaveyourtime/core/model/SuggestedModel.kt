package io.morgan.idonthaveyourtime.core.model

data class SuggestedModel(
    val modelId: ModelId,
    val displayName: String,
    val description: String,
    val huggingFaceRepoId: String,
    val revision: String = "main",
    val fileName: String,
    val transcriptionModelFormat: TranscriptionModelFormat? = null,
    val summarizerModelFormat: SummarizerModelFormat? = null,
) {
    val downloadUrl: String
        get() = "https://huggingface.co/$huggingFaceRepoId/resolve/$revision/$fileName?download=true"
}
