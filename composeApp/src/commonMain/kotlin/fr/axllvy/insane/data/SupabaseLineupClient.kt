package fr.axllvy.insane.data

import fr.axllvy.insane.Config
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText

/**
 * Reads the singleton lineup row from Supabase via the public REST API.
 * Requires an anon SELECT policy on the table — see README.
 */
class SupabaseLineupClient(private val http: HttpClient) {

    suspend fun fetchLineup(): Lineup? {
        val url = "${Config.SUPABASE_URL}/rest/v1/${Config.LINEUP_TABLE}"
        println("[Insane] GET $url id=eq.${Config.LINEUP_ROW_ID}")
        val response = http.get(url) {
            header("apikey", Config.SUPABASE_ANON_KEY)
            header("Authorization", "Bearer ${Config.SUPABASE_ANON_KEY}")
            header("Accept", "application/json")
            header("Accept-Profile", Config.LINEUP_SCHEMA)
            parameter("id", "eq.${Config.LINEUP_ROW_ID}")
            parameter("select", "data")
        }
        val body = response.bodyAsText()
        println("[Insane] response status=${response.status} bytes=${body.length}")
        println("[Insane] body preview=${body.take(400)}")
        return parseSupabaseLineupResponse(body)
    }
}
