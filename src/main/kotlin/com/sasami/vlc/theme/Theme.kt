package com.sasami.vlc.theme

import androidx.compose.material.Typography
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

object Theme {
    // Solid palette sampled from the supplied CineK artwork.
    val Bg = Color(0xFF1B1B1C)
    val Surface = Color(0xFF333336)
    val SurfaceBar = Color(0xFF221F1E)
    val Dark = Color(0xFF000000)
    val Accent = Color(0xFFFCBF1D)
    val AccentHover = Color(0xFFFC980D)
    val AccentHighlight = Color(0xFFFDF575)
    val AccentSoft = Color(0xFF4C3414)
    val AccentGlow = Color(0x4DFCBF1D)
    val TextPrimary = Color(0xF2FFFFFF)
    val TextSecondary = Color(0xA6FFFFFF)
    val TextMuted = Color(0x59FFFFFF)
    val Border = Color(0x14FFFFFF)
    val FillHover = Color(0x1AFFFFFF)
    val Danger = Color(0xFFF95602)


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
        )
    )
}
