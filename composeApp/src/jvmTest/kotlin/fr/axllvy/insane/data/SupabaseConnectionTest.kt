package fr.axllvy.insane.data

import io.github.jan.supabase.auth.MemoryCodeVerifierCache
import io.github.jan.supabase.auth.MemorySessionManager
import io.github.jan.supabase.auth.auth
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain

/**
 * Live integration test against the production Supabase project. Asserts:
 * - anon sign-in mints a session,
 * - the configured `insane.insane_lineup` row is readable with that session,
 * - the JSON parses into a non-empty [Lineup].
 *
 * Lives under `jvmTest` — the dedicated JVM target hosts a real `Dispatchers.Default` for
 * supabase-kt's lifecycle hooks (Android unit tests would need Robolectric, wasmJs needs a
 * browser). Requires network. Runs against the URL/key in [fr.axllvy.insane.Config], so it fails
 * closed if the project is paused or if RLS / `Exposed schemas` are misconfigured. Invoke via
 * `./gradlew :composeApp:jvmTest` or the IDE.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SupabaseConnectionTest {

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(Dispatchers.Default)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun fetchLineupOverSupabase() = runBlocking {
        // In-memory caches avoid platform Settings (Android SharedPreferences isn't available
        // in plain JVM unit tests). autoRefresh=false stops the background refresh scheduler.
        val supabase =
            createSupabase(
                sessionManager = MemorySessionManager(),
                codeVerifierCache = MemoryCodeVerifierCache(),
                autoRefresh = false,
            )
        try {
            supabase.auth.awaitInitialization()
            if (supabase.auth.currentUserOrNull() == null) {
                supabase.auth.signInAnonymously()
            }
            assertNotNull(supabase.auth.currentUserOrNull(), "anon sign-in failed")

            val lineup = SupabaseLineupClient(supabase).fetchLineup()
            assertNotNull(lineup, "fetchLineup returned null — RLS, schema, or row missing")
            assertTrue(lineup.isNotEmpty(), "lineup parsed but contains no days")
        } finally {
            supabase.close()
        }
    }
}
