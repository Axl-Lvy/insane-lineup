package fr.axllvy.insane.ui.friends

import androidx.compose.ui.graphics.Color

/**
 * Hand-picked palette of bright neons that read on the dark background. We do NOT randomize
 * per-user — instead each user-id deterministically maps to one entry so colors stay stable across
 * sessions and across devices.
 */
private val FriendPalette =
    listOf(
        Color(0xFF60A5FA), // sky blue
        Color(0xFF34D399), // emerald
        Color(0xFFFBBF24), // amber
        Color(0xFFF472B6), // pink
        Color(0xFF22D3EE), // cyan
        Color(0xFFA78BFA), // violet (matches accent — careful)
        Color(0xFFFB7185), // rose
        Color(0xFF84CC16), // lime
        Color(0xFFF97316), // orange
        Color(0xFF14B8A6), // teal
    )

/** Stable color for a user id — same id always produces the same color. */
fun friendColor(id: String): Color {
    var h = 0
    for (c in id) h = (h * 31 + c.code) and 0x7FFFFFFF
    return FriendPalette[h % FriendPalette.size]
}
