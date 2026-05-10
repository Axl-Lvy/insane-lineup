package fr.axllvy.insane.data

import kotlinx.serialization.Serializable

enum class StageKey { MIRAGE, CLOUD, ALTF4, TECHNOBUS }

enum class DayKey(val id: String, val label: String, val date: String, val full: String) {
    JEU("jeu", "Jeu", "14 mai", "Jeudi 14 mai"),
    VEN("ven", "Ven", "15 mai", "Vendredi 15 mai"),
    SAM("sam", "Sam", "16 mai", "Samedi 16 mai");

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
