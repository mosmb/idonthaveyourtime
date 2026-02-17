package io.morgan.idonthaveyourtime

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dagger.hilt.android.AndroidEntryPoint
import io.morgan.idonthaveyourtime.core.designsystem.IdonthaveyourtimeTheme
import io.morgan.idonthaveyourtime.core.model.SharedAudioInput
import io.morgan.idonthaveyourtime.feature.summarize.impl.SummarizeRoute
import timber.log.Timber

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private var pendingSharedAudio: List<SharedAudioInput> by mutableStateOf(emptyList())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        pendingSharedAudio = intent.toSharedAudioInputs(contentResolver)
        Timber.tag(TAG).i("MainActivity onCreate sharedAudios=%d", pendingSharedAudio.size)
        pendingSharedAudio.forEachIndexed { index, audio ->
            Timber.tag(TAG).d(
                "MainActivity incoming[%d] name=%s mime=%s sizeBytes=%s uri=%s",
                index,
                audio.displayName,
                audio.mimeType,
                audio.sizeBytes,
                audio.uriToken,
            )
        }

        enableEdgeToEdge()
        setContent {
            IdonthaveyourtimeTheme {
                SummarizeRoute(
                    incomingSharedAudio = pendingSharedAudio,
                    onIncomingHandled = { pendingSharedAudio = emptyList() },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingSharedAudio = intent.toSharedAudioInputs(contentResolver)
        Timber.tag(TAG).i("MainActivity onNewIntent sharedAudios=%d", pendingSharedAudio.size)
    }

    private companion object {
        const val TAG = "MainActivity"
    }
}
