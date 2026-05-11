package fr.axllvy.insane.data

import fr.axllvy.insane.Config
import fr.axllvy.insane.data.auth.SessionStore
import fr.axllvy.insane.logE
import fr.axllvy.insane.logI
import io.ktor.client.HttpClient
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType

/**
 * Thin helpers that add the standard Supabase headers (apikey + Bearer access token + schema
 * selector) to every PostgREST call.
 *
 * Schema selection: PostgREST uses `Accept-Profile` for read verbs and `Content-Profile` for write
 * verbs. The lineup table is in `public`; the friends-feature tables live in `insane`. Each caller
 * passes the relevant schema explicitly — defaulting to `LINEUP_SCHEMA` keeps the existing lineup
 * client untouched.
 */
internal suspend fun HttpClient.pgGet(
    session: SessionStore,
    path: String,
    schema: String = Config.LINEUP_SCHEMA,
    block: HttpRequestBuilder.() -> Unit = {},
): HttpResponse =
    pgCall("GET", path, schema) {
        get("${Config.SUPABASE_URL}$path") {
            pgRead(session, schema)
            block()
        }
    }

internal suspend fun HttpClient.pgPost(
    session: SessionStore,
    path: String,
    schema: String = Config.LINEUP_SCHEMA,
    block: HttpRequestBuilder.() -> Unit = {},
): HttpResponse =
    pgCall("POST", path, schema) {
        post("${Config.SUPABASE_URL}$path") {
            pgWrite(session, schema)
            block()
        }
    }

internal suspend fun HttpClient.pgPatch(
    session: SessionStore,
    path: String,
    schema: String = Config.LINEUP_SCHEMA,
    block: HttpRequestBuilder.() -> Unit = {},
): HttpResponse =
    pgCall("PATCH", path, schema) {
        patch("${Config.SUPABASE_URL}$path") {
            pgWrite(session, schema)
            block()
        }
    }

internal suspend fun HttpClient.pgDelete(
    session: SessionStore,
    path: String,
    schema: String = Config.LINEUP_SCHEMA,
    block: HttpRequestBuilder.() -> Unit = {},
): HttpResponse =
    pgCall("DELETE", path, schema) {
        delete("${Config.SUPABASE_URL}$path") {
            pgWrite(session, schema)
            block()
        }
    }

/**
 * Common request wrapper — logs verb/path/status (and rethrows with the original cause). Body
 * logging is deferred to callers, since reading the body here would consume the stream and break
 * parsing downstream.
 */
private suspend inline fun pgCall(
    verb: String,
    path: String,
    schema: String,
    crossinline block: suspend () -> HttpResponse,
): HttpResponse {
    return try {
        val response = block()
        val status = response.status.value
        if (status >= 400) {
            logE("PG $verb $path schema=$schema -> $status (caller will read body)")
        } else {
            logI("PG $verb $path schema=$schema -> $status")
        }
        response
    } catch (t: Throwable) {
        logE("PG $verb $path schema=$schema threw ${t::class.simpleName}: ${t.message}")
        throw t
    }
}

private suspend fun HttpRequestBuilder.pgRead(session: SessionStore, schema: String) {
    header("apikey", Config.SUPABASE_ANON_KEY)
    header(HttpHeaders.Authorization, "Bearer ${session.requireAccessToken()}")
    header(HttpHeaders.Accept, "application/json")
    header("Accept-Profile", schema)
}

private suspend fun HttpRequestBuilder.pgWrite(session: SessionStore, schema: String) {
    header("apikey", Config.SUPABASE_ANON_KEY)
    header(HttpHeaders.Authorization, "Bearer ${session.requireAccessToken()}")
    header(HttpHeaders.Accept, "application/json")
    header("Content-Profile", schema)
    contentType(ContentType.Application.Json)
}
