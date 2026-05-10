package fr.axllvy.insane.data

import fr.axllvy.insane.Config
import fr.axllvy.insane.data.auth.SessionStore
import fr.axllvy.insane.logE
import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * Cloud-backed favorites for the current user.
 *
 * - [favorites] is the source of truth for the UI.
 * - [toggle] applies the change locally first (optimistic) and reconciles on
 *   network failure by reloading.
 */
class FavoritesRepository(
    private val http: HttpClient,
    private val session: SessionStore,
) {
    private val _favorites = MutableStateFlow<Set<String>>(emptySet())
    val favorites: StateFlow<Set<String>> = _favorites.asStateFlow()

    private val json = Json { ignoreUnknownKeys = true }

    /** Pull the user's favorites from Supabase. Safe to call on every launch. */
    suspend fun load() {
        val me = session.userId ?: session.requireAccessToken().let { session.userId } ?: return
        val response = http.pgGet(session, "/rest/v1/favorites", schema = Config.INSANE_SCHEMA) {
            parameter("user_id", "eq.$me")
            parameter("select", "fav_key")
        }
        val body = response.bodyAsText()
        val keys = (json.parseToJsonElement(body) as? JsonArray)
            ?.mapNotNull { (it as? JsonObject)?.get("fav_key")?.jsonPrimitive?.content }
            ?.toSet()
            ?: emptySet()
        _favorites.value = keys
    }

    /** Toggle membership of [key]. Optimistic — rolls back on error. */
    suspend fun toggle(key: String) {
        val isFav = key in _favorites.value
        _favorites.value = if (isFav) _favorites.value - key else _favorites.value + key
        try {
            if (isFav) removeRemote(key) else addRemote(key)
        } catch (t: Throwable) {
            logE("favorites toggle failed: ${t.message}, reloading")
            runCatching { load() }
        }
    }

    private suspend fun addRemote(key: String) {
        val me = session.userId ?: error("no user")
        http.pgPost(session, "/rest/v1/favorites", schema = Config.INSANE_SCHEMA) {
            // Idempotent insert — re-favoriting the same row is a no-op rather
            // than a 409, which matters because optimistic UI may race retries.
            parameter("on_conflict", "user_id,fav_key")
            header("Prefer", "resolution=ignore-duplicates,return=minimal")
            setBody(
                buildJsonArray {
                    add(buildJsonObject {
                        put("user_id", me)
                        put("fav_key", key)
                    })
                }
            )
        }
    }

    private suspend fun removeRemote(key: String) {
        val me = session.userId ?: error("no user")
        http.pgDelete(session, "/rest/v1/favorites", schema = Config.INSANE_SCHEMA) {
            parameter("user_id", "eq.$me")
            parameter("fav_key", "eq.$key")
        }
    }
}
