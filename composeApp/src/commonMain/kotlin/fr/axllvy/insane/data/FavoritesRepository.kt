package fr.axllvy.insane.data

import com.russhwolf.settings.Settings
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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

private const val CACHE_KEY = "favorites_v1"
private const val PENDING_KEY = "favorites_pending_v1"
private const val OWNER_KEY = "favorites_owner_v1"

/**
 * Local-first favorites. The UI reads from on-disk cache at launch; toggles
 * persist locally before the network is touched. A pending-intent map carries
 * offline edits across launches and drains on every [sync].
 *
 * Conflict policy: a key present in `pending` overrides whatever the server
 * returns, until that intent has been acknowledged. Single-device offline edits
 * round-trip safely; multi-device divergence is last-sync-wins (acceptable
 * here — rows carry no `updated_at` for per-edit LWW).
 */
class FavoritesRepository(
    private val http: HttpClient,
    private val session: SessionStore,
    private val settings: Settings,
) {
    private val _favorites = MutableStateFlow<Set<String>>(emptySet())
    val favorites: StateFlow<Set<String>> = _favorites.asStateFlow()

    private val mutex = Mutex()
    private val pending: MutableMap<String, Intent> = mutableMapOf()

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /**
     * Hydrate the flow from disk. If the cached owner doesn't match the current
     * session we wipe — prevents a previous anonymous identity's favorites from
     * leaking into a freshly minted session.
     */
    fun loadFromCache() {
        val me = session.userId
        val cachedOwner = settings.getStringOrNull(OWNER_KEY)
        if (me == null || cachedOwner == null || cachedOwner != me) {
            settings.remove(CACHE_KEY)
            settings.remove(PENDING_KEY)
            if (me == null) settings.remove(OWNER_KEY)
            pending.clear()
            _favorites.value = emptySet()
            return
        }
        val cached = settings.getStringOrNull(CACHE_KEY)
            ?.let { runCatching { json.decodeFromString(CachedFavorites.serializer(), it) }.getOrNull() }
            ?.keys
            ?.toSet()
            ?: emptySet()
        val pendingState = settings.getStringOrNull(PENDING_KEY)
            ?.let { runCatching { json.decodeFromString(PendingState.serializer(), it) }.getOrNull() }
            ?.intents
            ?: emptyMap()
        pending.clear()
        pending.putAll(pendingState)
        _favorites.value = applyPending(cached)
    }

    /**
     * Optimistic toggle — persist locally, then best-effort drain. If the
     * network call fails the pending entry survives and the next [sync] retries.
     */
    suspend fun toggle(key: String) {
        mutex.withLock {
            val nowFav = key !in _favorites.value
            _favorites.value = if (nowFav) _favorites.value + key else _favorites.value - key
            pending[key] = if (nowFav) Intent.ADD else Intent.REMOVE
            persist(_favorites.value)
        }
        runCatching { sync() }
            .onFailure { logE("favorites: sync after toggle failed: ${it.message}") }
    }

    /**
     * Drain pending intents, then pull the canonical set. Safe to call
     * repeatedly and concurrently — a second caller will see an empty queue if
     * the first already drained it.
     */
    suspend fun sync() {
        val me = session.userId
            ?: session.requireAccessToken().let { session.userId }
            ?: return

        if (settings.getStringOrNull(OWNER_KEY) != me) {
            settings.putString(OWNER_KEY, me)
        }

        val snapshot = mutex.withLock { pending.toMap() }
        val succeeded = mutableSetOf<String>()
        for ((key, intent) in snapshot) {
            val ok = runCatching {
                when (intent) {
                    Intent.ADD -> addRemote(me, key)
                    Intent.REMOVE -> removeRemote(me, key)
                }
            }.isSuccess
            if (ok) succeeded += key
        }

        val canFetch = mutex.withLock {
            // Only drop ops we successfully flushed *and* that haven't been
            // superseded by a toggle that landed mid-flush.
            succeeded.forEach { k -> if (pending[k] == snapshot[k]) pending.remove(k) }
            persist(_favorites.value)
            pending.isEmpty()
        }
        if (!canFetch) return

        val remote = runCatching { fetchRemote(me) }.getOrNull() ?: return
        mutex.withLock {
            if (pending.isEmpty()) {
                _favorites.value = remote
                persist(remote)
            }
            // else: a fresh toggle arrived during the fetch; keep the optimistic
            // view and let the next sync reconcile.
        }
    }

    private fun applyPending(base: Set<String>): Set<String> {
        if (pending.isEmpty()) return base
        val merged = base.toMutableSet()
        for ((key, intent) in pending) {
            when (intent) {
                Intent.ADD -> merged.add(key)
                Intent.REMOVE -> merged.remove(key)
            }
        }
        return merged
    }

    private fun persist(visible: Set<String>) {
        settings.putString(
            CACHE_KEY,
            json.encodeToString(CachedFavorites.serializer(), CachedFavorites(visible.toList())),
        )
        settings.putString(
            PENDING_KEY,
            json.encodeToString(PendingState.serializer(), PendingState(pending.toMap())),
        )
    }

    private suspend fun fetchRemote(me: String): Set<String> {
        val response = http.pgGet(session, "/rest/v1/favorites", schema = Config.INSANE_SCHEMA) {
            parameter("user_id", "eq.$me")
            parameter("select", "fav_key")
        }
        val rows = json.parseToJsonElement(response.bodyAsText()) as? JsonArray ?: return emptySet()
        return rows.mapNotNull { (it as? JsonObject)?.get("fav_key")?.jsonPrimitive?.content }.toSet()
    }

    private suspend fun addRemote(me: String, key: String) {
        http.pgPost(session, "/rest/v1/favorites", schema = Config.INSANE_SCHEMA) {
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

    private suspend fun removeRemote(me: String, key: String) {
        http.pgDelete(session, "/rest/v1/favorites", schema = Config.INSANE_SCHEMA) {
            parameter("user_id", "eq.$me")
            parameter("fav_key", "eq.$key")
        }
    }
}

@Serializable
private data class CachedFavorites(val keys: List<String>)

@Serializable
private data class PendingState(val intents: Map<String, Intent>)

@Serializable
private enum class Intent { ADD, REMOVE }
