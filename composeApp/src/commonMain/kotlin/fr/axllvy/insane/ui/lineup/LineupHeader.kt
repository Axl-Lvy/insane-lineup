package fr.axllvy.insane.ui.lineup

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.axllvy.insane.data.DayKey
import fr.axllvy.insane.data.LineupState
import fr.axllvy.insane.data.StageKey
import fr.axllvy.insane.resources.Res
import fr.axllvy.insane.resources.cd_friends
import fr.axllvy.insane.resources.cd_notifications
import fr.axllvy.insane.resources.cd_refresh
import fr.axllvy.insane.resources.cd_search
import fr.axllvy.insane.resources.logo
import fr.axllvy.insane.ui.InsaneColors
import fr.axllvy.insane.ui.dayDateLabel
import fr.axllvy.insane.ui.dayShortLabel
import fr.axllvy.insane.ui.stageMeta
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

private val LiveMagenta = Color(0xFFF472B6)

@Composable
internal fun Header(
    state: LineupState,
    day: DayKey,
    onDayChange: (DayKey) -> Unit,
    hiddenStages: Set<StageKey>,
    onToggleStage: (StageKey) -> Unit,
    favsOnly: Boolean,
    onToggleFavsOnly: () -> Unit,
    favCount: Int,
    notificationsEnabled: Boolean,
    onToggleNotifications: () -> Unit,
    onRefresh: () -> Unit,
    onOpenFriends: () -> Unit,
    onOpenSearch: () -> Unit,
) {
    val headerBg = InsaneColors.HeaderBg
    val accent = InsaneColors.Accent
    val gridLine = InsaneColors.GridLine
    Column(
        Modifier
            .fillMaxWidth()
            .drawBehind { drawHeaderBackground(headerBg, accent, gridLine) }
            .padding(start = 14.dp, end = 12.dp, top = 12.dp, bottom = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        StatusBar(
            refreshing = state.refreshing,
            notificationsEnabled = notificationsEnabled,
            onToggleNotifications = onToggleNotifications,
            onRefresh = onRefresh,
            onOpenFriends = onOpenFriends,
            onOpenSearch = onOpenSearch,
        )
        TitleRow()
        DaySelector(day = day, onDayChange = onDayChange)
        StageFilters(
            hiddenStages = hiddenStages,
            onToggleStage = onToggleStage,
            favsOnly = favsOnly,
            onToggleFavsOnly = onToggleFavsOnly,
            favCount = favCount,
        )
    }
}

private fun DrawScope.drawHeaderBackground(
    headerBg: Color,
    accent: Color,
    gridLine: Color,
) {
    drawRect(headerBg)
    drawRect(
        brush = Brush.radialGradient(
            colors = listOf(accent.copy(alpha = 0.35f), accent.copy(alpha = 0f)),
            center = Offset(size.width * 0.05f, 0f),
            radius = size.maxDimension * 0.85f,
        ),
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

@Composable
private fun StatusBar(
    refreshing: Boolean,
    notificationsEnabled: Boolean,
    onToggleNotifications: () -> Unit,
    onRefresh: () -> Unit,
    onOpenFriends: () -> Unit,
    onOpenSearch: () -> Unit,
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
        val friendsCd = stringResource(Res.string.cd_friends)
        val refreshCd = stringResource(Res.string.cd_refresh)
        val notificationsCd = stringResource(Res.string.cd_notifications)
        val searchCd = stringResource(Res.string.cd_search)
        HeaderIconButton(
            icon = { tint ->
                Icon(
                    if (notificationsEnabled) Icons.Filled.Notifications else Icons.Outlined.NotificationsNone,
                    contentDescription = notificationsCd,
                    tint = tint,
                    modifier = Modifier.size(16.dp),
                )
            },
            enabled = true,
            active = notificationsEnabled,
            onClick = onToggleNotifications,
        )
        Spacer(Modifier.width(8.dp))
        HeaderIconButton(
            icon = { tint -> Icon(Icons.Filled.Search, contentDescription = searchCd, tint = tint, modifier = Modifier.size(16.dp)) },
            enabled = true,
            onClick = onOpenSearch,
        )
        Spacer(Modifier.width(8.dp))
        HeaderIconButton(
            icon = { tint -> Icon(Icons.Filled.People, contentDescription = friendsCd, tint = tint, modifier = Modifier.size(16.dp)) },
            enabled = true,
            onClick = onOpenFriends,
        )
        Spacer(Modifier.width(8.dp))
        HeaderIconButton(
            icon = { tint ->
                if (refreshing) {
                    CircularProgressIndicator(strokeWidth = 1.5.dp, color = tint, modifier = Modifier.size(14.dp))
                } else {
                    Icon(Icons.Filled.Refresh, contentDescription = refreshCd, tint = tint, modifier = Modifier.size(16.dp))
                }
            },
            enabled = !refreshing,
            onClick = onRefresh,
        )
    }
}

@Composable
private fun TitleRow() {
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
            modifier = Modifier.drawBehind {
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
}

@Composable
private fun DaySelector(day: DayKey, onDayChange: (DayKey) -> Unit) {
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
                        dayShortLabel(d).uppercase(),
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
                            dayDateLabel(d),
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
}

@Composable
private fun StageFilters(
    hiddenStages: Set<StageKey>,
    onToggleStage: (StageKey) -> Unit,
    favsOnly: Boolean,
    onToggleFavsOnly: () -> Unit,
    favCount: Int,
) {
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

@Composable
private fun HeaderIconButton(
    icon: @Composable (Color) -> Unit,
    enabled: Boolean,
    onClick: () -> Unit,
    active: Boolean = false,
) {
    val tint = InsaneColors.Accent
    Box(
        Modifier
            .size(34.dp)
            .background(tint.copy(alpha = if (active) 0.32f else 0.12f))
            .border(1.dp, tint.copy(alpha = if (active) 0.85f else 0.45f))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        icon(tint)
    }
}
