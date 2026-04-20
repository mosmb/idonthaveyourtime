package io.morgan.idonthaveyourtime.core.llm

import io.morgan.idonthaveyourtime.core.model.SummarizerModelFormat
import java.util.Locale

internal data class SummarizationPrompt(
    val systemPrompt: String,
    val userPrompt: String,
    val maxOutputTokens: Int,
) {
    fun asSinglePrompt(): String = buildString {
        appendLine(systemPrompt)
        appendLine()
        append(userPrompt)
    }.trim()
}

internal object SummarizationPromptFactory {

    fun mapChunk(
        transcriptChunk: String,
        languageCode: String?,
    ): SummarizationPrompt {
        val targetLanguage = resolveTargetLanguage(languageCode)
        val chunkText = transcriptChunk.trim().take(MAX_INPUT_CHARS)

        return SummarizationPrompt(
            systemPrompt = "Summarize transcript chunks accurately. Use only facts present. Keep names, dates, and numbers exact. Output bullets only.",
            userPrompt = buildString {
                appendLine("Return 3 to 6 concise bullets.")
                appendLine("Language: $targetLanguage")
                appendLine("Chunk:")
                appendLine("<<")
                appendLine(chunkText)
                appendLine(">>")
            }.trim(),
            maxOutputTokens = MAP_MAX_TOKENS,
        )
    }

    fun reduce(
        chunkBulletSummaries: List<String>,
        languageCode: String?,
    ): SummarizationPrompt {
        val targetLanguage = resolveTargetLanguage(languageCode)
        val joinedBullets = chunkBulletSummaries
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .joinToString(separator = "\n")
            .take(MAX_INPUT_CHARS)

        return SummarizationPrompt(
            systemPrompt = "Write deterministic transcript summaries. Use only supplied facts. No preamble. Preserve concrete details.",
            userPrompt = buildString {
                appendLine("Return exactly this structure:")
                appendLine("Title: <one line>")
                appendLine("TL;DR: <1 to 2 sentences>")
                appendLine("Key points:")
                appendLine("- <bullet>")
                appendLine("Action items:")
                appendLine("- <bullet> or None")
                appendLine("Language: $targetLanguage")
                appendLine("Chunk bullets:")
                appendLine("<<")
                appendLine(joinedBullets)
                appendLine(">>")
            }.trim(),
            maxOutputTokens = REDUCE_MAX_TOKENS,
        )
    }

    fun normalizeMapOutput(raw: String): String {
        val lines = raw.lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        val bulletLines = lines.filter { line ->
            line.startsWith("-") || line.startsWith("•") || line.startsWith("*")
        }

        val selected = if (bulletLines.isNotEmpty()) bulletLines else lines
        return selected
            .take(6)
            .joinToString(separator = "\n") { line ->
                when {
                    line.startsWith("-") -> line
                    line.startsWith("•") -> "- ${line.drop(1).trimStart()}"
                    line.startsWith("*") -> "- ${line.drop(1).trimStart()}"
                    else -> "- $line"
                }
            }
            .trim()
    }

    fun normalizeReduceOutput(raw: String): String = raw.trim()

    fun supportsModelFormat(
        format: SummarizerModelFormat?,
        supportedFormats: Set<SummarizerModelFormat>,
    ): Boolean = format != null && supportedFormats.contains(format)

    private fun resolveTargetLanguage(languageCode: String?): String {
        val normalized = languageCode?.trim().orEmpty()
        if (normalized.isBlank() || normalized.equals("auto", ignoreCase = true) || normalized.equals("und", ignoreCase = true)) {
            return Locale.getDefault().language.ifBlank { "en" }
        }
        return normalized
    }

    private const val MAX_INPUT_CHARS = 12_000
    private const val MAP_MAX_TOKENS = 160
    private const val REDUCE_MAX_TOKENS = 320
}
