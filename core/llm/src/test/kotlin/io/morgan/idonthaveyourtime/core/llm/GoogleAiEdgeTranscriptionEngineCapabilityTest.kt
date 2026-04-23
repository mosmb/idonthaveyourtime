package io.morgan.idonthaveyourtime.core.llm

import android.content.ContextWrapper
import io.morgan.idonthaveyourtime.core.data.datasource.audio.AudioSampleReaderLocalDataSource
import io.morgan.idonthaveyourtime.core.data.datasource.model.ModelLocatorLocalDataSource
import io.morgan.idonthaveyourtime.core.model.ModelAvailability
import io.morgan.idonthaveyourtime.core.model.ModelId
import io.morgan.idonthaveyourtime.core.model.ProcessingConfig
import io.morgan.idonthaveyourtime.core.model.TranscriptionModelFormat
import io.morgan.idonthaveyourtime.core.model.TranscriptionRuntime
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GoogleAiEdgeTranscriptionEngineCapabilityTest {

    @Test
    fun `transcription runtime contracts do not expose auto`() {
        assertTrue(TranscriptionRuntime.entries.none { runtime -> runtime.name == "Auto" })
    }

    @Test
    fun `processing config does not expose runtime selection`() {
        val fieldNames = ProcessingConfig::class.java.declaredFields.map { it.name }.toSet()

        assertTrue(fieldNames.contains("transcriptionModelFileName"))
        assertTrue(fieldNames.contains("llmModelFileName"))

        assertTrue("transcriptionRuntime should be removed from ProcessingConfig", !fieldNames.contains("transcriptionRuntime"))
        assertTrue("summarizerRuntime should be removed from ProcessingConfig", !fieldNames.contains("summarizerRuntime"))
    }

    @Test
    fun `probe accepts litertlm transcription model`() = runTest {
        val engine = googleAiEdgeEngine(modelFileName = "gemma-4-E2B-it.litertlm")

        val probe = engine.probe().getOrThrow()

        assertTrue(probe.supported)
        assertEquals(TranscriptionRuntime.GoogleAiEdgeLiteRtLm, probe.selectedRuntime)
        assertEquals(TranscriptionModelFormat.LiteRtLm, probe.modelFormat)
    }

    private fun googleAiEdgeEngine(modelFileName: String): GoogleAiEdgeTranscriptionEngineLocalDataSource =
        GoogleAiEdgeTranscriptionEngineLocalDataSource(
            context = ContextWrapper(null),
            modelLocator = FakeModelLocatorLocalDataSource(modelFileName),
            audioSampleReader = FakeAudioSampleReaderLocalDataSource(),
            defaultDispatcher = StandardTestDispatcher(),
        )

    private class FakeModelLocatorLocalDataSource(
        private val modelFileName: String,
    ) : ModelLocatorLocalDataSource {

        override fun observeAvailability(modelId: ModelId): Flow<ModelAvailability> = emptyFlow()

        override suspend fun getModelPath(modelId: ModelId): Result<String> =
            Result.success("/tmp/$modelFileName")
    }

    private class FakeAudioSampleReaderLocalDataSource : AudioSampleReaderLocalDataSource {
        override suspend fun read16kMonoFloats(
            wavFilePath: String,
            startMs: Long,
            endMs: Long,
        ): Result<FloatArray> = Result.success(floatArrayOf())

        override suspend fun read16kMonoWavBytes(
            wavFilePath: String,
            startMs: Long,
            endMs: Long,
        ): Result<ByteArray> = Result.success(byteArrayOf())
    }
}
