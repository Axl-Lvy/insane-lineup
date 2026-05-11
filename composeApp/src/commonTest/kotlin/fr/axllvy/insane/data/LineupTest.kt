package fr.axllvy.insane.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LineupTest {

    @Test
    fun timeToMin_noonIsZero() {
        assertEquals(0, timeToMin("12:00"))
    }

    @Test
    fun timeToMin_afterMidnightWrapsForward() {
        // 02:00 is past midnight, festival day starts at noon -> 14h offset.
        assertEquals(14 * 60, timeToMin("02:00"))
    }

    @Test
    fun timeToMin_lateAfternoon() {
        assertEquals(8 * 60 + 30, timeToMin("20:30"))
    }

    @Test
    fun setsOverlap_disjointReturnsFalse() {
        val a = SetEntry(s = "16:00", e = "17:30", a = "X")
        val b = SetEntry(s = "17:30", e = "19:00", a = "Y")
        assertFalse(setsOverlap(a, b))
        assertFalse(setsOverlap(b, a))
    }

    @Test
    fun setsOverlap_partialOverlapReturnsTrue() {
        val a = SetEntry(s = "16:00", e = "17:30", a = "X")
        val b = SetEntry(s = "17:00", e = "18:00", a = "Y")
        assertTrue(setsOverlap(a, b))
        assertTrue(setsOverlap(b, a))
    }

    @Test
    fun setsOverlap_acrossMidnightReturnsTrue() {
        val a = SetEntry(s = "23:30", e = "00:30", a = "X")
        val b = SetEntry(s = "00:00", e = "01:00", a = "Y")
        assertTrue(setsOverlap(a, b))
    }

    @Test
    fun parseLineupJson_basicShape() {
        val json =
            """
            {
              "jeu": {
                "MIRAGE": [{"s":"16:00","e":"17:30","a":"Kichta"}],
                "CLOUD":  [{"s":"18:00","e":"19:30","a":"Foo"}]
              },
              "sam": {
                "ALTF4": [{"s":"22:00","e":"23:30","a":"Bar"}]
              }
            }
            """
                .trimIndent()
        val lineup = parseLineupJson(json)
        assertNotNull(lineup)
        assertEquals(2, lineup.size)
        assertEquals("Kichta", lineup[DayKey.JEU]?.get(StageKey.MIRAGE)?.first()?.a)
        assertEquals("Bar", lineup[DayKey.SAM]?.get(StageKey.ALTF4)?.first()?.a)
    }

    @Test
    fun parseLineupJson_unknownDayAndStageDropped() {
        val json =
            """
            {
              "xxx": { "MIRAGE": [{"s":"16:00","e":"17:30","a":"Ghost"}] },
              "jeu": {
                "NOPE":   [{"s":"16:00","e":"17:30","a":"Drop"}],
                "MIRAGE": [{"s":"16:00","e":"17:30","a":"Keep"}]
              }
            }
            """
                .trimIndent()
        val lineup = parseLineupJson(json)
        assertNotNull(lineup)
        assertNull(lineup[DayKey.JEU]?.keys?.firstOrNull { it.name == "NOPE" })
        assertEquals(1, lineup.size)
        assertEquals("Keep", lineup[DayKey.JEU]?.get(StageKey.MIRAGE)?.single()?.a)
    }

    @Test
    fun parseLineupJson_invalidReturnsNull() {
        assertNull(parseLineupJson("not json"))
    }

    @Test
    fun serializeLineup_roundTrip() {
        val original =
            mapOf(
                DayKey.JEU to
                    mapOf(
                        StageKey.MIRAGE to
                            listOf(
                                SetEntry("16:00", "17:30", "Kichta"),
                                SetEntry("17:30", "19:00", "2hot2play"),
                            )
                    ),
                DayKey.VEN to
                    mapOf(StageKey.TECHNOBUS to listOf(SetEntry("23:00", "00:30", "Holy Priest"))),
            )
        val text = serializeLineup(original)
        val parsed = parseLineupJson(text)
        assertEquals(original, parsed)
    }

    @Test
    fun parseSupabaseLineupResponse_extractsInnerData() {
        val body =
            """
            [{"id":1,"data":{
              "jeu":{"MIRAGE":[{"s":"16:00","e":"17:30","a":"Kichta"}]}
            }}]
            """
                .trimIndent()
        val lineup = parseSupabaseLineupResponse(body)
        assertNotNull(lineup)
        assertEquals("Kichta", lineup[DayKey.JEU]?.get(StageKey.MIRAGE)?.first()?.a)
    }

    @Test
    fun parseSupabaseLineupResponse_emptyArrayReturnsNull() {
        assertNull(parseSupabaseLineupResponse("[]"))
    }

    @Test
    fun parseSupabaseLineupResponse_missingDataKeyReturnsNull() {
        assertNull(parseSupabaseLineupResponse("""[{"id":1}]"""))
    }

    @Test
    fun dayKey_fromIdRoundTrip() {
        assertEquals(DayKey.JEU, DayKey.fromId("jeu"))
        assertEquals(DayKey.VEN, DayKey.fromId("ven"))
        assertEquals(DayKey.SAM, DayKey.fromId("sam"))
        assertNull(DayKey.fromId("dim"))
    }
}
