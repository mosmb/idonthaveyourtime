package io.morgan.idonthaveyourtime.core.whisper

import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

internal object WavPcm16Reader {
    fun readPcm16Mono16kHzAsFloats(file: File): FloatArray {
        require(file.exists()) { "WAV file does not exist: ${file.absolutePath}" }

        val bytes = file.readBytes()
        require(bytes.size >= 12) { "Invalid WAV file (too small): ${file.absolutePath}" }

        val riff = String(bytes, 0, 4)
        val wave = String(bytes, 8, 4)
        require(riff == "RIFF" && wave == "WAVE") { "Invalid WAV header (expected RIFF/WAVE): ${file.absolutePath}" }

        var fmtFound = false
        var dataFound = false

        var audioFormat = 0
        var channels = 0
        var sampleRate = 0
        var bitsPerSample = 0

        var dataOffset = 0
        var dataSize = 0

        var cursor = 12
        while (cursor + 8 <= bytes.size) {
            val chunkId = String(bytes, cursor, 4)
            val chunkSize = readIntLE(bytes, cursor + 4)
            val chunkDataStart = cursor + 8
            val chunkDataEnd = chunkDataStart + chunkSize
            require(chunkDataEnd <= bytes.size) { "Invalid WAV file (chunk exceeds file size): ${file.absolutePath}" }

            when (chunkId) {
                "fmt " -> {
                    require(chunkSize >= 16) { "Invalid WAV fmt chunk: ${file.absolutePath}" }
                    audioFormat = readShortLE(bytes, chunkDataStart).toInt() and 0xFFFF
                    channels = readShortLE(bytes, chunkDataStart + 2).toInt() and 0xFFFF
                    sampleRate = readIntLE(bytes, chunkDataStart + 4)
                    bitsPerSample = readShortLE(bytes, chunkDataStart + 14).toInt() and 0xFFFF
                    fmtFound = true
                }

                "data" -> {
                    dataOffset = chunkDataStart
                    dataSize = chunkSize
                    dataFound = true
                }
            }

            cursor = chunkDataEnd + (chunkSize % 2)
        }

        require(fmtFound && dataFound) { "Invalid WAV file (missing fmt or data chunk): ${file.absolutePath}" }
        require(audioFormat == 1) { "Unsupported WAV format. Expected PCM but found format=$audioFormat" }
        require(channels == 1) { "Unsupported WAV channels. Expected mono but found channels=$channels" }
        require(sampleRate == 16_000) { "Unsupported WAV sample rate. Expected 16000 but found sampleRate=$sampleRate" }
        require(bitsPerSample == 16) { "Unsupported WAV bits/sample. Expected 16 but found bitsPerSample=$bitsPerSample" }
        require(dataSize > 0 && dataSize % 2 == 0) { "Invalid WAV data chunk size: $dataSize" }

        val sampleCount = dataSize / 2
        val result = FloatArray(sampleCount)
        var outIndex = 0
        var inIndex = dataOffset
        while (outIndex < sampleCount) {
            val sample = readShortLE(bytes, inIndex)
            result[outIndex] = (sample / 32768.0f).coerceIn(-1f, 1f)
            outIndex += 1
            inIndex += 2
        }
        return result
    }

    private fun readIntLE(bytes: ByteArray, offset: Int): Int =
        ByteBuffer.wrap(bytes, offset, Int.SIZE_BYTES)
            .order(ByteOrder.LITTLE_ENDIAN)
            .int

    private fun readShortLE(bytes: ByteArray, offset: Int): Short =
        ByteBuffer.wrap(bytes, offset, Short.SIZE_BYTES)
            .order(ByteOrder.LITTLE_ENDIAN)
            .short
}

