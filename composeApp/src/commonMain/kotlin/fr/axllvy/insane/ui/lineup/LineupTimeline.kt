package fr.axllvy.insane.ui.lineup

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.sp
import fr.axllvy.insane.data.DayKey
import fr.axllvy.insane.data.LineupState
import fr.axllvy.insane.data.SetEntry
import fr.axllvy.insane.data.StageKey
import fr.axllvy.insane.data.timeToMin
import fr.axllvy.insane.ui.InsaneColors
import fr.axllvy.insane.ui.friends.friendColor
import fr.axllvy.insane.ui.stageMeta
import kotlinx.coroutines.delay

private const val DAY_TOTAL_MIN = 16 * 60
private const val PX_PER_MIN = 1.6f
private const val HOUR_COUNT = 17
private const val FIRST_HOUR = 12
private val TIMELINE_HEIGHT = (DAY_TOTAL_MIN * PX_PER_MIN).dp
private val TIME_COL_WIDTH = 38.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun Timeline(
    state: LineupState,
    day: DayKey,
    hiddenStages: Set<StageKey>,
    favs: Set<String>,
    favCounts: Map<String, Int>,
    favsOnly: Boolean,
    visibleFriends: Set<String>,
    friendFavorites: Map<String, Set<String>>,
    onSelect: (String) -> Unit,
    onRefresh: () -> Unit,
    highlightKey: String? = null,
    modifier: Modifier = Modifier,
) {
    val visibleStages = StageKey.entries.filterNot { it in hiddenStages }
    val dayData = state.lineup[day].orEmpty()
    val scrollState = rememberScrollState()
    val density = LocalDensity.current

    LaunchedEffect(highlightKey, day) {
        val k = highlightKey ?: return@LaunchedEffect
        val parts = k.split("|", limit = 4)
        if (parts.size < 3 || parts[0] != day.id) return@LaunchedEffect
        val stage = runCatching { StageKey.valueOf(parts[1]) }.getOrNull() ?: return@LaunchedEffect
        val start = parts[2]
        val set = dayData[stage]?.firstOrNull { it.s == start } ?: return@LaunchedEffect
        val targetDp = (timeToMin(set.s) * PX_PER_MIN).dp - 24.dp
        val target = with(density) { targetDp.toPx() }.toInt().coerceAtLeast(0)
        scrollState.animateScrollTo(target)
    }

    PullToRefreshBox(
        isRefreshing = state.refreshing,
        onRefresh = onRefresh,
        modifier = modifier.fillMaxWidth(),
    ) {
        Box(
            Modifier.fillMaxWidth()
                .verticalScroll(scrollState)
                .padding(horizontal = 8.dp, vertical = 12.dp)
        ) {
            Box(Modifier.fillMaxWidth().height(TIMELINE_HEIGHT + 24.dp)) {
                HourGrid()
                HourLabels()
                Row(
                    Modifier.padding(start = TIME_COL_WIDTH).fillMaxWidth().height(TIMELINE_HEIGHT),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    for (stage in visibleStages) {
                        StageColumn(
                            stage = stage,
                            sets = dayData[stage].orEmpty(),
                            day = day,
                            favs = favs,
                            favCounts = favCounts,
                            favsOnly = favsOnly,
                            visibleFriends = visibleFriends,
                            friendFavorites = friendFavorites,
                            onSelect = onSelect,
                            highlightKey = highlightKey,
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HourGrid() {
    for (i in 0 until HOUR_COUNT) {
        Box(
            Modifier.padding(start = TIME_COL_WIDTH, top = (i * 60 * PX_PER_MIN).dp)
                .fillMaxWidth()
                .height(1.dp)
                .background(InsaneColors.GridLine)
        )
    }
}

@Composable
private fun HourLabels() {
    Box(Modifier.width(TIME_COL_WIDTH).fillMaxHeight()) {
        for (i in 0 until HOUR_COUNT) {
            val h = (FIRST_HOUR + i) % 24
            Text(
                text = "${h.toString().padStart(2, '0')}:00",
                color = InsaneColors.OnBgSubtle,
                fontSize = 9.5.sp,
                modifier = Modifier.offset(y = (i * 60 * PX_PER_MIN - 7).dp),
            )
        }
    }
}

@Composable
private fun StageColumn(
    stage: StageKey,
    sets: List<SetEntry>,
    day: DayKey,
    favs: Set<String>,
    favCounts: Map<String, Int>,
    favsOnly: Boolean,
    visibleFriends: Set<String>,
    friendFavorites: Map<String, Set<String>>,
    onSelect: (String) -> Unit,
    highlightKey: String?,
    modifier: Modifier = Modifier,
) {
    val meta = stageMeta.getValue(stage)
    Box(modifier.clip(RoundedCornerShape(4.dp)).background(InsaneColors.ColumnBg)) {
        sets.forEach { set ->
            val durationMin = timeToMin(set.e) - timeToMin(set.s)
            if (durationMin <= 0) return@forEach
            val top = (timeToMin(set.s) * PX_PER_MIN).dp.coerceAtLeast(0.dp)
            val height = (durationMin * PX_PER_MIN).dp
            val barHeight = (height - 4.dp).coerceAtLeast(0.dp)
            val key = "${day.id}|${stage.name}|${set.s}|${set.a}"
            val isFav = key in favs
            val dimmed = favsOnly && !isFav
            val friendsWhoLikeIt = visibleFriends.filter {
                friendFavorites[it].orEmpty().contains(key)
            }
            val hasFriendInterest = friendsWhoLikeIt.isNotEmpty()
            val favCount = favCounts[key] ?: 0

            val isHighlight = key == highlightKey
            val normalBg = meta.color.copy(alpha = if (isFav) 0.22f else 0.12f)
            val normalBorder = meta.color.copy(alpha = if (dimmed) 0.18f else 1f)
            val starColor = InsaneColors.Star
            val highlightBg = starColor.copy(alpha = 0.30f)
            val highlightAnim = remember { Animatable(0f) }
            LaunchedEffect(highlightKey) {
                if (!isHighlight) {
                    highlightAnim.snapTo(0f)
                    return@LaunchedEffect
                }
                highlightAnim.snapTo(1f)
                repeat(4) {
                    highlightAnim.animateTo(0.25f, tween(170))
                    highlightAnim.animateTo(1f, tween(170))
                }
                delay(2000)
                highlightAnim.animateTo(0f, tween(1000))
            }
            val t = highlightAnim.value
            val borderColor = if (t > 0f) lerp(normalBorder, starColor, t) else normalBorder
            val borderWidth = if (t > 0f) lerp(2.dp, 3.dp, t) else 2.dp
            val baseBg = if (t > 0f) lerp(normalBg, highlightBg, t) else normalBg
            Box(
                Modifier.padding(top = top + 2.dp, start = 2.dp, end = 2.dp)
                    .height(barHeight)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(4.dp))
                    .background(baseBg)
                    .border(borderWidth, borderColor, RoundedCornerShape(4.dp))
                    .clickable { onSelect(key) }
                    .padding(horizontal = 5.dp, vertical = 4.dp)
            ) {
                Column {
                    Text(set.s, color = InsaneColors.OnBgTimeChip, fontSize = 8.5.sp)
                    Text(
                        set.a,
                        color = InsaneColors.OnBg.copy(alpha = if (dimmed) 0.18f else 1f),
                        fontSize =
                            when {
                                height < 35.dp -> 9.sp
                                height < 60.dp -> 10.sp
                                else -> 11.sp
                            },
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (isFav || favCount > 0) {
                    Row(
                        modifier = Modifier.align(Alignment.TopEnd),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                    ) {
                        if (favCount > 0) {
                            Text(
                                favCount.toString(),
                                color = InsaneColors.OnBgDim.copy(alpha = if (dimmed) 0.3f else 1f),
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                        if (isFav) {
                            Icon(
                                Icons.Filled.Star,
                                contentDescription = null,
                                tint = InsaneColors.Star,
                                modifier = Modifier.size(11.dp),
                            )
                        }
                    }
                }
                if (hasFriendInterest && !dimmed) {
                    Row(
                        modifier = Modifier.align(Alignment.BottomStart),
                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                    ) {
                        friendsWhoLikeIt.take(4).forEach { id ->
                            Box(Modifier.size(6.dp).clip(CircleShape).background(friendColor(id)))
                        }
                    }
                }
            }
        }
    }
}
