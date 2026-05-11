package fr.axllvy.insane.data

import com.russhwolf.settings.Settings
import fr.axllvy.insane.logE
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

private const val CACHE_KEY = "favorites_v1"
private const val PENDING_KEY = "favorites_pending_v1"
private const val OWNER_KEY = "favorites_owner_v1"

/**
 * Local-first favorites. The UI reads from on-disk cache at launch; toggles persist locally before
 * the network is touched. A pending-intent map carries offline edits across launches and drains on
 * every [sync].
 *
 * Conflict policy: a key present in `pending` overrides whatever the server returns, until that
 * intent has been acknowledged. Single-device offline edits round-trip safely; multi-device
 * divergence is last-sync-wins (acceptable here — rows carry no `updated_at` for per-edit LWW).
 */
class FavoritesRepository(private val supabase: SupabaseClient, private val settings: Settings) {
    private val _favorites = MutableStateFlow<Set<String>>(emptySet())
    val favorites: StateFlow<Set<String>> = _favorites.asStateFlow()

    /**
     * Per-set favorite counts across all users (keyed by fav_key). Populated by [loadCounts] from
     * the aggregate RPC; nudged optimistically on every local [toggle] so the UI reacts before the
     * next sync lands.
     */
    private val _counts = MutableStateFlow<Map<String, Int>>(emptyMap())
    val counts: StateFlow<Map<String, Int>> = _counts.asStateFlow()

    private val mutex = Mutex()
    private val pending: MutableMap<String, Intent> = mutableMapOf()

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private val userId: String?
        get() = supabase.auth.currentUserOrNull()?.id

    /**
     * Hydrate the flow from disk. If the cached owner doesn't match the current session we wipe —
     * prevents a previous anonymous identity's favorites from leaking into a freshly minted
     * session.
     */
    fun loadFromCache() {
        val me = userId
        val cachedOwner = settings.getStringOrNull(OWNER_KEY)
        if (me == null || cachedOwner == null || cachedOwner != me) {
            settings.remove(CACHE_KEY)
            settings.remove(PENDING_KEY)
            if (me == null) settings.remove(OWNER_KEY)
            pending.clear()
            _favorites.value = emptySet()
            return
        }
        val cached =
            settings
                .getStringOrNull(CACHE_KEY)
                ?.let {
                    runCatching { json.decodeFromString(CachedFavorites.serializer(), it) }
                        .getOrNull()
                }
                ?.keys
                ?.toSet() ?: emptySet()
        val pendingState =
            settings
                .getStringOrNull(PENDING_KEY)
                ?.let {
                    runCatching { json.decodeFromString(PendingState.serializer(), it) }.getOrNull()
                }
                ?.intents ?: emptyMap()
        pending.clear()
        pending.putAll(pendingState)
        _favorites.value = applyPending(cached)
    }

    /**
     * Optimistic toggle — persist locally, then best-effort drain. If the network call fails the
     * pending entry survives and the next [sync] retries.
     */
    suspend fun toggle(key: String) {
        mutex.withLock {
            val nowFav = key !in _favorites.value
            _favorites.value = if (nowFav) _favorites.value + key else _favorites.value - key
            pending[key] = if (nowFav) Intent.ADD else Intent.REMOVE
            persist(_favorites.value)
            // Nudge the local count so the badge updates instantly; sync() will
            // overwrite with the authoritative aggregate.
            val current = _counts.value[key] ?: 0
            val next = (if (nowFav) current + 1 else current - 1).coerceAtLeast(0)
            _counts.value = _counts.value + (key to next)
        }
        runCatching { sync() }
            .onFailure { logE("favorites: sync after toggle failed: ${it.message}") }
    }

    /**
     * Drain pending intents, then pull the canonical set. Safe to call repeatedly and concurrently
     * — a second caller will see an empty queue if the first already drained it.
     */
    suspend fun sync() {
        val me = userId ?: return

        if (settings.getStringOrNull(OWNER_KEY) != me) {
            settings.putString(OWNER_KEY, me)
        }

        val snapshot = mutex.withLock { pending.toMap() }
        val succeeded = mutableSetOf<String>()
        for ((key, intent) in snapshot) {
            val ok =
                runCatching {
                        when (intent) {
                            Intent.ADD -> addRemote(me, key)
                            Intent.REMOVE -> removeRemote(me, key)
                        }
                    }
                    .isSuccess
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

    /** Pull the per-set aggregate counts across every user. Safe to call alone. */
    suspend fun loadCounts() {
        val rows = supabase.postgrest.rpc("favorite_counts").decodeList<CountRow>()
        _counts.value = rows.associate { it.fav_key to it.count.toInt() }
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
        val rows =
            supabase
                .from("favorites")
                .select(columns = Columns.list("fav_key")) { filter { eq("user_id", me) } }
                .decodeList<FavRow>()
        return rows.mapTo(mutableSetOf()) { it.fav_key }
    }

    private suspend fun addRemote(me: String, key: String) {
        supabase.from("favorites").upsert(FavRow(user_id = me, fav_key = key)) {
            onConflict = "user_id,fav_key"
            ignoreDuplicates = true
        }
    }

    private suspend fun removeRemote(me: String, key: String) {
        supabase.from("favorites").delete {
            filter {
                eq("user_id", me)
                eq("fav_key", key)
            }
        }
    }
}

@Serializable private data class FavRow(val user_id: String, val fav_key: String)

@Serializable private data class CountRow(val fav_key: String, val count: Long)

@Serializable private data class CachedFavorites(val keys: List<String>)

@Serializable private data class PendingState(val intents: Map<String, Intent>)

@Serializable
private enum class Intent {
    ADD,
    REMOVE,
}
