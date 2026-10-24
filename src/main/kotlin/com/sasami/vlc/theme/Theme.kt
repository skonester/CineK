package com.sasami.vlc.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.core.tween
import androidx.compose.material.Shapes
import androidx.compose.material.Typography
import androidx.compose.material.darkColors
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.sp

object Theme {
    // Neutrals follow FletBox's dark zinc scale; the gold and orange accents are
    // sampled from the supplied CineK artwork and shared with the Android app.
    val Bg = Color(0xFF09090B)
    val SurfaceBar = Color(0xFF111113)
    // Semantic surfaces keep the desktop Compose UI easy to carry to Android.
    val SurfaceLow = Color(0xFF18181B)
    val Surface = Color(0xFF1F1F23)
    val SurfaceHigh = Color(0xFF27272A)
    val SurfaceFocused = Color(0xFF2E2716)
    val Dark = Color(0xFF000000)
    val Accent = Color(0xFFFCBF1D)
    val AccentHover = Color(0xFFFC980D)
    val AccentHighlight = Color(0xFFFDF575)
    val AccentSoft = Color(0xFF2B2310)
    val AccentGlow = Color(0x4DFCBF1D)
    val TextPrimary = Color(0xFFFAFAFA)
    val TextSecondary = Color(0xFFA1A1AA)
    val TextMuted = Color(0xFF71717A)
    // Hairline borders do the work shadows would: FletBox draws every card,
    // field and menu with a 1px edge and keeps elevation flat.
    val Border = Color(0xFF27272A)
    val BorderStrong = Color(0xFF3F3F46)
    val FillHover = Color(0x14FAFAFA)
    val Danger = Color(0xFFF87171)
    // Translucent panels for Compose-drawn overlays. Not for anything over the
    // VLC surface: it is a native window, so nothing composites through it.
    val Glass = Color(0xB818181B)
    val GlassBorder = Color(0x1FFAFAFA)

    val CardShape = RoundedCornerShape(16.dp)
    val HeroShape = RoundedCornerShape(20.dp)
    val ItemShape = RoundedCornerShape(12.dp)
    val MenuShape = RoundedCornerShape(12.dp)
    val DialogShape = RoundedCornerShape(20.dp)
    val ControlShape = RoundedCornerShape(50)

    /** CSS `ease` over 200 ms, the transition FletBox uses for hover, focus and selection. */
    private val Ease = CubicBezierEasing(0.25f, 0.1f, 0.25f, 1f)
    fun <T> ease(durationMillis: Int = 200): TweenSpec<T> = tween(durationMillis, easing = Ease)

    val colors = darkColors(primary = Accent, primaryVariant = AccentHover, secondary = Accent,
        background = Bg, surface = SurfaceLow, error = Danger,
        onPrimary = Bg, onSecondary = Bg, onBackground = TextPrimary, onSurface = TextPrimary)

    // Buttons, chips and text fields use the small shape, so they all become pills.
    val shapes = Shapes(small = ControlShape, medium = MenuShape, large = DialogShape)

    val typography = Typography(
        body1 = TextStyle(
            fontFamily = FontFamily.SansSerif,
            fontWeight = FontWeight.Normal,
            fontSize = 14.sp,
            color = Color.Unspecified
        ),
        body2 = TextStyle(
            fontFamily = FontFamily.SansSerif,
            fontWeight = FontWeight.Normal,
            fontSize = 12.sp,
            color = TextSecondary
        ),
        h1 = TextStyle(
            fontFamily = FontFamily.SansSerif,
            fontWeight = FontWeight.Bold,
            fontSize = 26.sp,
            color = Color.Unspecified
        ),
        h2 = TextStyle(
            fontFamily = FontFamily.SansSerif,
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp,
            color = Color.Unspecified
        ),
        button = TextStyle(
            fontFamily = FontFamily.SansSerif,
            fontWeight = FontWeight.Medium,
            fontSize = 14.sp,
            letterSpacing = 0.15.sp
        )
    )
}
