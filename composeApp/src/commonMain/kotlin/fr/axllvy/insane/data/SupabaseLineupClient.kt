package fr.axllvy.insane.data

import fr.axllvy.insane.Config
import fr.axllvy.insane.data.auth.SessionStore
import fr.axllvy.insane.logE
import fr.axllvy.insane.logI
import io.ktor.client.HttpClient
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText

/**
 * Reads the singleton lineup row from Supabase via the public REST API. Uses the authed Bearer
 * token from [SessionStore]; the lineup table needs an `authenticated` SELECT policy.
 */
class SupabaseLineupClient(private val http: HttpClient, private val session: SessionStore) {

    suspend fun fetchLineup(): Lineup? {
        val path = "/rest/v1/${Config.LINEUP_TABLE}"
        logI("lineup: GET $path id=eq.${Config.LINEUP_ROW_ID} schema=${Config.LINEUP_SCHEMA}")
        val response =
            http.pgGet(session, path) {
                parameter("id", "eq.${Config.LINEUP_ROW_ID}")
                parameter("select", "data")
            }
        val body = response.bodyAsText()
        logI("lineup: status=${response.status.value} bytes=${body.length}")
        if (response.status.value !in 200..299) {
            throw LineupFetchException(status = response.status.value, bodyExcerpt = body.take(400))
        }
        val parsed = parseSupabaseLineupResponse(body)
        if (parsed == null) {
            logE("lineup: 200 but parse returned null. body=${body.take(400)}")
        }
        return parsed
    }
}

class LineupFetchException(val status: Int, val bodyExcerpt: String) :
    RuntimeException("HTTP $status — $bodyExcerpt")
