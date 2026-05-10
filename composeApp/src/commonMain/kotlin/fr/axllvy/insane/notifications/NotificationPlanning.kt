package fr.axllvy.insane.notifications

import fr.axllvy.insane.data.DayKey
import fr.axllvy.insane.data.Lineup
import fr.axllvy.insane.data.SetEntry
import fr.axllvy.insane.data.StageKey
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.Month
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant

const val NOTIFICATION_LEAD_MS: Long = 10 * 60 * 1000L

private val FESTIVAL_TZ = TimeZone.of("Europe/Paris")
private const val FESTIVAL_YEAR = 2026

private val dayDates = mapOf(
    DayKey.JEU to LocalDate(FESTIVAL_YEAR, Month.MAY, 14),
    DayKey.VEN to LocalDate(FESTIVAL_YEAR, Month.MAY, 15),
    DayKey.SAM to LocalDate(FESTIVAL_YEAR, Month.MAY, 16),
)

fun stageLabel(stage: StageKey): String = when (stage) {
    StageKey.MIRAGE -> "Mirage"
    StageKey.CLOUD -> "Cloud"
    StageKey.ALTF4 -> "AltF4"
    StageKey.TECHNOBUS -> "Technobus"
}

fun computeScheduledNotifications(
    lineup: Lineup,
    favKeys: Set<String>,
    nowMs: Long,
    formatBody: (StageKey) -> String,
    leadTimeMs: Long = NOTIFICATION_LEAD_MS,
): List<ScheduledNotification> = buildList {
    for (key in favKeys) {
        val parts = key.split("|")
        if (parts.size != 4) continue
        val day = DayKey.fromId(parts[0]) ?: continue
        val stage = runCatching { StageKey.valueOf(parts[1]) }.getOrNull() ?: continue
        val start = parts[2]
        val artist = parts[3]
        val set = lineup[day]?.get(stage)?.firstOrNull { it.s == start && it.a == artist } ?: continue
        val startMs = setStartEpochMs(day, set) ?: continue
        val fireAt = startMs - leadTimeMs
        if (fireAt <= nowMs) continue
        add(
            ScheduledNotification(
                id = notificationIdForFav(key),
                title = artist,
                body = formatBody(stage),
                fireAtEpochMs = fireAt,
            ),
        )
    }
}

fun notificationIdForFav(favKey: String): String = "fav|$favKey"

private fun setStartEpochMs(day: DayKey, set: SetEntry): Long? = runCatching {
    val date = dayDates[day] ?: return@runCatching null
    val parts = set.s.split(":")
    val h = parts[0].toInt()
    val m = parts[1].toInt()
    // Times before 12:00 belong to the morning of the next day (post-midnight).
    val effectiveDate = if (h < 12) date.plus(DatePeriod(days = 1)) else date
    val ldt = LocalDateTime(
        year = effectiveDate.year,
        month = effectiveDate.month,
        dayOfMonth = effectiveDate.dayOfMonth,
        hour = h,
        minute = m,
    )
    ldt.toInstant(FESTIVAL_TZ).toEpochMilliseconds()
}.getOrNull()
