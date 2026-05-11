package fr.axllvy.insane.data

import fr.axllvy.insane.Config
import fr.axllvy.insane.logE
import fr.axllvy.insane.logI
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.exceptions.RestException
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Reads the singleton lineup row from Supabase. The lineup table needs an `authenticated` SELECT
 * policy; the supabase-kt [io.github.jan.supabase.auth.Auth] plugin attaches the access token to
 * every PostgREST call.
 */
class SupabaseLineupClient(private val supabase: SupabaseClient) {

    suspend fun fetchLineup(): Lineup? {
        logI("lineup: select id=${Config.LINEUP_ROW_ID} schema=${Config.LINEUP_SCHEMA}")
        val row =
            try {
                supabase
                    .from(Config.LINEUP_TABLE)
                    .select(columns = Columns.list("data")) {
                        filter { eq("id", Config.LINEUP_ROW_ID) }
                    }
                    .decodeSingleOrNull<LineupRow>()
            } catch (t: RestException) {
                throw LineupFetchException(status = t.statusCode, bodyExcerpt = t.error.take(400))
            }
        if (row == null) {
            logI("lineup: no row")
            return null
        }
        val parsed = parseLineupJson(row.data.toString())
        if (parsed == null) {
            logE("lineup: row found but parse returned null")
        }
        return parsed
    }

    /**
     * Overwrite the lineup row. RLS gates this to admin users (insane.profiles.is_admin = true);
     * non-admin calls fail with HTTP 401/403 from PostgREST and surface as [LineupFetchException].
     */
    suspend fun updateLineup(lineup: Lineup) {
        val payload = Json.parseToJsonElement(serializeLineup(lineup)) as JsonObject
        logI("lineup: update id=${Config.LINEUP_ROW_ID}")
        try {
            supabase.from(Config.LINEUP_TABLE).update(buildJsonObject { put("data", payload) }) {
                filter { eq("id", Config.LINEUP_ROW_ID) }
            }
        } catch (t: RestException) {
            throw LineupFetchException(status = t.statusCode, bodyExcerpt = t.error.take(400))
        }
    }
}

@Serializable private data class LineupRow(@SerialName("data") val data: JsonObject)

class LineupFetchException(val status: Int, val bodyExcerpt: String) :
    RuntimeException("HTTP $status — $bodyExcerpt")
