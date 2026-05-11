package fr.axllvy.insane.data.auth

import com.russhwolf.settings.Settings
import fr.axllvy.insane.logE
import fr.axllvy.insane.logI
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json

private const val SESSION_KEY = "auth_session_v1"

/**
 * Persists the current GoTrue session and exposes a single coroutine-safe entry point that
 * guarantees a valid access token: [requireAccessToken].
 * - On first call: signs in anonymously.
 * - When the cached token is close to expiry: refreshes.
 * - Concurrent callers share a single in-flight network call via [refreshMutex].
 */
class SessionStore(
    private val settings: Settings,
    private val auth: AuthClient,
    private val nowMs: () -> Long,
) {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }
    private val refreshMutex = Mutex()

    private val _session = MutableStateFlow(load())
    val session: StateFlow<StoredSession?> = _session.asStateFlow()

    val userId: String?
        get() = _session.value?.userId

    /** Returns a non-expired access token, signing in or refreshing as needed. */
    suspend fun requireAccessToken(): String =
        refreshMutex.withLock {
            val now = nowMs()
            val current = _session.value
            if (current != null && !current.isExpired(now)) return current.accessToken

            val fresh =
                if (current == null) {
                    logI("auth: no session, signing in anonymously")
                    auth.signInAnonymously()
                } else {
                    logI("auth: refreshing token")
                    try {
                        auth.refresh(current.refreshToken)
                    } catch (t: Throwable) {
                        // Refresh token rejected (revoked / project reset) — re-sign-in.
                        logE("auth: refresh failed (${t.message}), re-signing in")
                        auth.signInAnonymously()
                    }
                }
            val stored = fresh.toStored(nowMs())
            save(stored)
            _session.value = stored
            stored.accessToken
        }

    /** Clear the local session — used for sign-out / debugging. */
    fun clear() {
        settings.remove(SESSION_KEY)
        _session.value = null
    }

    private fun load(): StoredSession? {
        val raw = settings.getStringOrNull(SESSION_KEY) ?: return null
        return runCatching { json.decodeFromString<StoredSession>(raw) }.getOrNull()
    }

    private fun save(s: StoredSession) {
        settings.putString(SESSION_KEY, json.encodeToString(StoredSession.serializer(), s))
    }
}
