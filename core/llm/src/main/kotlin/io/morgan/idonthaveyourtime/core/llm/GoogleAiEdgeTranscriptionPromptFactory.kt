package io.morgan.idonthaveyourtime.core.llm

import io.morgan.idonthaveyourtime.core.model.LanguageHint

internal object GoogleAiEdgeTranscriptionPromptFactory {

    fun userPrompt(languageHint: LanguageHint): String = when (languageHint) {
        LanguageHint.Auto ->
            "Transcribe the spoken audio exactly as text. Keep the original language. Do not summarize, explain, paraphrase, or invent content. Return only the transcript."

        is LanguageHint.Fixed ->
            "The spoken audio is in ${languageHint.languageCode}. Transcribe it exactly in that language. Do not summarize, explain, paraphrase, or invent content. Return only the transcript."
    }
}
