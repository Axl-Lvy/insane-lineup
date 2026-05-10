package fr.axllvy.inase

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
import fr.axllvy.inase.data.LineupRepository
import fr.axllvy.inase.data.SupabaseLineupClient
import fr.axllvy.inase.data.createHttpClient
import fr.axllvy.inase.data.createSettings
import fr.axllvy.inase.ui.InaseColors
import fr.axllvy.inase.ui.InaseTheme
import fr.axllvy.inase.ui.LineupScreen

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

    InaseTheme {
        val current = state
        if (current == null) {
            Box(
                Modifier.fillMaxSize().background(InaseColors.Bg),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(color = InaseColors.Accent)
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
