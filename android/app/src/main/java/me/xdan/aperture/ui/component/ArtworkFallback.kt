package me.xdan.aperture.ui.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import me.xdan.aperture.ui.theme.ApertureTheme

@Composable
fun ArtworkFallback(
    title: String,
    isFocused: Boolean,
    modifier: Modifier = Modifier,
    titleColor: Color? = null
) {
    val motion = ApertureTheme.motion
    val containerColor = animateColorAsState(
        targetValue = if (isFocused) {
            Color(0xFFFCBF1D)
        } else {
            Color(0xFF272524)
        },
        animationSpec = if (isFocused) {
            motion.focusGlowEnter<Color>()
        } else {
            motion.focusGlowExit<Color>()
        },
        label = "fallbackArtworkContainer"
    ).value
    val animatedTitleColor = animateColorAsState(
        targetValue = titleColor ?: if (isFocused) {
            Color(0xFF1B1B1C)
        } else {
            Color(0xFFF2F2F2)
        },
        animationSpec = if (isFocused) {
            motion.focusGlowEnter<Color>()
        } else {
            motion.focusGlowExit<Color>()
        },
        label = "fallbackArtworkTitle"
    ).value

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(containerColor)
            .border(
                width = 1.dp,
                color = if (isFocused) Color(0xFFFDF575) else Color(0xFF3D3935),
                shape = ApertureTheme.shapes.poster
            )
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = title,
            color = animatedTitleColor,
            style = ApertureTheme.typography.mediaTitle,
            textAlign = TextAlign.Center,
            maxLines = 5,
            overflow = TextOverflow.Ellipsis
        )
    }
}
