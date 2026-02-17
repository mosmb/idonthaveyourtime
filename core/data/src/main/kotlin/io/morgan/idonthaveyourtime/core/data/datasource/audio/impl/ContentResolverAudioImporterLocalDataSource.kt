package io.morgan.idonthaveyourtime.core.data.datasource.audio.impl

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import dagger.hilt.android.qualifiers.ApplicationContext
import io.morgan.idonthaveyourtime.core.common.ProcessingException
import io.morgan.idonthaveyourtime.core.data.datasource.audio.AudioImporterLocalDataSource
import io.morgan.idonthaveyourtime.core.data.di.IoDispatcher
import io.morgan.idonthaveyourtime.core.model.ImportedAudio
import io.morgan.idonthaveyourtime.core.model.ProcessingLimits
import io.morgan.idonthaveyourtime.core.model.SharedAudioInput
import java.io.File
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import timber.log.Timber

internal class ContentResolverAudioImporterLocalDataSource @Inject constructor(
    @ApplicationContext private val context: Context,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : AudioImporterLocalDataSource {

    private val resolver: ContentResolver = context.contentResolver

    override suspend fun importFromUri(sharedAudio: SharedAudioInput, sessionId: String): Result<ImportedAudio> =
        withContext(ioDispatcher) {
            Timber.tag(TAG).i(
                "Import start sessionId=%s uri=%s mime=%s displayName=%s sizeBytes=%s",
                sessionId,
                sharedAudio.uriToken,
                sharedAudio.mimeType,
                sharedAudio.displayName,
                sharedAudio.sizeBytes,
            )

            runCatching {
                val uri = Uri.parse(sharedAudio.uriToken)

                val declaredSize = sharedAudio.sizeBytes
                    ?: querySizeBytes(uri)
                    ?: throw ProcessingException("INVALID_SIZE", "Unable to determine file size")

                Timber.tag(TAG).d(
                    "Import metadata sessionId=%s declaredSizeBytes=%d sourceName=%s",
                    sessionId,
                    declaredSize,
                    sharedAudio.displayName,
                )

                if (declaredSize <= 0) {
                    throw ProcessingException("EMPTY_FILE", "Shared audio file is empty")
                }

                if (declaredSize > ProcessingLimits.MAX_IMPORT_SIZE_BYTES) {
                    throw ProcessingException(
                        "FILE_TOO_LARGE",
                        "Audio file exceeds max supported size (${ProcessingLimits.MAX_IMPORT_SIZE_BYTES} bytes)",
                    )
                }

                val sessionDirectory = File(context.cacheDir, "sessions/$sessionId")
                if (!sessionDirectory.exists()) {
                    sessionDirectory.mkdirs()
                }

                val sourceName = sharedAudio.displayName ?: queryDisplayName(uri) ?: "shared_audio"
                val extension = inferExtension(sourceName, sharedAudio.mimeType)
                val importedFile = File(sessionDirectory, "import.$extension")

                Timber.tag(TAG).i(
                    "Import copying sessionId=%s target=%s",
                    sessionId,
                    importedFile.absolutePath,
                )

                resolver.openInputStream(uri)?.use { inputStream ->
                    importedFile.outputStream().use { outputStream ->
                        inputStream.copyTo(outputStream)
                    }
                } ?: throw ProcessingException("UNREADABLE_URI", "Unable to open shared audio content")

                if (!importedFile.exists() || importedFile.length() <= 0L) {
                    throw ProcessingException("EMPTY_FILE", "Imported audio file is empty")
                }

                Timber.tag(TAG).i(
                    "Import done sessionId=%s file=%s sizeBytes=%d",
                    sessionId,
                    importedFile.absolutePath,
                    importedFile.length(),
                )

                ImportedAudio(
                    sessionId = sessionId,
                    cachedFilePath = importedFile.absolutePath,
                    sourceName = sourceName,
                    mimeType = sharedAudio.mimeType,
                    sizeBytes = importedFile.length(),
                )
            }.onFailure { throwable ->
                Timber.tag(TAG).e(throwable, "Import failed sessionId=%s uri=%s", sessionId, sharedAudio.uriToken)
            }
        }

    private fun queryDisplayName(uri: Uri): String? =
        resolver.query(uri, null, null, null, null)?.use { cursor ->
            if (!cursor.moveToFirst()) {
                return null
            }

            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (nameIndex < 0) {
                null
            } else {
                cursor.getString(nameIndex)
            }
        }

    private fun querySizeBytes(uri: Uri): Long? =
        resolver.query(uri, null, null, null, null)?.use { cursor ->
            if (!cursor.moveToFirst()) {
                return null
            }

            val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
            if (sizeIndex < 0) {
                null
            } else {
                cursor.getLong(sizeIndex)
            }
        }

    private fun inferExtension(fileName: String, mimeType: String?): String {
        val lowerMime = mimeType?.lowercase(Locale.US)

        if (lowerMime?.contains("ogg") == true) return "ogg"
        if (lowerMime?.contains("opus") == true) return "opus"
        if (lowerMime?.contains("wav") == true) return "wav"
        if (lowerMime?.contains("mpeg") == true || lowerMime == "audio/mp3") return "mp3"

        val explicitExt = fileName.substringAfterLast('.', missingDelimiterValue = "")
        return explicitExt.ifBlank { "audio" }.lowercase(Locale.US)
    }

    private companion object {
        const val TAG = "AudioImporter"
    }
}
