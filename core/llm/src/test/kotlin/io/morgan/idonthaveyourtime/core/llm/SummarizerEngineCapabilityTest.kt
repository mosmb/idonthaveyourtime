package io.morgan.idonthaveyourtime.core.llm

import android.content.ContextWrapper
import io.morgan.idonthaveyourtime.core.data.datasource.model.ModelLocatorLocalDataSource
import io.morgan.idonthaveyourtime.core.model.ModelAvailability
import io.morgan.idonthaveyourtime.core.model.ModelId
import io.morgan.idonthaveyourtime.core.model.SummarizerModelFormat
import io.morgan.idonthaveyourtime.core.model.SummarizerRuntime
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SummarizerEngineCapabilityTest {

    @Test
    fun `LiteRT-LM capability and probe match litertlm contract`() = runTest {
        val engine = liteRtLmEngine("gemma-4-E2B-it.litertlm")

        val capability = engine.capability()
        val probe = engine.probe().getOrThrow()

        assertEquals(SummarizerRuntime.LiteRtLm, capability.runtime)
        assertEquals(setOf(SummarizerModelFormat.LiteRtLm), capability.supportedFormats)
        assertTrue(capability.supportsStreaming)
        assertTrue(capability.supportsAsyncGeneration)
        assertTrue(capability.supportsHardwareAcceleration)
        assertTrue(probe.supported)
        assertEquals(SummarizerRuntime.LiteRtLm, probe.selectedRuntime)
        assertEquals(SummarizerModelFormat.LiteRtLm, probe.modelFormat)
        assertNull(probe.failureReason)
    }

    @Test
    fun `LiteRT-LM probe rejects non litertlm models`() = runTest {
        val probe = liteRtLmEngine("summary.gguf").probe().getOrThrow()

        assertFalse(probe.supported)
        assertNull(probe.modelFormat)
        assertEquals("LiteRT-LM requires a `.litertlm` model.", probe.failureReason)
    }

    @Test
    fun `MediaPipe capability and probe match task contract`() = runTest {
        val engine = mediaPipeEngine("gemma3-270m-it-q8.task")

        val capability = engine.capability()
        val probe = engine.probe().getOrThrow()

        assertEquals(SummarizerRuntime.MediaPipeLlmInference, capability.runtime)
        assertEquals(setOf(SummarizerModelFormat.Task), capability.supportedFormats)
        assertTrue(capability.supportsStreaming)
        assertTrue(capability.supportsAsyncGeneration)
        assertTrue(capability.supportsHardwareAcceleration)
        assertTrue(probe.supported)
        assertEquals(SummarizerRuntime.MediaPipeLlmInference, probe.selectedRuntime)
        assertEquals(SummarizerModelFormat.Task, probe.modelFormat)
        assertNull(probe.failureReason)
    }

    @Test
    fun `MediaPipe probe rejects non task models`() = runTest {
        val probe = mediaPipeEngine("summary.gguf").probe().getOrThrow()

        assertFalse(probe.supported)
        assertNull(probe.modelFormat)
        assertEquals("MediaPipe LLM Inference requires a `.task` model.", probe.failureReason)
    }

    private fun liteRtLmEngine(modelFileName: String): LiteRtLmSummarizerEngineLocalDataSource =
        LiteRtLmSummarizerEngineLocalDataSource(
            context = ContextWrapper(null),
            modelLocator = FakeModelLocatorLocalDataSource(modelFileName),
            defaultDispatcher = StandardTestDispatcher(),
        )

    private fun mediaPipeEngine(modelFileName: String): MediaPipeLlmInferenceSummarizerEngineLocalDataSource =
        MediaPipeLlmInferenceSummarizerEngineLocalDataSource(
            context = ContextWrapper(null),
            modelLocator = FakeModelLocatorLocalDataSource(modelFileName),
            defaultDispatcher = StandardTestDispatcher(),
        )

    private class FakeModelLocatorLocalDataSource(
        private val modelFileName: String,
    ) : ModelLocatorLocalDataSource {

        override fun observeAvailability(modelId: ModelId): Flow<ModelAvailability> = emptyFlow()

        override suspend fun getModelPath(modelId: ModelId): Result<String> =
            Result.success("/tmp/$modelFileName")
    }
}
