package fr.axllvy.insane

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import fr.axllvy.insane.data.FavoritesRepository
import fr.axllvy.insane.data.FriendsRepository
import fr.axllvy.insane.data.LineupRepository
import fr.axllvy.insane.data.SupabaseLineupClient
import fr.axllvy.insane.data.auth.AuthClient
import fr.axllvy.insane.data.auth.SessionStore
import fr.axllvy.insane.data.createHttpClient
import fr.axllvy.insane.data.createSettings
import fr.axllvy.insane.notifications.NotificationsController
import fr.axllvy.insane.notifications.createNotificationScheduler
import fr.axllvy.insane.ui.InsaneColors
import fr.axllvy.insane.ui.InsaneTheme
import fr.axllvy.insane.ui.LineupScreen

@Composable
fun App() {
    val deps = remember { buildDependencies() }
    val state by deps.lineup.state.collectAsState()
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        // Anonymous sign-in (or refresh) before any data calls. Failures here
        // fall through to the bundled cache path inside loadInitial.
        runCatching { deps.session.requireAccessToken() }
            .onFailure { logE("auth bootstrap failed: ${it.message}") }

        deps.lineup.loadInitial()
        deps.lineup.refresh(::nowMs)
        runCatching { deps.favorites.load() }
        runCatching { deps.friends.loadAll() }
    }

    LaunchedEffect(deps.notifications) {
        deps.notifications.bind(scope, deps.favorites.favorites, deps.lineup.state)
    }

    InsaneTheme {
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
                onRefresh = { deps.lineup.refresh(::nowMs) },
            )
        }
    }
}

private class AppDependencies(
    val session: SessionStore,
    val lineup: LineupRepository,
    val favorites: FavoritesRepository,
    val friends: FriendsRepository,
    val notifications: NotificationsController,
)

private fun buildDependencies(): AppDependencies {
    val http = createHttpClient()
    val settings = createSettings()
    val auth = AuthClient(http)
    val session = SessionStore(settings = settings, auth = auth, nowMs = ::nowMs)
    val lineup = LineupRepository(
        client = SupabaseLineupClient(http, session),
        settings = settings,
    )
    val favorites = FavoritesRepository(http, session)
    val friends = FriendsRepository(http, session, ::nowMs)
    val notifications = NotificationsController(
        scheduler = createNotificationScheduler(),
        settings = settings,
        nowMs = ::nowMs,
    )
    return AppDependencies(session, lineup, favorites, friends, notifications)
}

expect fun nowMs(): Long
