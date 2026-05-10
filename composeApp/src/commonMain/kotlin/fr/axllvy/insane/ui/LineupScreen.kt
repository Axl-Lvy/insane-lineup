package fr.axllvy.insane.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.axllvy.insane.data.DayKey
import fr.axllvy.insane.data.FavoritesRepository
import fr.axllvy.insane.data.FriendCode
import fr.axllvy.insane.data.FriendsRepository
import fr.axllvy.insane.data.LineupState
import fr.axllvy.insane.data.RefreshOutcome
import fr.axllvy.insane.data.SetEntry
import fr.axllvy.insane.data.StageKey
import fr.axllvy.insane.data.timeToMin
import fr.axllvy.insane.ui.friends.FriendsSheet
import fr.axllvy.insane.ui.friends.FriendsSheetState
import fr.axllvy.insane.ui.friends.QrCodeView
import fr.axllvy.insane.ui.friends.QrScannerSheet
import fr.axllvy.insane.ui.friends.friendColor
import fr.axllvy.insane.resources.Res
import fr.axllvy.insane.resources.logo
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource

private const val DAY_TOTAL_MIN = 16 * 60
private val PX_PER_MIN = 1.6f
private val TIMELINE_HEIGHT = (DAY_TOTAL_MIN * PX_PER_MIN).dp
private val TIME_COL_WIDTH = 38.dp
private val HOUR_COUNT = 17

@Composable
fun LineupScreen(
    state: LineupState,
    favoritesRepo: FavoritesRepository,
    friendsRepo: FriendsRepository,
    onRefresh: suspend () -> RefreshOutcome,
) {
    val scope = rememberCoroutineScope()
    var day by rememberSaveable { mutableStateOf(DayKey.JEU) }
    val favs by favoritesRepo.favorites.collectAsState()
    var favsOnly by rememberSaveable { mutableStateOf(false) }
    var hiddenStages by rememberSaveable { mutableStateOf(setOf<StageKey>()) }
    var selected by remember { mutableStateOf<String?>(null) }

    var showFriends by remember { mutableStateOf(false) }
    var showScanner by remember { mutableStateOf(false) }
    var visibleFriends by rememberSaveable { mutableStateOf(setOf<String>()) }
    var myCode by remember { mutableStateOf<FriendCode?>(null) }

    val friends by friendsRepo.friends.collectAsState()
    val myDisplayName by friendsRepo.myDisplayName.collectAsState()
    val friendFavorites by friendsRepo.friendFavorites.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }

    val triggerRefresh: () -> Unit = {
        if (!state.refreshing) {
            scope.launch {
                val outcome = onRefresh()
                val msg = when (outcome) {
                    RefreshOutcome.Refreshed -> "Lineup refreshed"
                    RefreshOutcome.Offline -> "You're offline"
                    is RefreshOutcome.Error -> "Refresh failed"
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
                onOpenFriends = { showFriends = true },
            )

            Timeline(
                state = state,
                day = day,
                hiddenStages = hiddenStages,
                favs = favs,
                favsOnly = favsOnly,
                visibleFriends = visibleFriends,
                friendFavorites = friendFavorites,
                onSelect = { selected = it },
                onRefresh = triggerRefresh,
                modifier = Modifier.weight(1f),
            )
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp),
            snackbar = { data ->
                Snackbar(
                    snackbarData = data,
                    containerColor = InsaneColors.BgMid,
                    contentColor = InsaneColors.OnBg,
                )
            },
        )

        selected?.let { key ->
            DetailDialog(
                selectedKey = key,
                day = day,
                state = state,
                isFav = key in favs,
                onToggleFav = { scope.launch { favoritesRepo.toggle(key) } },
                onDismiss = { selected = null },
            )
        }

        if (showFriends) {
            FriendsSheet(
                state = FriendsSheetState(
                    myDisplayName = myDisplayName,
                    myCode = myCode,
                    friends = friends,
                    visibleFriendIds = visibleFriends,
                ),
                onClose = { showFriends = false },
                onRotateCode = {
                    val rotated = friendsRepo.rotateCode()
                    if (rotated != null) myCode = rotated
                    rotated
                },
                onRedeem = { code -> friendsRepo.redeem(code) },
                onSetVisibility = { id, visible ->
                    visibleFriends = if (visible) visibleFriends + id else visibleFriends - id
                },
                onUnfriend = { id ->
                    friendsRepo.unfriend(id)
                    visibleFriends = visibleFriends - id
                },
                onSetDisplayName = { name -> friendsRepo.setDisplayName(name) },
                onLaunchScanner = { showScanner = true },
                qrRenderer = { data, sizeDp -> QrCodeView(data = data, sizeDp = sizeDp) },
            )
        }

        if (showScanner) {
            QrScannerSheet(
                onResult = { code ->
                    showScanner = false
                    if (code != null) {
                        scope.launch {
                            val result = friendsRepo.redeem(code)
                            snackbarHostState.showSnackbar(
                                when (result) {
                                    is fr.axllvy.insane.data.RedeemResult.Added ->
                                        "Added ${result.friend.displayName ?: "friend"}"
                                    is fr.axllvy.insane.data.RedeemResult.Failed -> result.message
                                }
                            )
                        }
                    }
                },
                onDismiss = { showScanner = false },
            )
        }
    }
}

