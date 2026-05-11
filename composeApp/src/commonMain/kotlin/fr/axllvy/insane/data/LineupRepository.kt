package fr.axllvy.insane.data

import com.russhwolf.settings.Settings
import fr.axllvy.insane.logE
import fr.axllvy.insane.resources.Res
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.jetbrains.compose.resources.ExperimentalResourceApi

private const val CACHE_KEY = "lineup_json_v1"
private const val CACHE_AT_KEY = "lineup_cached_at_v1"
private const val FALLBACK_RESOURCE = "files/lineup_fallback.json"

sealed interface LineupSource {
    data object Bundled : LineupSource

    data class Cached(val cachedAtMs: Long) : LineupSource

    data object Fresh : LineupSource
}

data class LineupState(
    val lineup: Lineup,
    val source: LineupSource,
    val refreshing: Boolean = false,
    val lastError: String? = null,
)

sealed interface RefreshOutcome {
    data object Refreshed : RefreshOutcome

    data object Offline : RefreshOutcome

    data class Error(val message: String) : RefreshOutcome
}

@OptIn(ExperimentalResourceApi::class)
class LineupRepository(private val client: SupabaseLineupClient, private val settings: Settings) {
    private val _state = MutableStateFlow<LineupState?>(null)
    val state: StateFlow<LineupState?> = _state.asStateFlow()

    /** Load fastest-available lineup so the UI can render immediately. */
    suspend fun loadInitial() {
        val cached = settings.getStringOrNull(CACHE_KEY)?.let(::parseLineupJson)
        if (cached != null) {
            val at = settings.getLongOrNull(CACHE_AT_KEY) ?: 0L
            _state.value = LineupState(cached, LineupSource.Cached(at))
            return
        }
        val bundled = parseLineupJson(Res.readBytes(FALLBACK_RESOURCE).decodeToString())
        if (bundled != null) {
            _state.value = LineupState(bundled, LineupSource.Bundled)
        }
    }

    /**
     * Push a new lineup to Supabase and reflect it locally. Throws on REST error so callers can
     * show a precise message. Cache + state are only mutated after a successful round-trip.
     */
    suspend fun save(lineup: Lineup, now: () -> Long) {
        client.updateLineup(lineup)
        settings.putString(CACHE_KEY, serializeLineup(lineup))
        settings.putLong(CACHE_AT_KEY, now())
        _state.value = LineupState(lineup, LineupSource.Fresh)
    }

    /** Try to fetch from Supabase; on success update state + cache, on failure keep current. */
    suspend fun refresh(now: () -> Long): RefreshOutcome {
        val current = _state.value
        if (current != null) _state.value = current.copy(refreshing = true, lastError = null)
        return try {
            val fresh = client.fetchLineup()
            if (fresh != null) {
                settings.putString(CACHE_KEY, serializeLineup(fresh))
                settings.putLong(CACHE_AT_KEY, now())
                _state.value = LineupState(fresh, LineupSource.Fresh)
                RefreshOutcome.Refreshed
            } else {
                if (current != null)
                    _state.value = current.copy(refreshing = false, lastError = "Empty response")
                RefreshOutcome.Error("Empty response")
            }
        } catch (t: Throwable) {
            val offline = t.looksOffline()
            // Full chain to console only — the UI just shows a terse label.
            logE("refresh threw ${t::class.simpleName}: ${t.describeChain()} (offline=$offline)")
            val short =
                if (offline) "Offline" else (t.message?.take(60) ?: t::class.simpleName.orEmpty())
            _state.value = current?.copy(refreshing = false, lastError = short)
            if (offline) RefreshOutcome.Offline else RefreshOutcome.Error(short)
        }
    }
}

/**
 * Walks the exception chain so callers see the real reason. PostgREST puts useful info inside the
 * response body — [LineupFetchException] forwards it into the message; the rest of the chain
 * captures network-layer wrappers.
 */
private fun Throwable.describeChain(): String {
    val parts = mutableListOf<String>()
    var t: Throwable? = this
    var depth = 0
    while (t != null && depth < 4) {
        val cls = t::class.simpleName.orEmpty()
        val msg = t.message?.takeIf { it.isNotBlank() }
        parts += if (msg != null) "$cls: $msg" else cls
        val next = t.cause
        if (next === t) break
        t = next
        depth++
    }
    return parts.joinToString(" ← ")
}

private fun Throwable.looksOffline(): Boolean {
    var t: Throwable? = this
    var depth = 0
    val sb = StringBuilder()
    while (t != null && depth < 5) {
        sb.append(t::class.simpleName.orEmpty()).append(' ')
        sb.append(t.message.orEmpty()).append(' ')
        t = t.cause
        depth++
    }
    val s = sb.toString().lowercase()
    return "unknownhost" in s ||
        "unable to resolve" in s ||
        "no address" in s ||
        "unreachable" in s ||
        "no internet" in s ||
        "offline" in s ||
        "connection refused" in s ||
        "failed to connect" in s
}
