package io.morgan.idonthaveyourtime.core.data.datasource.model.impl

import com.google.common.truth.Truth.assertThat
import io.morgan.idonthaveyourtime.core.data.datasource.model.ModelStoreLocalDataSource
import io.morgan.idonthaveyourtime.core.data.datasource.settings.ProcessingConfigLocalDataSource
import io.morgan.idonthaveyourtime.core.model.ModelAvailability
import io.morgan.idonthaveyourtime.core.model.ModelId
import io.morgan.idonthaveyourtime.core.model.ProcessingConfig
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test

class ModelManagerModelLocatorLocalDataSourceTest {

    @Test
    fun `legacy whisper bin file does not count as ready transcription model`() = runTest {
        val modelsDirectory = Files.createTempDirectory("legacy-whisper-models").toFile()
        val legacyFile = File(modelsDirectory, "ggml-base-q5_1.bin").apply {
            writeText("legacy")
        }
        val locator = ModelManagerModelLocatorLocalDataSource(
            processingConfigDataSource = FakeProcessingConfigLocalDataSource(
                ProcessingConfig(transcriptionModelFileName = legacyFile.name),
            ),
            modelStoreDataSource = FakeModelStoreLocalDataSource(
                modelsDirectory = modelsDirectory,
                userFiles = mapOf(legacyFile.name to legacyFile),
            ),
        )

        val availability = locator.observeAvailability(ModelId.Transcription).first()
        val result = locator.getModelPath(ModelId.Transcription)

        assertThat(availability).isEqualTo(ModelAvailability.Missing)
        assertThat(result.isFailure).isTrue()
        assertThat(result.exceptionOrNull()?.message)
            .contains(ProcessingConfig().transcriptionModelFileName)
    }

    @Test
    fun `legacy whisper bin config resolves to default litertlm model when present`() = runTest {
        val modelsDirectory = Files.createTempDirectory("normalized-transcription-models").toFile()
        val defaultFile = File(modelsDirectory, ProcessingConfig().transcriptionModelFileName).apply {
            writeText("litertlm")
        }
        val locator = ModelManagerModelLocatorLocalDataSource(
            processingConfigDataSource = FakeProcessingConfigLocalDataSource(
                ProcessingConfig(transcriptionModelFileName = "ggml-base-q5_1.bin"),
            ),
            modelStoreDataSource = FakeModelStoreLocalDataSource(
                modelsDirectory = modelsDirectory,
                userFiles = mapOf(defaultFile.name to defaultFile),
            ),
        )

        val availability = locator.observeAvailability(ModelId.Transcription).first()
        val result = locator.getModelPath(ModelId.Transcription)

        assertThat(availability).isEqualTo(ModelAvailability.Ready)
        assertThat(result.getOrThrow()).isEqualTo(defaultFile.absolutePath)
    }

    private class FakeProcessingConfigLocalDataSource(
        config: ProcessingConfig,
    ) : ProcessingConfigLocalDataSource {
        private val state = MutableStateFlow(config)

        override fun observeConfig(): Flow<ProcessingConfig> = state

        override suspend fun getConfig(): ProcessingConfig = state.value

        override suspend fun setConfig(config: ProcessingConfig): Result<Unit> = runCatching {
            state.value = config
        }
    }

    private class FakeModelStoreLocalDataSource(
        private val modelsDirectory: File,
        private val userFiles: Map<String, File>,
    ) : ModelStoreLocalDataSource {
        private val changes = MutableStateFlow(Unit)

        override fun observeModelsDirectoryChanges(): Flow<Unit> = changes

        override fun findModelFileInUserStorage(fileNames: List<String>): File? =
            fileNames.firstNotNullOfOrNull { fileName -> userFiles[fileName] }

        override fun hasBundledAssetModel(fileNames: List<String>): Boolean = false

        override fun extractBundledAssetModel(fileNames: List<String>): File? = null

        override fun modelsDirectory(): File = modelsDirectory
    }
}
