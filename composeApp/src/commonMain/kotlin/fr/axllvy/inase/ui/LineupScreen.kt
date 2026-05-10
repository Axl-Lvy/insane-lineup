package fr.axllvy.inase.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.axllvy.inase.data.DayKey
import fr.axllvy.inase.data.LineupSource
import fr.axllvy.inase.data.LineupState
import fr.axllvy.inase.data.RefreshOutcome
import fr.axllvy.inase.data.SetEntry
import fr.axllvy.inase.data.StageKey
import fr.axllvy.inase.data.timeToMin
import kotlinx.coroutines.launch

private const val DAY_TOTAL_MIN = 16 * 60
private val PX_PER_MIN = 1.6f
private val TIMELINE_HEIGHT = (DAY_TOTAL_MIN * PX_PER_MIN).dp
private val TIME_COL_WIDTH = 38.dp
private val HOUR_COUNT = 17

@Composable
fun LineupScreen(state: LineupState, onRefresh: suspend () -> RefreshOutcome) {
    var day by rememberSaveable { mutableStateOf(DayKey.JEU) }
    var favs by rememberSaveable { mutableStateOf(setOf<String>()) }
    var favsOnly by rememberSaveable { mutableStateOf(false) }
    var hiddenStages by rememberSaveable { mutableStateOf(setOf<StageKey>()) }
    var selected by remember { mutableStateOf<String?>(null) }

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val triggerRefresh: () -> Unit = {
        if (!state.refreshing) {
            scope.launch {
                val outcome = onRefresh()
                val msg = when (outcome) {
                    RefreshOutcome.Refreshed -> "Lineup refreshed"
                    RefreshOutcome.Offline -> "You're offline"
                    is RefreshOutcome.Error -> "Refresh failed: ${outcome.message}"
                }
                snackbarHostState.currentSnackbarData?.dismiss()
                snackbarHostState.showSnackbar(msg)
            }
        }
    }

    Box(Modifier.fillMaxSize().background(verticalGradient())) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Header(
                state = state,
                day = day,
                onDayChange = { day = it },
                hiddenStages = hiddenStages,
                onToggleStage = { s ->
                    hiddenStages = if (s in hiddenStages) hiddenStages - s else hiddenStages + s
                },
                favsOnly = favsOnly,
                onToggleFavsOnly = { favsOnly = !favsOnly },
                favCount = favs.size,
                onRefresh = triggerRefresh,
            )

            Timeline(
                state = state,
                day = day,
                hiddenStages = hiddenStages,
                favs = favs,
                favsOnly = favsOnly,
                onSelect = { selected = it },
                onRefresh = triggerRefresh,
                modifier = Modifier.weight(1f),
            )
        }

        SourceBadge(state = state, modifier = Modifier.align(Alignment.BottomEnd).padding(12.dp))

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp),
            snackbar = { data ->
                Snackbar(
                    snackbarData = data,
                    containerColor = InaseColors.BgMid,
                    contentColor = InaseColors.OnBg,
                )
            },
        )

        selected?.let { key ->
            DetailDialog(
                selectedKey = key,
                day = day,
                state = state,
                isFav = key in favs,
                onToggleFav = { favs = if (key in favs) favs - key else favs + key },
                onDismiss = { selected = null },
            )
        }
    }
}

@Composable
private fun verticalGradient() = Brush.verticalGradient(
    listOf(InaseColors.BgTop, InaseColors.BgMid, InaseColors.Bg)
)

