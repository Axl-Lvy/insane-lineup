package fr.axllvy.insane.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SearchIndexTest {

    private val lineup: Lineup =
        mapOf(
            DayKey.JEU to
                mapOf(
                    StageKey.MIRAGE to
                        listOf(
                            SetEntry("16:00", "17:30", "Kichta"),
                            SetEntry("20:30", "22:00", "Vortek's b2b Byorn"),
                        ),
                    StageKey.CLOUD to listOf(SetEntry("18:00", "19:00", "Söme Ärtist")),
                ),
            DayKey.VEN to mapOf(StageKey.ALTF4 to listOf(SetEntry("01:00", "02:00", "Kichta"))),
        )

    @Test
    fun normalize_stripsAccentsAndPunctuation() {
        assertEquals("vorteksb2bbyorn", normalize("Vortek's b2b Byorn"))
        assertEquals("someartist", normalize("Söme Ärtist!"))
        assertEquals("", normalize("  -- "))
    }

    @Test
    fun query_emptyReturnsEmpty() {
        val index = SearchIndex.build(lineup)
        assertEquals(emptyList(), index.query(""))
        assertEquals(emptyList(), index.query("   "))
    }

    @Test
    fun query_caseInsensitive() {
        val index = SearchIndex.build(lineup)
        val hits = index.query("KICHTA")
        assertEquals(2, hits.size)
        assertTrue(hits.all { it.set.a == "Kichta" })
    }

    @Test
    fun query_ignoresAccents() {
        val index = SearchIndex.build(lineup)
        val hits = index.query("some artist")
        assertEquals(1, hits.size)
        assertEquals("Söme Ärtist", hits.single().set.a)
    }

    @Test
    fun query_matchesIndividualArtistInB2bSet() {
        val index = SearchIndex.build(lineup)
        val byorn = index.query("byorn")
        assertEquals(1, byorn.size)
        assertEquals("Vortek's b2b Byorn", byorn.single().set.a)
    }

    @Test
    fun query_matchesPhraseAcrossB2bSeparator() {
        val index = SearchIndex.build(lineup)
        val hits = index.query("vorteks b2b byorn")
        assertEquals(1, hits.size)
    }

    @Test
    fun query_dedupsAcrossArtistTokensInSameSet() {
        // Vortek's b2b Byorn — the substring "or" hits both halves and the full string;
        // SearchIndex must emit a single SetMatch per unique (day,stage,start,artist) key.
        val index = SearchIndex.build(lineup)
        val hits = index.query("or")
        val vortekHits = hits.filter { it.set.a == "Vortek's b2b Byorn" }
        assertEquals(1, vortekHits.size)
    }

    @Test
    fun query_sortedByDayThenTime() {
        val index = SearchIndex.build(lineup)
        val hits = index.query("kichta")
        // jeu 16:00 first, ven 01:00 second (01:00 normalised to post-midnight after jeu).
        assertEquals(DayKey.JEU, hits[0].day)
        assertEquals("16:00", hits[0].set.s)
        assertEquals(DayKey.VEN, hits[1].day)
        assertEquals("01:00", hits[1].set.s)
    }

    @Test
    fun setMatchKey_uniquePerSlot() {
        val a = SetMatch(DayKey.JEU, StageKey.MIRAGE, SetEntry("16:00", "17:30", "Kichta"))
        val b = SetMatch(DayKey.JEU, StageKey.MIRAGE, SetEntry("17:30", "19:00", "Kichta"))
        val c = SetMatch(DayKey.VEN, StageKey.MIRAGE, SetEntry("16:00", "17:30", "Kichta"))
        assertEquals(3, setOf(a.key(), b.key(), c.key()).size)
    }
}