@Composable
private fun verticalGradient() = Brush.verticalGradient(
    listOf(InsaneColors.BgTop, InsaneColors.BgMid, InsaneColors.Bg)
)

private val LiveMagenta = Color(0xFFF472B6)

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
    onOpenFriends: () -> Unit,
) {
    val pulse by rememberInfiniteTransition(label = "header-pulse").animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(900),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "live-dot",
    )

    val headerBg = InsaneColors.HeaderBg
    val accent = InsaneColors.Accent
    val gridLine = InsaneColors.GridLine

    Column(
        Modifier
            .fillMaxWidth()
            .drawBehind {
                drawRect(headerBg)
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(accent.copy(alpha = 0.35f), accent.copy(alpha = 0f)),
                        center = Offset(size.width * 0.05f, 0f),
                        radius = size.maxDimension * 0.85f,
                    )
                )
                val gap = 3.dp.toPx()
                var y = 0f
                while (y < size.height) {
                    drawLine(
                        color = gridLine,
                        start = Offset(0f, y),
                        end = Offset(size.width, y),
                        strokeWidth = 1f,
                    )
                    y += gap
                }
                val stripe = 2.dp.toPx()
                drawRect(
                    color = accent,
                    topLeft = Offset(0f, size.height - stripe),
                    size = Size(size.width, stripe),
                )
            }
            .padding(start = 14.dp, end = 12.dp, top = 12.dp, bottom = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // ── Status bar: live indicator + transmission tag + friends + refresh
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .alpha(pulse)
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(LiveMagenta),
            )
            Spacer(Modifier.width(7.dp))
            Text(
                "LIVE · TX.026",
                color = LiveMagenta,
                fontSize = 9.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.8.sp,
            )
            Spacer(Modifier.width(10.dp))
            Box(
                Modifier
                    .height(10.dp)
                    .width(1.dp)
                    .background(InsaneColors.OnBgFaint),
            )
            Spacer(Modifier.width(10.dp))
            Text(
                "//  BEYOND  REALITY",
                color = InsaneColors.OnBgDim,
                fontSize = 9.sp,
                letterSpacing = 1.6.sp,
            )
            Spacer(Modifier.weight(1f))
            HeaderIconButton(
                icon = { tint -> Icon(Icons.Filled.People, contentDescription = "Friends", tint = tint, modifier = Modifier.size(16.dp)) },
                enabled = true,
                onClick = onOpenFriends,
            )
            Spacer(Modifier.width(8.dp))
            HeaderIconButton(
                icon = { tint ->
                    if (state.refreshing) {
                        CircularProgressIndicator(strokeWidth = 1.5.dp, color = tint, modifier = Modifier.size(14.dp))
                    } else {
                        Icon(Icons.Filled.Refresh, contentDescription = "Refresh", tint = tint, modifier = Modifier.size(16.dp))
                    }
                },
                enabled = !state.refreshing,
                onClick = onRefresh,
            )
        }

        // ── Display title: massive italic slab "INSANE" + side metadata
        Row(verticalAlignment = Alignment.Bottom) {
            Image(
                painter = painterResource(Res.drawable.logo),
                contentDescription = null,
                modifier = Modifier.size(54.dp).padding(bottom = 4.dp),
            )
            Spacer(Modifier.width(8.dp))
            val accentSlab = InsaneColors.Accent
            Text(
                "INSANE",
                color = InsaneColors.OnBg,
                fontSize = 46.sp,
                fontWeight = FontWeight.Black,
                fontStyle = FontStyle.Italic,
                letterSpacing = (-1.5).sp,
                modifier = Modifier
                    .drawBehind {
                        val slabH = size.height * 0.18f
                        val slabY = size.height * 0.58f
                        drawRect(
                            color = accentSlab.copy(alpha = 0.30f),
                            topLeft = Offset(-2.dp.toPx(), slabY),
                            size = Size(size.width + 4.dp.toPx(), slabH),
                        )
                    },
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.padding(bottom = 7.dp)) {
                Text(
                    "FESTIVAL",
                    color = LiveMagenta,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 4.sp,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    "✦ ED.026",
                    color = InsaneColors.OnBgDim,
                    fontSize = 10.sp,
                    letterSpacing = 1.5.sp,
                )
            }
        }

        // ── Day selector: ticket-stub blocks with sharp edges
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for (d in DayKey.entries) {
                val active = d == day
                val bg = if (active) InsaneColors.Accent else InsaneColors.TabInactiveBg
                val labelColor = if (active) Color.White else InsaneColors.OnBg
                val dateColor = if (active) Color.White.copy(alpha = 0.78f) else InsaneColors.OnBgDim
                Box(
                    Modifier
                        .weight(1f)
                        .height(56.dp)
                        .background(bg)
                        .border(1.dp, if (active) InsaneColors.Accent else InsaneColors.Border)
                        .clickable { onDayChange(d) },
                ) {
                    if (active) {
                        Box(
                            Modifier
                                .align(Alignment.TopStart)
                                .height(3.dp)
                                .fillMaxWidth()
                                .background(LiveMagenta),
                        )
                    }
                    Column(
                        Modifier
                            .fillMaxSize()
                            .padding(horizontal = 10.dp, vertical = 7.dp),
                        verticalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            d.label.uppercase(),
                            color = labelColor,
                            fontSize = 19.sp,
                            fontWeight = FontWeight.Black,
                            fontStyle = FontStyle.Italic,
                            letterSpacing = 0.5.sp,
                        )
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                d.date,
                                color = dateColor,
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.Medium,
                                letterSpacing = 1.2.sp,
                            )
                            if (active) {
                                Text(
                                    "▸",
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Black,
                                )
                            }
                        }
                    }
                }
            }
        }

        // ── Stage filters (square dot + caps label) + favourites counter
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            StageKey.entries.forEach { s ->
                val meta = stageMeta.getValue(s)
                val hidden = s in hiddenStages
                Row(
                    Modifier
                        .clickable { onToggleStage(s) }
                        .padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    Box(
                        Modifier
                            .size(8.dp)
                            .background(if (hidden) Color.Transparent else meta.color)
                            .border(1.5.dp, if (hidden) meta.color.copy(alpha = 0.45f) else meta.color),
                    )
                    Text(
                        meta.label.uppercase(),
                        color = if (hidden) InsaneColors.OnBgFaint else InsaneColors.OnBg,
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.4.sp,
                    )
                }
            }
            Spacer(Modifier.weight(1f))
            Row(
                Modifier
                    .background(if (favsOnly) InsaneColors.Star else Color.Transparent)
                    .border(1.dp, InsaneColors.Star)
                    .clickable { onToggleFavsOnly() }
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Icon(
                    if (favsOnly) Icons.Filled.Star else Icons.Outlined.StarBorder,
                    contentDescription = null,
                    tint = if (favsOnly) Color.Black else InsaneColors.Star,
                    modifier = Modifier.size(12.dp),
                )
                Text(
                    favCount.toString().padStart(2, '0'),
                    color = if (favsOnly) Color.Black else InsaneColors.Star,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.sp,
                )
            }
        }
    }
}

