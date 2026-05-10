package fr.axllvy.insane.ui.lineup

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.axllvy.insane.data.DayKey
import fr.axllvy.insane.data.LineupState
import fr.axllvy.insane.data.StageKey
import fr.axllvy.insane.resources.Res
import fr.axllvy.insane.resources.detail_add_to_favorites
import fr.axllvy.insane.resources.detail_fav_count_none
import fr.axllvy.insane.resources.detail_fav_count_one
import fr.axllvy.insane.resources.detail_fav_count_other
import fr.axllvy.insane.resources.detail_favorite_active
import fr.axllvy.insane.ui.InsaneColors
import fr.axllvy.insane.ui.dayFullLabel
import fr.axllvy.insane.ui.stageMeta
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun DetailDialog(
    selectedKey: String,
    day: DayKey,
    state: LineupState,
    isFav: Boolean,
    favCount: Int,
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
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                Box(Modifier.size(7.dp).clip(CircleShape).background(meta.color))
                Text(meta.label.uppercase(), color = meta.color, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
            Text(set.a, color = InsaneColors.OnBg, fontSize = 28.sp, fontWeight = FontWeight.Bold)
            Text("${set.s} → ${set.e}", color = InsaneColors.OnBgEmphasis, fontSize = 16.sp)
            Text(dayFullLabel(day).uppercase(), color = InsaneColors.OnBgDim, fontSize = 11.sp)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(
                    Icons.Filled.Star,
                    contentDescription = null,
                    tint = if (favCount > 0) InsaneColors.Star else InsaneColors.OnBgFaint,
                    modifier = Modifier.size(14.dp),
                )
                Text(
                    text = when (favCount) {
                        0 -> stringResource(Res.string.detail_fav_count_none)
                        1 -> stringResource(Res.string.detail_fav_count_one)
                        else -> stringResource(Res.string.detail_fav_count_other, favCount)
                    },
                    color = InsaneColors.OnBgEmphasis,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
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
                    stringResource(if (isFav) Res.string.detail_favorite_active else Res.string.detail_add_to_favorites),
                    color = if (isFav) Color.Black else InsaneColors.Star,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}
