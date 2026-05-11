package fr.axllvy.insane.data.auth

import fr.axllvy.insane.Config
import fr.axllvy.insane.logE
import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

/**
 * Thin wrapper over the GoTrue REST API. Always includes the apikey header so it works *before* we
 * have a session.
 *
 * We deserialize the response body manually (not via ktor's `.body()`) so a non-2xx error response
 * — typically `{"code": "...", "msg": "..."}` for disabled providers — surfaces the real message
 * instead of a confusing "fields missing" deserialize error.
 */
class AuthClient(private val http: HttpClient) {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    suspend fun signInAnonymously(): GoTrueSession {
        val body = buildJsonObject {
            put("data", JsonObject(emptyMap()))
            putJsonObject("gotrue_meta_security") { /* no captcha */ }
        }
        return request("/auth/v1/signup", body)
    }

    suspend fun refresh(refreshToken: String): GoTrueSession {
        val body = buildJsonObject { put("refresh_token", refreshToken) }
        return request("/auth/v1/token", body, queryGrantType = "refresh_token")
    }

    private suspend fun request(
        path: String,
        body: JsonObject,
        queryGrantType: String? = null,
    ): GoTrueSession {
        val response =
            http.post("${Config.SUPABASE_URL}$path") {
                header("apikey", Config.SUPABASE_ANON_KEY)
                header(HttpHeaders.Authorization, "Bearer ${Config.SUPABASE_ANON_KEY}")
                contentType(ContentType.Application.Json)
                if (queryGrantType != null) parameter("grant_type", queryGrantType)
                setBody(body)
            }
        val text = response.bodyAsText()
        val status = response.status.value
        if (status !in 200..299) {
            logE("auth: $path -> $status body=${text.take(400)}")
            throw GoTrueException(status, text.take(300))
        }
        return runCatching { json.decodeFromString(GoTrueSession.serializer(), text) }
            .getOrElse {
                // 2xx but the body isn't a session — log the body and bail
                // with a useful error rather than a JsonConvertException.
                logE("auth: $path -> 2xx but unexpected body=${text.take(400)}")
                throw GoTrueException(status, "unexpected body: ${text.take(200)}")
            }
    }
}

class GoTrueException(val status: Int, val bodyExcerpt: String) :
    RuntimeException("GoTrue $status — $bodyExcerpt")
