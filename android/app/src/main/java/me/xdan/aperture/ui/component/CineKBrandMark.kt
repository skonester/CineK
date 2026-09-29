package me.xdan.aperture.ui.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import me.xdan.aperture.R
import me.xdan.aperture.ui.theme.ApertureTheme

/** CineK's artwork, shared by navigation and ambient mode. */
@Composable
fun CineKBrandMark(
    modifier: Modifier = Modifier,
    accent: Color = ApertureTheme.brandAccent,
    spinBlades: Boolean = false,
    spinKey: Any? = null
) {
    val rotation = remember { Animatable(0f) }
    LaunchedEffect(spinBlades, spinKey) {
        if (spinBlades) {
            rotation.snapTo(0f)
            rotation.animateTo(360f, tween(650))
        } else {
            rotation.snapTo(0f)
        }
    }
    Image(
        painter = painterResource(R.drawable.cinek_logo),
        contentDescription = null,
        modifier = modifier.graphicsLayer(rotationZ = rotation.value)
    )
}
