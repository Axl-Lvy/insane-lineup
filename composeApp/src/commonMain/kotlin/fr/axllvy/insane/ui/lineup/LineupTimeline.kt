package fr.axllvy.insane.ui.lineup

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.axllvy.insane.data.DayKey
import fr.axllvy.insane.data.LineupState
import fr.axllvy.insane.data.SetEntry
import fr.axllvy.insane.data.StageKey
import fr.axllvy.insane.data.timeToMin
import fr.axllvy.insane.ui.InsaneColors
import fr.axllvy.insane.ui.friends.friendColor
import fr.axllvy.insane.ui.stageMeta

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
                .padding(horizontal = 8.dp, vertical = 12.dp),
        ) {
            Box(Modifier.fillMaxWidth().height(TIMELINE_HEIGHT + 24.dp)) {
                HourGrid()
                HourLabels()
                Row(
                    Modifier
                        .padding(start = TIME_COL_WIDTH)
                        .fillMaxWidth()
                        .height(TIMELINE_HEIGHT),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    for (stage in visibleStages) {
                        StageColumn(
                            stage = stage,
                            sets = dayData[stage].orEmpty(),
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
private fun HourGrid() {
    for (i in 0 until HOUR_COUNT) {
        Box(
            Modifier
                .padding(start = TIME_COL_WIDTH, top = (i * 60 * PX_PER_MIN).dp)
                .fillMaxWidth()
                .height(1.dp)
                .background(InsaneColors.GridLine),
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
            .background(InsaneColors.ColumnBg),
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
            val friendsWhoLikeIt = visibleFriends.filter { friendFavorites[it].orEmpty().contains(key) }
            val hasFriendInterest = friendsWhoLikeIt.isNotEmpty()

            val baseBg = meta.color.copy(alpha = if (isFav) 0.22f else 0.12f)
            val borderColor = when {
                hasFriendInterest && !dimmed -> friendColor(friendsWhoLikeIt.first())
                else -> meta.color.copy(alpha = if (dimmed) 0.18f else 1f)
            }
            Box(
                Modifier
                    .padding(top = top + 2.dp, start = 2.dp, end = 2.dp)
                    .height(barHeight)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(4.dp))
                    .background(baseBg)
                    .border(2.dp, borderColor, RoundedCornerShape(4.dp))
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
                                    .background(friendColor(id)),
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
                        fontSize = when {
                            height < 35.dp -> 9.sp
                            height < 60.dp -> 10.sp
                            else -> 11.sp
                        },
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
