package io.morgan.idonthaveyourtime.core.whisper

import android.os.Build
import java.io.File
import timber.log.Timber

internal object WhisperLibraryLoader {
    private const val TAG = "WhisperLibraryLoader"

    private const val LIB_DEFAULT = "whisper"
    private const val LIB_VFPV4 = "whisper_vfpv4"
    private const val LIB_V8FP16 = "whisper_v8fp16_va"

    private val loadResult: Result<Unit> = runCatching {
        val primaryAbi = Build.SUPPORTED_ABIS.firstOrNull().orEmpty()
        Timber.tag(TAG).d("Primary ABI: %s", primaryAbi)

        var loadVfpv4 = false
        var loadV8fp16 = false

        val cpuInfo = cpuInfo()?.lowercase()
        if (primaryAbi == "armeabi-v7a") {
            if (cpuInfo?.contains("vfpv4") == true) {
                Timber.tag(TAG).d("CPU supports vfpv4")
                loadVfpv4 = true
            }
        } else if (primaryAbi == "arm64-v8a") {
            if (cpuInfo?.contains("fphp") == true || cpuInfo?.contains("asimdhp") == true) {
                Timber.tag(TAG).d("CPU supports fp16 arithmetic")
                loadV8fp16 = true
            }
        }

        val lib = when {
            loadVfpv4 -> LIB_VFPV4
            loadV8fp16 -> LIB_V8FP16
            else -> LIB_DEFAULT
        }

        Timber.tag(TAG).d("Loading native library %s", lib)
        System.loadLibrary(lib)
        Timber.tag(TAG).i("Loaded native library %s", lib)
    }.onFailure { throwable ->
        Timber.tag(TAG).e(throwable, "Failed to load whisper native libraries")
    }

    val isLoaded: Boolean get() = loadResult.isSuccess
    val loadError: Throwable? get() = loadResult.exceptionOrNull()

    fun ensureLoaded() {
        if (!isLoaded) {
            throw IllegalStateException("Failed to load whisper native libraries: ${loadError?.message ?: "unknown error"}")
        }
    }

    private fun cpuInfo(): String? = try {
        File("/proc/cpuinfo").inputStream().bufferedReader().use { it.readText() }
    } catch (_: Exception) {
        null
    }
}
