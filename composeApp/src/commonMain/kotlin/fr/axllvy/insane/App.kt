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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import fr.axllvy.insane.data.LineupRepository
import fr.axllvy.insane.data.SupabaseLineupClient
import fr.axllvy.insane.data.createHttpClient
import fr.axllvy.insane.data.createSettings
import fr.axllvy.insane.ui.InsaneColors
import fr.axllvy.insane.ui.InsaneTheme
import fr.axllvy.insane.ui.LineupScreen

@Composable
fun App() {
    val repo = remember {
        LineupRepository(
            client = SupabaseLineupClient(createHttpClient()),
            settings = createSettings(),
        )
    }
    val state by repo.state.collectAsState()

    LaunchedEffect(Unit) {
        repo.loadInitial()
        repo.refresh(::nowMs)
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
                onRefresh = { repo.refresh(::nowMs) },
            )
        }
    }
}

expect fun nowMs(): Long
