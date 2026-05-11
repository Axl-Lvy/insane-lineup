package fr.axllvy.insane.data

import kotlin.test.Test
import kotlin.test.assertEquals

class ArtistSplitTest {

    @Test
    fun split_singleArtistPassesThrough() {
        assertEquals(listOf("Kichta"), splitArtists("Kichta"))
    }

    @Test
    fun split_b2bLowercase() {
        assertEquals(listOf("Vortek's", "Byorn"), splitArtists("Vortek's b2b Byorn"))
    }

    @Test
    fun split_b2bCaseInsensitive() {
        assertEquals(listOf("A", "B"), splitArtists("A B2B B"))
        assertEquals(listOf("A", "B"), splitArtists("A b2B B"))
    }

    @Test
    fun split_f2fSeparator() {
        assertEquals(listOf("DJ One", "DJ Two"), splitArtists("DJ One f2f DJ Two"))
    }

    @Test
    fun split_secretB2bWithoutSurroundingSpacesStaysAsOne() {
        // Real data point: "Secret B2B" is a stage name, not two artists.
        assertEquals(listOf("Secret B2B"), splitArtists("Secret B2B"))
    }

    @Test
    fun split_collapsesEmptyTokens() {
        assertEquals(listOf("A", "B"), splitArtists("  A b2b   B  "))
    }
}
