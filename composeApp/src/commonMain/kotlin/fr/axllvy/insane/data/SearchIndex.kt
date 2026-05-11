package fr.axllvy.insane.data

data class SetMatch(
    val day: DayKey,
    val stage: StageKey,
    val set: SetEntry,
) {
    fun key(): String = "${day.id}|${stage.name}|${set.s}|${set.a}"
}

class SearchIndex private constructor(
    private val entries: List<Entry>,
) {
    private data class Entry(val token: String, val match: SetMatch)

    fun query(raw: String): List<SetMatch> {
        val q = normalize(raw)
        if (q.isEmpty()) return emptyList()
        val seen = mutableSetOf<String>()
        val out = mutableListOf<SetMatch>()
        for (e in entries) {
            if (!e.token.contains(q)) continue
            val k = e.match.key()
            if (seen.add(k)) out += e.match
        }
        return out.sortedWith(compareBy({ it.day.ordinal }, { timeToMin(it.set.s) }))
    }

    companion object {
        fun build(lineup: Lineup): SearchIndex {
            val entries = mutableListOf<Entry>()
            for ((day, stages) in lineup) {
                for ((stage, sets) in stages) {
                    for (set in sets) {
                        val match = SetMatch(day, stage, set)
                        // Index each individual artist (b2b/f2f split).
                        for (artist in splitArtists(set.a)) {
                            entries += Entry(normalize(artist), match)
                        }
                        // Also index the full string for phrase queries like "vortek's b2b byorn".
                        entries += Entry(normalize(set.a), match)
                    }
                }
            }
            return SearchIndex(entries)
        }
    }
}

internal fun normalize(s: String): String {
    val sb = StringBuilder(s.length)
    for (c in s) {
        val lower = c.lowercaseChar()
        val stripped = ACCENT_MAP[lower] ?: lower
        if (stripped.isLetterOrDigit()) sb.append(stripped)
    }
    return sb.toString()
}

private val ACCENT_MAP: Map<Char, Char> = buildMap {
    "àáâãäåāăąǎ".forEach { put(it, 'a') }
    "çćĉċč".forEach { put(it, 'c') }
    "ďđ".forEach { put(it, 'd') }
    "èéêëēĕėęě".forEach { put(it, 'e') }
    "ìíîïĩīĭįı".forEach { put(it, 'i') }
    "ñńņňŋ".forEach { put(it, 'n') }
    "òóôõöøōŏőǒ".forEach { put(it, 'o') }
    "ŕŗř".forEach { put(it, 'r') }
    "śŝşšș".forEach { put(it, 's') }
    "ţťțŧ".forEach { put(it, 't') }
    "ùúûüũūŭůűųǔ".forEach { put(it, 'u') }
    "ýÿŷ".forEach { put(it, 'y') }
    "źżž".forEach { put(it, 'z') }
}
