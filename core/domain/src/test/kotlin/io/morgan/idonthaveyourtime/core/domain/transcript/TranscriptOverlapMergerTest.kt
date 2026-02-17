package io.morgan.idonthaveyourtime.core.domain.transcript

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class TranscriptOverlapMergerTest {

    @Test
    fun `dropBestOverlapPrefix drops overlapping prefix tokens`() {
        val prev = "We will meet on Monday morning"
        val next = "on Monday morning at nine sharp"

        val merged = TranscriptOverlapMerger.dropBestOverlapPrefix(prev, next)

        assertThat(merged).isEqualTo("at nine sharp")
    }

    @Test
    fun `dropBestOverlapPrefix keeps text when overlap is too small`() {
        val prev = "Hello world"
        val next = "world again"

        val merged = TranscriptOverlapMerger.dropBestOverlapPrefix(prev, next)

        assertThat(merged).isEqualTo("world again")
    }

    @Test
    fun `dropBestOverlapPrefix is case-insensitive`() {
        val prev = "This is an overlapping phrase"
        val next = "an OVERLAPPING phrase continues here"

        val merged = TranscriptOverlapMerger.dropBestOverlapPrefix(prev, next)

        assertThat(merged).isEqualTo("continues here")
    }
}

