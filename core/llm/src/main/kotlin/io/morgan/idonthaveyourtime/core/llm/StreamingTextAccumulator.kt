package io.morgan.idonthaveyourtime.core.llm

internal object StreamingTextAccumulator {
    fun append(
        previous: String,
        incoming: String,
    ): String {
        val nextChunk = incoming.trim()
        if (nextChunk.isEmpty()) {
            return previous
        }
        if (previous.isEmpty()) {
            return nextChunk
        }
        if (nextChunk.startsWith(previous)) {
            return nextChunk
        }
        if (previous.endsWith(nextChunk)) {
            return previous
        }
        return previous + nextChunk
    }
}
