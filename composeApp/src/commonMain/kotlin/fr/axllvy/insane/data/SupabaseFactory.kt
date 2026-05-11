package fr.axllvy.insane.data

import fr.axllvy.insane.Config
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.CodeVerifierCache
import io.github.jan.supabase.auth.SessionManager
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest

/**
 * Builds the single [SupabaseClient] used by every repository. Auth handles its own session
 * persistence (default [io.github.jan.supabase.auth.SettingsSessionManager], backed by
 * `multiplatform-settings`) and auto-refresh, so we no longer need `SessionStore`. Postgrest's
 * `defaultSchema` removes the need for per-call `Accept-Profile`/`Content-Profile` headers.
 *
 * Both overrides are `null` in production (factory uses the platform default). Tests pass
 * [io.github.jan.supabase.auth.MemorySessionManager] and
 * [io.github.jan.supabase.auth.MemoryCodeVerifierCache] so they don't require platform settings —
 * Android `SharedPreferences` isn't available in plain JVM unit tests.
 */
fun createSupabase(
    sessionManager: SessionManager? = null,
    codeVerifierCache: CodeVerifierCache? = null,
    autoRefresh: Boolean = true,
): SupabaseClient =
    createSupabaseClient(
        supabaseUrl = Config.SUPABASE_URL,
        supabaseKey = Config.SUPABASE_ANON_KEY,
    ) {
        install(Auth) {
            alwaysAutoRefresh = autoRefresh
            autoLoadFromStorage = true
            autoSaveToStorage = true
            if (sessionManager != null) this.sessionManager = sessionManager
            if (codeVerifierCache != null) this.codeVerifierCache = codeVerifierCache
        }
        install(Postgrest) { defaultSchema = Config.INSANE_SCHEMA }
    }
