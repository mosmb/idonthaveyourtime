package io.morgan.idonthaveyourtime.core.llm

internal object LlamaJni {
    init {
        LlamaLibraryLoader.ensureLoaded()
    }

    external fun initContext(
        modelPath: String,
        nCtx: Int,
        nThreads: Int,
    ): Long

    external fun freeContext(contextPtr: Long)

    external fun generateChat(
        contextPtr: Long,
        system: String,
        user: String,
        maxTokens: Int,
        temperature: Float,
        topP: Float,
        topK: Int,
    ): String

    external fun requestAbort(contextPtr: Long)
}

