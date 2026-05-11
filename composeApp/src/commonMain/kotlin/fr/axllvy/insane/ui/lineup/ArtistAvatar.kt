package fr.axllvy.insane.ui.lineup

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import fr.axllvy.insane.data.LocalArtistImages
import fr.axllvy.insane.logE
import fr.axllvy.insane.ui.InsaneColors

/**
 * Circular avatar for a single artist. Falls back to the artist's first letter on a tinted disk
 * when no image is registered. Tapping opens the artist's SoundCloud page if we have one.
 *
 * Sized via [size] so the same composable handles the solo (larger) and b2b/f2f (paired, smaller)
 * layouts.
 */
@Composable
fun ArtistAvatar(name: String, size: Dp, borderColor: Color) {
    val images = LocalArtistImages.current
    val imageUrl = images.urlFor(name)
    val externalUrl = images.soundcloudUrlFor(name)
    val uriHandler = LocalUriHandler.current
    val onClick: (() -> Unit)? =
        externalUrl?.let {
            {
                runCatching { uriHandler.openUri(it) }
                    .onFailure { e -> logE("openUri($externalUrl) failed: ${e.message}") }
            }
        }
    val base =
        Modifier.size(size)
            .clip(CircleShape)
            .background(InsaneColors.BgTop)
            .border(1.dp, borderColor, CircleShape)
    val clickable = if (onClick != null) base.clickable(onClick = onClick) else base
    Box(clickable, contentAlignment = Alignment.Center) {
        if (imageUrl != null) {
            AsyncImage(
                model = imageUrl,
                contentDescription = null,
                modifier = Modifier.size(size).clip(CircleShape),
            )
        } else {
            // First grapheme cluster, uppercased — covers "[Ivy]" → "[", "Évan" → "É".
            val letter = name.firstOrNull()?.uppercaseChar()?.toString() ?: "?"
            Text(
                text = letter,
                color = InsaneColors.OnBgEmphasis,
                fontSize = (size.value * 0.42f).sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

/**
 * Lays out one or two artist avatars for a set. With two artists they sit side by side, each
 * labeled — useful for b2b / f2f sets where the audience cares which of the pair is which.
 */
@Composable
fun ArtistAvatarRow(artists: List<String>, accent: Color) {
    if (artists.isEmpty()) return
    when (artists.size) {
        1 ->
            Box(Modifier.size(96.dp), contentAlignment = Alignment.Center) {
                ArtistAvatar(name = artists[0], size = 96.dp, borderColor = accent)
            }
        else ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.Top,
            ) {
                artists.take(3).forEach { name ->
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.width(80.dp),
                    ) {
                        ArtistAvatar(name = name, size = 72.dp, borderColor = accent)
                        Text(
                            text = name,
                            color = InsaneColors.OnBgEmphasis,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
            }
    }
}
