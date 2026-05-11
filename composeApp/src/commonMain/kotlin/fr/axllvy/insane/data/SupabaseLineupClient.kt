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
import kotlinx.serialization.json.JsonObject

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
}

@Serializable private data class LineupRow(@SerialName("data") val data: JsonObject)

class LineupFetchException(val status: Int, val bodyExcerpt: String) :
    RuntimeException("HTTP $status — $bodyExcerpt")
