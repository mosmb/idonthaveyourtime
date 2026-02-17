package io.morgan.idonthaveyourtime.core.data.datasource.model

import java.io.File
import kotlinx.coroutines.flow.Flow

/**
 * Provides access to the app's local model storage (user files and bundled assets).
 *
 * This is a **local** data source: implementations must not perform network I/O.
 */
interface ModelStoreLocalDataSource {
    fun observeModelsDirectoryChanges(): Flow<Unit>
    fun findModelFileInUserStorage(fileNames: List<String>): File?
    fun hasBundledAssetModel(fileNames: List<String>): Boolean
    fun extractBundledAssetModel(fileNames: List<String>): File?
    fun modelsDirectory(): File
}