@Composable
private fun HeaderIconButton(
    icon: @Composable (Color) -> Unit,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val tint = InsaneColors.Accent
    Box(
        Modifier
            .size(34.dp)
            .background(tint.copy(alpha = 0.12f))
            .border(1.dp, tint.copy(alpha = 0.45f))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        icon(tint)
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
    visibleFriends: Set<String>,
    friendFavorites: Map<String, Set<String>>,
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
                        .background(InsaneColors.GridLine)
                )
            }

            // Hour labels
            Box(Modifier.width(TIME_COL_WIDTH).fillMaxHeight()) {
                for (i in 0 until HOUR_COUNT) {
                    val h = (12 + i) % 24
                    Text(
                        text = "${h.toString().padStart(2, '0')}:00",
                        color = InsaneColors.OnBgSubtle,
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
                        visibleFriends = visibleFriends,
                        friendFavorites = friendFavorites,
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
    visibleFriends: Set<String>,
    friendFavorites: Map<String, Set<String>>,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val meta = stageMeta.getValue(stage)
    Box(
        modifier
            .clip(RoundedCornerShape(4.dp))
            .background(InsaneColors.ColumnBg)
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
            val friendsWhoLikeIt = visibleFriends.filter { (friendFavorites[it] ?: emptySet()).contains(key) }

            val hasFriendInterest = friendsWhoLikeIt.isNotEmpty()
            val baseBg = if (isFav) meta.color.copy(alpha = 0.22f)
                         else meta.color.copy(alpha = 0.12f)
            Box(
                Modifier
                    .padding(top = top + 2.dp, start = 2.dp, end = 2.dp)
                    .height(barHeight)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(4.dp))
                    .background(baseBg)
                    .border(
                        width = if (hasFriendInterest) 2.dp else 2.dp,
                        color = if (hasFriendInterest && !dimmed)
                                    friendColor(friendsWhoLikeIt.first())
                                else
                                    meta.color.copy(alpha = if (dimmed) 0.18f else 1f),
                        shape = RoundedCornerShape(topStart = 0.dp, bottomStart = 0.dp, topEnd = 4.dp, bottomEnd = 4.dp),
                    )
                    .clickable { onSelect(key) }
                    .padding(horizontal = 5.dp, vertical = 4.dp),
            ) {
                // Top edge stripe — one segment per visible friend who favorited this set.
                // Reads at a glance ("Camille and Théo are going") without hiding the artist name.
                if (hasFriendInterest) {
                    Row(
                        Modifier
                            .align(Alignment.TopStart)
                            .offset(y = (-4).dp, x = (-5).dp)
                            .fillMaxWidth()
                            .height(3.dp),
                    ) {
                        friendsWhoLikeIt.take(4).forEach { id ->
                            Box(
                                Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .background(friendColor(id))
                            )
                        }
                    }
                }
                Column {
                    Text(
                        set.s,
                        color = InsaneColors.OnBgTimeChip,
                        fontSize = 8.5.sp,
                    )
                    Text(
                        set.a,
                        color = InsaneColors.OnBg.copy(alpha = if (dimmed) 0.18f else 1f),
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
                        tint = InsaneColors.Star,
                        modifier = Modifier.size(11.dp).align(Alignment.TopEnd),
                    )
                }
            }
        }
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
            .background(InsaneColors.DialogScrim)
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .padding(20.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(InsaneColors.BgMid)
                .border(1.dp, meta.color, RoundedCornerShape(16.dp))
                .padding(22.dp)
                .clickable(enabled = false) {},
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                Box(Modifier.size(7.dp).clip(CircleShape).background(meta.color))
                Text(meta.label.uppercase(), color = meta.color, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
            Text(set.a, color = InsaneColors.OnBg, fontSize = 28.sp, fontWeight = FontWeight.Bold)
            Text("${set.s} → ${set.e}", color = InsaneColors.OnBgEmphasis, fontSize = 16.sp)
            Text(day.full.uppercase(), color = InsaneColors.OnBgDim, fontSize = 11.sp)
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (isFav) InsaneColors.Star else Color.Transparent)
                    .border(1.dp, InsaneColors.Star, RoundedCornerShape(10.dp))
                    .clickable(onClick = onToggleFav)
                    .padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                Icon(
                    if (isFav) Icons.Filled.Star else Icons.Outlined.StarBorder,
                    contentDescription = null,
                    tint = if (isFav) Color.Black else InsaneColors.Star,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    if (isFav) "FAVORI" else "AJOUTER AUX FAVORIS",
                    color = if (isFav) Color.Black else InsaneColors.Star,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}
