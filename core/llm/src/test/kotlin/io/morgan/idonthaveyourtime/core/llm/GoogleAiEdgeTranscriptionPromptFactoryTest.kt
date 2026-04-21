package io.morgan.idonthaveyourtime.core.llm

import io.morgan.idonthaveyourtime.core.model.LanguageHint
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GoogleAiEdgeTranscriptionPromptFactoryTest {

    @Test
    fun `auto language prompt requests verbatim transcript only`() {
        val prompt = GoogleAiEdgeTranscriptionPromptFactory.userPrompt(LanguageHint.Auto)

        assertTrue(prompt.contains("Return only the transcript"))
        assertFalse(prompt.contains("translate"))
    }
}
