package io.morgan.idonthaveyourtime.core.domain.transcript

import kotlin.math.min

object TranscriptOverlapMerger {
    fun dropBestOverlapPrefix(
        previousText: String,
        nextText: String,
        maxTokens: Int = 15,
        minOverlapTokens: Int = 3,
    ): String {
        val prevTokens = tokenize(previousText).takeLast(maxTokens)
        val nextTokens = tokenize(nextText).take(maxTokens)

        if (prevTokens.isEmpty() || nextTokens.isEmpty()) {
            return nextText.trim()
        }

        val maxPossible = min(prevTokens.size, nextTokens.size)
        for (k in maxPossible downTo minOverlapTokens) {
            val prevSuffix = prevTokens.takeLast(k)
            val nextPrefix = nextTokens.take(k)
            if (equalsIgnoreCase(prevSuffix, nextPrefix)) {
                return tokenize(nextText)
                    .drop(k)
                    .joinToString(separator = " ")
                    .trim()
            }
        }

        return nextText.trim()
    }

    private fun tokenize(text: String): List<String> =
        text.trim()
            .split(WHITESPACE)
            .filter { it.isNotBlank() }

    private fun equalsIgnoreCase(a: List<String>, b: List<String>): Boolean {
        if (a.size != b.size) return false
        for (i in a.indices) {
            if (!a[i].equals(b[i], ignoreCase = true)) return false
        }
        return true
    }

    private val WHITESPACE = "\\s+".toRegex()
}