@Composable
private fun Header(
    state: LineupState,
    day: DayKey,
    onDayChange: (DayKey) -> Unit,
    hiddenStages: Set<StageKey>,
    onToggleStage: (StageKey) -> Unit,
    favsOnly: Boolean,
    onToggleFavsOnly: () -> Unit,
    favCount: Int,
    onRefresh: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(Color(0xD9_0A_08_14))
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .border(1.dp, InaseColors.Border, RoundedCornerShape(0.dp)),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    "INSANE FESTIVAL",
                    color = Color.White,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    "Beyond ✦ Reality",
                    color = InaseColors.Accent,
                    fontSize = 10.sp,
                )
            }
            IconButton(onClick = onRefresh, enabled = !state.refreshing) {
                if (state.refreshing) {
                    CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
                } else {
                    Icon(Icons.Filled.Refresh, contentDescription = "Refresh", tint = InaseColors.Accent)
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for (d in DayKey.entries) {
                val active = d == day
                Column(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            if (active) Color(0x33A78BFA) else Color(0x08FFFFFF)
                        )
                        .border(
                            1.dp,
                            if (active) InaseColors.Accent else InaseColors.Border,
                            RoundedCornerShape(8.dp),
                        )
                        .clickable { onDayChange(d) }
                        .padding(vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(d.label, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Text(d.date, color = InaseColors.TextDim, fontSize = 9.sp)
                }
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            StageKey.entries.forEach { s ->
                val meta = stageMeta.getValue(s)
                val hidden = s in hiddenStages
                Row(
                    Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .border(
                            1.dp,
                            if (hidden) InaseColors.Border else meta.color,
                            RoundedCornerShape(999.dp),
                        )
                        .clickable { onToggleStage(s) }
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    Box(
                        Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(if (hidden) Color.Transparent else meta.color)
                            .border(1.dp, meta.color, CircleShape)
                    )
                    Text(
                        meta.label,
                        color = if (hidden) Color(0x4DFFFFFF) else meta.color,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
            Spacer(Modifier.weight(1f))
            Row(
                Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(if (favsOnly) Color(0x26FBBF24) else Color.Transparent)
                    .border(1.dp, InaseColors.Star, RoundedCornerShape(999.dp))
                    .clickable { onToggleFavsOnly() }
                    .padding(horizontal = 9.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Icon(
                    if (favsOnly) Icons.Filled.Star else Icons.Outlined.StarBorder,
                    contentDescription = null,
                    tint = InaseColors.Star,
                    modifier = Modifier.size(11.dp),
                )
                Text("$favCount", color = InaseColors.Star, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Timeline(
    state: LineupState,
    day: DayKey,
    hiddenStages: Set<StageKey>,
    favs: Set<String>,
    favsOnly: Boolean,
    onSelect: (String) -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val visibleStages = StageKey.entries.filterNot { it in hiddenStages }
    val dayData = state.lineup[day].orEmpty()

    PullToRefreshBox(
        isRefreshing = state.refreshing,
        onRefresh = onRefresh,
        modifier = modifier.fillMaxWidth(),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 8.dp, vertical = 12.dp)
        ) {
            Box(Modifier.fillMaxWidth().height(TIMELINE_HEIGHT + 24.dp)) {
            // Hour grid lines
            for (i in 0 until HOUR_COUNT) {
                Box(
                    Modifier
                        .padding(start = TIME_COL_WIDTH, top = (i * 60 * PX_PER_MIN).dp)
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(Color(0x0AFFFFFF))
                )
            }

            // Hour labels
            Box(Modifier.width(TIME_COL_WIDTH).fillMaxHeight()) {
                for (i in 0 until HOUR_COUNT) {
                    val h = (12 + i) % 24
                    Text(
                        text = "${h.toString().padStart(2, '0')}:00",
                        color = Color(0x66FFFFFF),
                        fontSize = 9.5.sp,
                        modifier = Modifier
                            .offset(y = (i * 60 * PX_PER_MIN - 7).dp)
                    )
                }
            }

            // Stage columns
            Row(
                Modifier
                    .padding(start = TIME_COL_WIDTH)
                    .fillMaxWidth()
                    .height(TIMELINE_HEIGHT),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                for (stage in visibleStages) {
                    val sets = dayData[stage].orEmpty()
                    StageColumn(
                        stage = stage,
                        sets = sets,
                        day = day,
                        favs = favs,
                        favsOnly = favsOnly,
                        onSelect = onSelect,
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                    )
                }
            }
        }
        }
    }
}

@Composable
private fun StageColumn(
    stage: StageKey,
    sets: List<SetEntry>,
    day: DayKey,
    favs: Set<String>,
    favsOnly: Boolean,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val meta = stageMeta.getValue(stage)
    Box(
        modifier
            .clip(RoundedCornerShape(4.dp))
            .background(Color(0x04FFFFFF))
    ) {
        sets.forEach { set ->
            val durationMin = timeToMin(set.e) - timeToMin(set.s)
            if (durationMin <= 0) return@forEach
            val top = (timeToMin(set.s) * PX_PER_MIN).dp.coerceAtLeast(0.dp)
            val height = (durationMin * PX_PER_MIN).dp
            val barHeight = (height - 4.dp).coerceAtLeast(0.dp)
            val key = "${day.id}|${stage.name}|${set.s}|${set.a}"
            val isFav = key in favs
            val dimmed = favsOnly && !isFav

            Box(
                Modifier
                    .padding(top = top + 2.dp, start = 2.dp, end = 2.dp)
                    .height(barHeight)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(4.dp))
                    .background(
                        if (isFav)
                            meta.color.copy(alpha = 0.22f)
                        else
                            meta.color.copy(alpha = 0.12f)
                    )
                    .border(
                        width = 2.dp,
                        color = meta.color.copy(alpha = if (dimmed) 0.18f else 1f),
                        shape = RoundedCornerShape(topStart = 0.dp, bottomStart = 0.dp, topEnd = 4.dp, bottomEnd = 4.dp),
                    )
                    .clickable { onSelect(key) }
                    .padding(horizontal = 5.dp, vertical = 4.dp),
            ) {
                Column {
                    Text(
                        set.s,
                        color = Color(0x8CFFFFFF),
                        fontSize = 8.5.sp,
                    )
                    Text(
                        set.a,
                        color = Color.White.copy(alpha = if (dimmed) 0.18f else 1f),
                        fontSize = if (height < 35.dp) 9.sp else if (height < 60.dp) 10.sp else 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (isFav) {
                    Icon(
                        Icons.Filled.Star,
                        contentDescription = null,
                        tint = InaseColors.Star,
                        modifier = Modifier.size(11.dp).align(Alignment.TopEnd),
                    )
                }
            }
        }
    }
}

@Composable
private fun SourceBadge(state: LineupState, modifier: Modifier = Modifier) {
    val text = when (state.source) {
        LineupSource.Bundled -> "Bundled · tap ↻ to refresh"
        is LineupSource.Cached -> if (state.lastError != null)
            "Cached · ${state.lastError}"
        else "Cached · tap ↻ to refresh"
        LineupSource.Fresh -> "Up to date"
    }
    Box(
        modifier
            .clip(RoundedCornerShape(999.dp))
            .background(Color(0x33A78BFA))
            .border(1.dp, Color(0x66A78BFA), RoundedCornerShape(999.dp))
            .padding(horizontal = 10.dp, vertical = 5.dp)
    ) {
        Text(text, color = InaseColors.Accent, fontSize = 10.sp)
    }
}

@Composable
private fun DetailDialog(
    selectedKey: String,
    day: DayKey,
    state: LineupState,
    isFav: Boolean,
    onToggleFav: () -> Unit,
    onDismiss: () -> Unit,
) {
    val parts = selectedKey.split("|")
    val stage = runCatching { StageKey.valueOf(parts[1]) }.getOrNull() ?: return
    val start = parts[2]
    val set = state.lineup[day]?.get(stage)?.firstOrNull { it.s == start } ?: return
    val meta = stageMeta.getValue(stage)

    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0x8C000000))
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .padding(20.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(InaseColors.BgMid)
                .border(1.dp, meta.color, RoundedCornerShape(16.dp))
                .padding(22.dp)
                .clickable(enabled = false) {},
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                Box(Modifier.size(7.dp).clip(CircleShape).background(meta.color))
                Text(meta.label.uppercase(), color = meta.color, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
            Text(set.a, color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
            Text("${set.s} → ${set.e}", color = Color(0xD9FFFFFF), fontSize = 16.sp)
            Text(day.full.uppercase(), color = Color(0x80FFFFFF), fontSize = 11.sp)
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (isFav) InaseColors.Star else Color.Transparent)
                    .border(1.dp, InaseColors.Star, RoundedCornerShape(10.dp))
                    .clickable(onClick = onToggleFav)
                    .padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                Icon(
                    if (isFav) Icons.Filled.Star else Icons.Outlined.StarBorder,
                    contentDescription = null,
                    tint = if (isFav) Color.Black else InaseColors.Star,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    if (isFav) "FAVORI" else "AJOUTER AUX FAVORIS",
                    color = if (isFav) Color.Black else InaseColors.Star,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}
