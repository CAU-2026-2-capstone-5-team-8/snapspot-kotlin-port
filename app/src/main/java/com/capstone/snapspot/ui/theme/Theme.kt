package com.capstone.snapspot.ui.theme

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.capstone.snapspot.R

/**
 * "Highlighter on a map": map paper and ink, park green for actions,
 * and highlighter yellow reserved for one meaning only — "you are near this place".
 */
object SnapColor {
    val Paper = Color(0xFFF2F3EE)
    val Surface = Color(0xFFFFFFFF)
    val Ink = Color(0xFF1F2B2A)
    // Muted and Green were darkened a little so small text passes 4.5:1 on every background it sits on (ContrastTest).
    val Muted = Color(0xFF636D6B)
    val Line = Color(0xFFD9DED6)
    val Green = Color(0xFF2B7453)
    val GreenSoft = Color(0xFFE3EFE7)
    val Highlight = Color(0xFFFFE34D)
    val Amber = Color(0xFF8A5A0A)
    val AmberSoft = Color(0xFFFBF0D4)
}

object SnapFont {
    val Display = FontFamily(Font(R.font.black_han_sans, FontWeight.Normal))
    val Plex = FontFamily(
        Font(R.font.plex_kr_regular, FontWeight.Normal),
        Font(R.font.plex_kr_medium, FontWeight.Medium),
        Font(R.font.plex_kr_bold, FontWeight.Bold),
    )
}

/** Type scale on a 4px grid. */
object SnapType {
    val Hero = TextStyle(fontFamily = SnapFont.Display, fontSize = 40.sp, lineHeight = 46.sp, color = SnapColor.Ink)
    val Title = TextStyle(fontFamily = SnapFont.Plex, fontWeight = FontWeight.Bold, fontSize = 18.sp, lineHeight = 26.sp, color = SnapColor.Ink)
    val Body = TextStyle(fontFamily = SnapFont.Plex, fontWeight = FontWeight.Normal, fontSize = 15.sp, lineHeight = 22.sp, color = SnapColor.Ink)
    val Meta = TextStyle(fontFamily = SnapFont.Plex, fontWeight = FontWeight.Normal, fontSize = 13.sp, lineHeight = 18.sp, color = SnapColor.Muted)
    val Label = TextStyle(fontFamily = SnapFont.Plex, fontWeight = FontWeight.Medium, fontSize = 13.sp, lineHeight = 18.sp, color = SnapColor.Muted)
    val Distance = TextStyle(fontFamily = SnapFont.Display, fontSize = 22.sp, lineHeight = 26.sp, color = SnapColor.Ink)
}

object Space {
    val Xs = 4.dp
    val S = 8.dp
    val M = 12.dp
    val L = 16.dp
    val Xl = 24.dp
    val Xxl = 32.dp
    fun all(v: androidx.compose.ui.unit.Dp) = PaddingValues(v)
}

private val Scheme = lightColorScheme(
    primary = SnapColor.Green,
    onPrimary = Color.White,
    primaryContainer = SnapColor.GreenSoft,
    onPrimaryContainer = SnapColor.Ink,
    secondary = SnapColor.Highlight,
    onSecondary = SnapColor.Ink,
    background = SnapColor.Paper,
    onBackground = SnapColor.Ink,
    surface = SnapColor.Surface,
    onSurface = SnapColor.Ink,
    surfaceVariant = SnapColor.GreenSoft,
    onSurfaceVariant = SnapColor.Muted,
    outline = SnapColor.Line,
)

/** Light only, like the RN app. Also tells every screen whether the user turned animations off. */
@Composable
fun SnapSpotTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    var reduceMotion by remember { mutableStateOf(readReduceMotion(context)) }
    // The setting is changed outside the app, so read it again whenever the app comes back to the front.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { reduceMotion = readReduceMotion(context) }
    CompositionLocalProvider(LocalReduceMotion provides reduceMotion) {
        MaterialTheme(colorScheme = Scheme, content = content)
    }
}
