package io.morgan.idonthaveyourtime

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ShareIntentParserInstrumentedTest {

    private val contentResolver = ApplicationProvider.getApplicationContext<Context>().contentResolver

    @Test
    fun actionSend_parsesSingleStreamUri() {
        val uri = Uri.parse("content://example.invalid/audio/1")
        val intent = Intent(Intent.ACTION_SEND)
            .setType("audio/ogg")
            .putExtra(Intent.EXTRA_STREAM, uri)

        val inputs = intent.toSharedAudioInputs(contentResolver)

        assertEquals(1, inputs.size)
        assertEquals(uri.toString(), inputs.first().uriToken)
        assertEquals("audio/ogg", inputs.first().mimeType)
    }

    @Test
    fun actionSendMultiple_parsesAllStreamUris() {
        val uri1 = Uri.parse("content://example.invalid/audio/1")
        val uri2 = Uri.parse("content://example.invalid/audio/2")
        val intent = Intent(Intent.ACTION_SEND_MULTIPLE)
            .setType("audio/*")
            .putParcelableArrayListExtra(Intent.EXTRA_STREAM, arrayListOf(uri1, uri2))

        val inputs = intent.toSharedAudioInputs(contentResolver)

        assertEquals(2, inputs.size)
        assertTrue(inputs.map { it.uriToken }.containsAll(listOf(uri1.toString(), uri2.toString())))
        assertTrue(inputs.all { it.mimeType == "audio/*" })
    }
}
