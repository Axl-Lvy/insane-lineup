package fr.axllvy.insane.data

import kotlinx.serialization.Serializable

enum class StageKey { MIRAGE, CLOUD, ALTF4, TECHNOBUS }

enum class DayKey(val id: String) {
    JEU("jeu"),
    VEN("ven"),
    SAM("sam");

    companion object {
        fun fromId(id: String): DayKey? = entries.firstOrNull { it.id == id }
    }
}

@Serializable
data class SetEntry(val s: String, val e: String, val a: String)

typealias StageMap = Map<StageKey, List<SetEntry>>
typealias Lineup = Map<DayKey, StageMap>

private const val DAY_START_MIN = 12 * 60

fun timeToMin(t: String): Int {
    val parts = t.split(":")
    val h = parts[0].toInt()
    val m = parts[1].toInt()
    var total = h * 60 + m
    if (total < 12 * 60) total += 24 * 60
    return total - DAY_START_MIN
}

fun setsOverlap(a: SetEntry, b: SetEntry): Boolean =
    timeToMin(a.s) < timeToMin(b.e) && timeToMin(b.s) < timeToMin(a.e)
