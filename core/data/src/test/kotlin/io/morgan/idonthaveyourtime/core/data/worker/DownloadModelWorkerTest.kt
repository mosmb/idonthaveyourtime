package io.morgan.idonthaveyourtime.core.data.worker

import com.google.common.truth.Truth.assertThat
import io.morgan.idonthaveyourtime.core.model.ModelId
import org.junit.Test

class DownloadModelWorkerTest {

    @Test
    fun `resolve supported download model id accepts transcription`() {
        val result = resolveSupportedDownloadModelId("Transcription")

        assertThat(result.getOrThrow()).isEqualTo(ModelId.Transcription)
    }

    @Test
    fun `resolve supported download model id rejects legacy whisper work`() {
        val result = resolveSupportedDownloadModelId("Whisper")

        assertThat(result.isFailure).isTrue()
        assertThat(result.exceptionOrNull()?.message).contains("Unsupported model id")
    }
}
