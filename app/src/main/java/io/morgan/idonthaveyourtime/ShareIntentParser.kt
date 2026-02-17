package io.morgan.idonthaveyourtime

import android.content.ContentResolver
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.content.IntentCompat
import io.morgan.idonthaveyourtime.core.model.SharedAudioInput
import timber.log.Timber

internal fun Intent?.toSharedAudioInputs(contentResolver: ContentResolver): List<SharedAudioInput> {
    if (this == null) {
        return emptyList()
    }

    Timber.tag(TAG).d("Share intent received action=%s type=%s", action, type)

    return when (action) {
        Intent.ACTION_SEND -> {
            val uri = IntentCompat.getParcelableExtra(this, Intent.EXTRA_STREAM, Uri::class.java)
            Timber.tag(TAG).d("Share intent single uri=%s", uri)
            uri?.let { listOf(it.toSharedAudioInput(contentResolver, type)) } ?: emptyList()
        }

        Intent.ACTION_SEND_MULTIPLE -> {
            val uris = IntentCompat.getParcelableArrayListExtra(this, Intent.EXTRA_STREAM, Uri::class.java).orEmpty()
            Timber.tag(TAG).d("Share intent multiple uris=%d", uris.size)
            uris.map { uri -> uri.toSharedAudioInput(contentResolver, type) }
        }

        else -> emptyList()
    }
}

private fun Uri.toSharedAudioInput(contentResolver: ContentResolver, fallbackMimeType: String?): SharedAudioInput {
    val (displayName, sizeBytes) = runCatching {
        contentResolver.query(this, null, null, null, null)?.use { cursor ->
            if (!cursor.moveToFirst()) {
                return@use Pair(null, null)
            }

            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
            val name = if (nameIndex >= 0) cursor.getString(nameIndex) else null
            val size = if (sizeIndex >= 0) cursor.getLong(sizeIndex) else null
            Pair(name, size)
        } ?: Pair(null, null)
    }.getOrDefault(Pair(null, null))

    val resolvedMimeType = runCatching { contentResolver.getType(this) }.getOrNull() ?: fallbackMimeType
    Timber.tag(TAG).d(
        "Share intent parsed uri=%s mime=%s displayName=%s sizeBytes=%s",
        this,
        resolvedMimeType,
        displayName,
        sizeBytes,
    )

    return SharedAudioInput(
        uriToken = toString(),
        mimeType = resolvedMimeType,
        displayName = displayName,
        sizeBytes = sizeBytes,
    )
}

private const val TAG = "ShareIntentParser"
