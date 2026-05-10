package fr.axllvy.inase.data

import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

private val json = Json {
    ignoreUnknownKeys = true
    isLenient = true
}

/** Parse a Lineup from the JSON shape stored in Supabase: `{ jeu: { MIRAGE: [...] }, ... }`. */
fun parseLineupJson(text: String): Lineup? = runCatching {
    val raw: Map<String, Map<String, List<SetEntry>>> = json.decodeFromString(text)
    raw.entries
        .mapNotNull { (dayId, stages) ->
            val day = DayKey.fromId(dayId) ?: run {
                println("[Inase] parseLineupJson: skipping unknown dayId=$dayId")
                return@mapNotNull null
            }
            val stageMap = stages.entries.mapNotNull { (stageId, sets) ->
                runCatching { StageKey.valueOf(stageId) }.getOrNull()?.let { it to sets }
                    ?: run {
                        println("[Inase] parseLineupJson: skipping unknown stageId=$stageId for day=$dayId")
                        null
                    }
            }.toMap()
            day to stageMap
        }
        .toMap()
}.onFailure { println("[Inase] parseLineupJson failed: ${it::class.simpleName}: ${it.message}") }
    .getOrNull()

fun serializeLineup(lineup: Lineup): String {
    val raw: Map<String, Map<String, List<SetEntry>>> =
        lineup.entries.associate { (day, stages) ->
            day.id to stages.entries.associate { (stage, sets) -> stage.name to sets }
        }
    val serializer = MapSerializer(
        String.serializer(),
        MapSerializer(String.serializer(), ListSerializer(SetEntry.serializer())),
    )
    return json.encodeToString(serializer, raw)
}

/** Parse a Supabase REST response (`[{ "data": {...} }]`) and return the inner Lineup. */
fun parseSupabaseLineupResponse(body: String): Lineup? = runCatching {
    val rows = json.parseToJsonElement(body) as? JsonArray ?: run {
        println("[Inase] supabase response is not a JSON array")
        return@runCatching null
    }
    val first = rows.firstOrNull() as? JsonObject ?: run {
        println("[Inase] supabase response array is empty or row is not an object (size=${rows.size})")
        return@runCatching null
    }
    val data = first["data"]?.jsonObject ?: run {
        println("[Inase] supabase row missing 'data' field; keys=${first.keys}")
        return@runCatching null
    }
    val lineup = parseLineupJson(data.toString())
    if (lineup == null) println("[Inase] parseLineupJson returned null for data field")
    lineup
}.onFailure { println("[Inase] parseSupabaseLineupResponse failed: ${it::class.simpleName}: ${it.message}") }
    .getOrNull()
