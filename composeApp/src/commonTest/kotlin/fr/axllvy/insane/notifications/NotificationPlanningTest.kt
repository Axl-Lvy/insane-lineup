package fr.axllvy.insane.notifications

import fr.axllvy.insane.data.DayKey
import fr.axllvy.insane.data.Lineup
import fr.axllvy.insane.data.SetEntry
import fr.axllvy.insane.data.StageKey
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.Month
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant

class NotificationPlanningTest {

    private val festivalTz = TimeZone.of("Europe/Paris")

    private val lineup: Lineup =
        mapOf(
            DayKey.JEU to
                mapOf(
                    StageKey.MIRAGE to
                        listOf(
                            SetEntry("16:00", "17:30", "Kichta"),
                            SetEntry("23:00", "00:30", "Vieze Asbak"),
                        )
                ),
            DayKey.VEN to mapOf(StageKey.CLOUD to listOf(SetEntry("01:30", "03:00", "Late Set"))),
        )

    private fun favKey(day: DayKey, stage: StageKey, set: SetEntry) =
        "${day.id}|${stage.name}|${set.s}|${set.a}"

    private fun epoch(year: Int, month: Month, day: Int, hour: Int, minute: Int): Long =
        LocalDateTime(year, month, day, hour, minute).toInstant(festivalTz).toEpochMilliseconds()

    @Test
    fun schedules_futureFavoriteWithLeadTime() {
        val key = favKey(DayKey.JEU, StageKey.MIRAGE, lineup[DayKey.JEU]!![StageKey.MIRAGE]!![0])
        val now = epoch(2026, Month.MAY, 14, 12, 0) // festival jeu midday

        val out =
            computeScheduledNotifications(
                lineup = lineup,
                favKeys = setOf(key),
                nowMs = now,
                formatBody = { "stage:${it.name}" },
            )

        assertEquals(1, out.size)
        val n = out.single()
        assertEquals("Kichta", n.title)
        assertEquals("stage:MIRAGE", n.body)
        assertEquals("fav|$key", n.id)
        val expectedFire = epoch(2026, Month.MAY, 14, 16, 0) - NOTIFICATION_LEAD_MS
        assertEquals(expectedFire, n.fireAtEpochMs)
    }

    @Test
    fun skips_setsWhoseLeadTimeHasAlreadyPassed() {
        val key = favKey(DayKey.JEU, StageKey.MIRAGE, lineup[DayKey.JEU]!![StageKey.MIRAGE]!![0])
        // Set starts at 16:00; lead is 10min; "now" past 15:50 means fireAt <= now -> drop.
        val now = epoch(2026, Month.MAY, 14, 15, 55)

        val out =
            computeScheduledNotifications(
                lineup = lineup,
                favKeys = setOf(key),
                nowMs = now,
                formatBody = { "x" },
            )

        assertTrue(out.isEmpty())
    }

    @Test
    fun postMidnightSet_isScheduledOnNextCalendarDay() {
        // Jeu 23:00 (same calendar day) — date stays as jeu (May 14).
        val sameDayKey =
            favKey(DayKey.JEU, StageKey.MIRAGE, lineup[DayKey.JEU]!![StageKey.MIRAGE]!![1])
        // Ven 01:30 (post-midnight) — date rolls to May 16 (the morning after ven).
        val nextDayKey =
            favKey(DayKey.VEN, StageKey.CLOUD, lineup[DayKey.VEN]!![StageKey.CLOUD]!![0])
        val now = epoch(2026, Month.MAY, 14, 12, 0)

        val out =
            computeScheduledNotifications(
                lineup = lineup,
                favKeys = setOf(sameDayKey, nextDayKey),
                nowMs = now,
                formatBody = { "" },
            )

        val byTitle = out.associateBy { it.title }
        val expectedSame = epoch(2026, Month.MAY, 14, 23, 0) - NOTIFICATION_LEAD_MS
        val expectedNext = epoch(2026, Month.MAY, 16, 1, 30) - NOTIFICATION_LEAD_MS
        assertEquals(expectedSame, byTitle.getValue("Vieze Asbak").fireAtEpochMs)
        assertEquals(expectedNext, byTitle.getValue("Late Set").fireAtEpochMs)
    }

    @Test
    fun unknownFavKey_isIgnored() {
        val now = epoch(2026, Month.MAY, 14, 12, 0)
        val out =
            computeScheduledNotifications(
                lineup = lineup,
                favKeys =
                    setOf(
                        "bad|key",
                        "jeu|NOPE|16:00|X",
                        "jeu|MIRAGE|99:99|Nobody",
                        "xxx|MIRAGE|16:00|Kichta",
                    ),
                nowMs = now,
                formatBody = { "" },
            )
        assertTrue(out.isEmpty())
    }

    @Test
    fun customLeadTimeIsRespected() {
        val key = favKey(DayKey.JEU, StageKey.MIRAGE, lineup[DayKey.JEU]!![StageKey.MIRAGE]!![0])
        val now = epoch(2026, Month.MAY, 14, 12, 0)
        val lead = 30 * 60 * 1000L

        val out =
            computeScheduledNotifications(
                lineup = lineup,
                favKeys = setOf(key),
                nowMs = now,
                formatBody = { "" },
                leadTimeMs = lead,
            )

        val expected = epoch(2026, Month.MAY, 14, 16, 0) - lead
        assertEquals(expected, out.single().fireAtEpochMs)
    }

    @Test
    fun stageLabel_mapsAllStages() {
        assertEquals("Mirage", stageLabel(StageKey.MIRAGE))
        assertEquals("Cloud", stageLabel(StageKey.CLOUD))
        assertEquals("AltF4", stageLabel(StageKey.ALTF4))
        assertEquals("Technobus", stageLabel(StageKey.TECHNOBUS))
    }

    @Test
    fun notificationIdForFav_isPrefixed() {
        assertEquals("fav|jeu|MIRAGE|16:00|Kichta", notificationIdForFav("jeu|MIRAGE|16:00|Kichta"))
    }
}
