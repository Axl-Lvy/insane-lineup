package fr.axllvy.insane

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import coil3.ImageLoader
import coil3.compose.setSingletonImageLoaderFactory
import coil3.network.ktor3.KtorNetworkFetcherFactory
import coil3.request.crossfade
import fr.axllvy.insane.data.ArtistImages
import fr.axllvy.insane.data.FavoritesRepository
import fr.axllvy.insane.data.FriendsRepository
import fr.axllvy.insane.data.LineupRepository
import fr.axllvy.insane.data.LocalArtistImages
import fr.axllvy.insane.data.SupabaseLineupClient
import fr.axllvy.insane.data.createSettings
import fr.axllvy.insane.data.createSupabase
import fr.axllvy.insane.notifications.NotificationsController
import fr.axllvy.insane.notifications.createNotificationScheduler
import fr.axllvy.insane.ui.InsaneColors
import fr.axllvy.insane.ui.InsaneTheme
import fr.axllvy.insane.ui.LineupScreen
import io.github.jan.supabase.auth.auth

@Composable
fun App() {
    val deps = remember { buildDependencies() }
    val state by deps.lineup.state.collectAsState()
    val scope = rememberCoroutineScope()

    // Coil 3 on KMP needs an explicit fetcher — the default doesn't pull from
    // the network on iOS/wasm. Ktor3 engine here matches the one used for
    // Postgrest, so we don't drag in a second HTTP stack.
    setSingletonImageLoaderFactory { context ->
        ImageLoader.Builder(context)
            .components { add(KtorNetworkFetcherFactory()) }
            .crossfade(true)
            .build()
    }

    LaunchedEffect(Unit) {
        // Anonymous sign-in (or refresh) before any data calls. supabase-kt auto-loads any
        // persisted session on init; only mint a fresh anon one if none exists.
        runCatching {
                deps.supabase.auth.awaitInitialization()
                if (deps.supabase.auth.currentUserOrNull() == null) {
                    deps.supabase.auth.signInAnonymously()
                }
            }
            .onFailure { logE("auth bootstrap failed: ${it.message}") }

        deps.lineup.loadInitial()
        deps.favorites.loadFromCache()
        deps.lineup.refresh(::nowMs)
        runCatching { deps.favorites.sync() }
        runCatching { deps.favorites.loadCounts() }
        runCatching { deps.friends.loadAll() }
    }

    LaunchedEffect(deps.notifications) {
        deps.notifications.bind(scope, deps.favorites.favorites, deps.lineup.state)
    }

    // Manifest is a tiny (~6 kB) bundled JSON; load once at startup.
    val artistImages by produceState(ArtistImages.Empty) { value = ArtistImages.load() }

    InsaneTheme {
        CompositionLocalProvider(LocalArtistImages provides artistImages) {
            val current = state
            if (current == null) {
                Box(
                    Modifier.fillMaxSize().background(InsaneColors.Bg),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(color = InsaneColors.Accent)
                }
            } else {
                LineupScreen(
                    state = current,
                    favoritesRepo = deps.favorites,
                    friendsRepo = deps.friends,
                    notifications = deps.notifications,
                    onRefresh = {
                        val outcome = deps.lineup.refresh(::nowMs)
                        runCatching { deps.favorites.sync() }
                        runCatching { deps.favorites.loadCounts() }
                        outcome
                    },
                )
            }
        }
    }
}

private class AppDependencies(
    val supabase: io.github.jan.supabase.SupabaseClient,
    val lineup: LineupRepository,
    val favorites: FavoritesRepository,
    val friends: FriendsRepository,
    val notifications: NotificationsController,
)

private fun buildDependencies(): AppDependencies {
    val supabase = createSupabase()
    val settings = createSettings()
    val lineup = LineupRepository(client = SupabaseLineupClient(supabase), settings = settings)
    val favorites = FavoritesRepository(supabase, settings)
    val friends = FriendsRepository(supabase, ::nowMs)
    val notifications =
        NotificationsController(
            scheduler = createNotificationScheduler(),
            settings = settings,
            nowMs = ::nowMs,
        )
    return AppDependencies(supabase, lineup, favorites, friends, notifications)
}

expect fun nowMs(): Long
