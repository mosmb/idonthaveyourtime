package io.morgan.idonthaveyourtime.core.database

import org.junit.Assert.assertEquals
import org.junit.Test
import java.nio.file.Paths

class AppDatabaseContractTest {

    @Test
    fun `database version stays compatible with shipped schema`() {
        val databaseSource = Paths.get("src/main/kotlin/io/morgan/idonthaveyourtime/core/database/AppDatabase.kt")
            .toFile()
            .readText()
        val version = Regex("""version\s*=\s*(\d+)""")
            .find(databaseSource)
            ?.groupValues
            ?.get(1)
            ?.toInt()

        assertEquals(3, version)
    }
}
