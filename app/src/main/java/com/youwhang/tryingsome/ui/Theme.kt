package com.youwhang.tryingsome.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Scheme = darkColorScheme(
    primary = Color(0xFFFCB328),
    onPrimary = Color(0xFF1A1200),
    secondary = Color(0xFF7FB0F0),
    background = Color(0xFF020F23),
    onBackground = Color(0xFFE6ECF5),
    surface = Color(0xFF0B1E3B),
    onSurface = Color(0xFFE6ECF5),
    surfaceVariant = Color(0xFF152F56),
    onSurfaceVariant = Color(0xFFB8C6DB),
    surfaceContainer = Color(0xFF0F2547),
    surfaceContainerHigh = Color(0xFF152F56),
)

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Scheme, content = content)
}

/** e621 태그 카테고리 색 */
fun tagColor(category: Int): Color = when (category) {
    1 -> Color(0xFFF2AC08) // artist
    3 -> Color(0xFFDD00DD) // copyright
    4 -> Color(0xFF00AA00) // character
    5 -> Color(0xFFED5D1F) // species
    6 -> Color(0xFFFF3D3D) // invalid
    7 -> Color(0xFFCCCCCC) // meta
    8 -> Color(0xFF228822) // lore
    else -> Color(0xFFB4C7D9) // general
}

fun formatCount(n: Int): String = when {
    n >= 1_000_000 -> String.format("%.1fM", n / 1_000_000f)
    n >= 1_000 -> String.format("%.1fk", n / 1_000f)
    else -> n.toString()
}
