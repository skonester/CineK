package me.xdan.aperture.ui.component.touch

import android.view.ViewConfiguration
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitHorizontalTouchSlopOrCancellation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.horizontalDrag
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.ButtonBorder
import androidx.tv.material3.ButtonColors
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.ButtonGlow
import androidx.tv.material3.ButtonScale
import androidx.tv.material3.ButtonShape
import androidx.tv.material3.ClickableSurfaceBorder
import androidx.tv.material3.ClickableSurfaceColors
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ClickableSurfaceGlow
import androidx.tv.material3.ClickableSurfaceScale
import androidx.tv.material3.ClickableSurfaceShape
import androidx.tv.material3.Glow
import androidx.tv.material3.IconButtonDefaults
import androidx.tv.material3.NavigationDrawerItemBorder
import androidx.tv.material3.NavigationDrawerItemColors
import androidx.tv.material3.NavigationDrawerItemDefaults
import androidx.tv.material3.NavigationDrawerItemGlow
import androidx.tv.material3.NavigationDrawerItemScale
import androidx.tv.material3.NavigationDrawerItemShape
import androidx.tv.material3.NavigationDrawerScope
import androidx.tv.material3.OutlinedButtonDefaults
import androidx.tv.material3.SurfaceColors
import androidx.tv.material3.SurfaceDefaults
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.tv.material3.Button as TvButton
import androidx.tv.material3.IconButton as TvIconButton
import androidx.tv.material3.NavigationDrawerItem as TvNavigationDrawerItem
import androidx.tv.material3.OutlinedButton as TvOutlinedButton
import androidx.tv.material3.Surface as TvSurface

/*
 * androidx.tv.material3 surfaces and buttons only react to D-pad Enter: their
 * clickable path is focusable() plus key handling, with no pointer input, so on a
 * phone every tap is ignored. The composables below have the same signatures as
 * the TV ones and delegate to them, keeping the remote focus visuals, and add tap
 * and long-press handling. Import them in place of the androidx.tv.material3 names.
 */

private val TapIndicationDelayMs = ViewConfiguration.getTapTimeout().toLong()

/**
 * Taps and long presses for TV controls. Presses are reported to [interactionSource]
 * so the control's pressed scale and colours animate the same way they do for the
 * remote's select key.
 */
@Composable
fun Modifier.touchClickable(
    interactionSource: MutableInteractionSource,
    enabled: Boolean = true,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit
): Modifier {
    val currentOnClick by rememberUpdatedState(onClick)
    val currentOnLongClick by rememberUpdatedState(onLongClick)
    if (!enabled) return this
    val hasLongClick = onLongClick != null

    return pointerInput(interactionSource, hasLongClick) {
        detectTapGestures(
            onPress = { offset ->
                coroutineScope {
                    // Delay the pressed state like Modifier.clickable so scrolling a
                    // shelf doesn't flash every card under the finger.
                    var press: PressInteraction.Press? = null
                    val delayedPress = launch {
                        delay(TapIndicationDelayMs)
                        press = PressInteraction.Press(offset).also { interactionSource.emit(it) }
                    }
                    val released = tryAwaitRelease()
                    delayedPress.cancel()
                    val shownPress = press
                    when {
                        shownPress != null && released -> interactionSource.emit(PressInteraction.Release(shownPress))
                        shownPress != null -> interactionSource.emit(PressInteraction.Cancel(shownPress))
                        released -> {
                            val quickPress = PressInteraction.Press(offset)
                            interactionSource.emit(quickPress)
                            interactionSource.emit(PressInteraction.Release(quickPress))
                        }
                    }
                }
            },
            onLongPress = if (hasLongClick) {
                { currentOnLongClick?.invoke() }
            } else {
                null
            },
            onTap = { currentOnClick() }
        )
    }
}

/**
 * Horizontal drag and tap-to-position for sliders and seek bars that otherwise only
 * respond to D-pad left/right. [onSeek] receives the touch position as a fraction of
 * the width. A gesture that turns vertical first is left to the enclosing scroller.
 */
@Composable
fun Modifier.touchSeek(
    enabled: Boolean = true,
    onSeekStart: () -> Unit = {},
    onSeekEnd: () -> Unit = {},
    onSeek: (fraction: Float) -> Unit
): Modifier {
    val currentOnSeekStart by rememberUpdatedState(onSeekStart)
    val currentOnSeekEnd by rememberUpdatedState(onSeekEnd)
    val currentOnSeek by rememberUpdatedState(onSeek)
    if (!enabled) return this

    return pointerInput(Unit) {
        fun fractionAt(x: Float) = if (size.width > 0) (x / size.width).coerceIn(0f, 1f) else 0f

        awaitEachGesture {
            val down = awaitFirstDown()
            val dragStart = awaitHorizontalTouchSlopOrCancellation(down.id) { change, _ -> change.consume() }
            if (dragStart != null) {
                currentOnSeekStart()
                currentOnSeek(fractionAt(dragStart.position.x))
                horizontalDrag(dragStart.id) { change ->
                    currentOnSeek(fractionAt(change.position.x))
                    change.consume()
                }
                currentOnSeekEnd()
            } else {
                val up = currentEvent.changes.firstOrNull { it.id == down.id }
                if (up != null && up.changedToUp() && !up.isConsumed) {
                    up.consume()
                    currentOnSeekStart()
                    currentOnSeek(fractionAt(down.position.x))
                    currentOnSeekEnd()
                }
            }
        }
    }
}

@Composable
fun Surface(
    modifier: Modifier = Modifier,
    tonalElevation: Dp = 0.dp,
    shape: Shape = SurfaceDefaults.shape,
    colors: SurfaceColors = SurfaceDefaults.colors(),
    border: Border = Border.None,
    glow: Glow = Glow.None,
    content: @Composable BoxScope.() -> Unit
) {
    TvSurface(
        modifier = modifier,
        tonalElevation = tonalElevation,
        shape = shape,
        colors = colors,
        border = border,
        glow = glow,
        content = content
    )
}

@Composable
fun Surface(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    tonalElevation: Dp = 0.dp,
    shape: ClickableSurfaceShape = ClickableSurfaceDefaults.shape(),
    colors: ClickableSurfaceColors = ClickableSurfaceDefaults.colors(),
    scale: ClickableSurfaceScale = ClickableSurfaceDefaults.scale(),
    border: ClickableSurfaceBorder = ClickableSurfaceDefaults.border(),
    glow: ClickableSurfaceGlow = ClickableSurfaceDefaults.glow(),
    interactionSource: MutableInteractionSource? = null,
    content: @Composable BoxScope.() -> Unit
) {
    val source = interactionSource ?: remember { MutableInteractionSource() }
    TvSurface(
        onClick = onClick,
        modifier = modifier.touchClickable(source, enabled, onLongClick, onClick),
        onLongClick = onLongClick,
        enabled = enabled,
        tonalElevation = tonalElevation,
        shape = shape,
        colors = colors,
        scale = scale,
        border = border,
        glow = glow,
        interactionSource = source,
        content = content
    )
}

@Composable
fun Button(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    scale: ButtonScale = ButtonDefaults.scale(),
    glow: ButtonGlow = ButtonDefaults.glow(),
    shape: ButtonShape = ButtonDefaults.shape(),
    colors: ButtonColors = ButtonDefaults.colors(),
    tonalElevation: Dp = 0.dp,
    border: ButtonBorder = ButtonDefaults.border(),
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    interactionSource: MutableInteractionSource? = null,
    content: @Composable RowScope.() -> Unit
) {
    val source = interactionSource ?: remember { MutableInteractionSource() }
    TvButton(
        onClick = onClick,
        modifier = modifier.touchClickable(source, enabled, onLongClick, onClick),
        onLongClick = onLongClick,
        enabled = enabled,
        scale = scale,
        glow = glow,
        shape = shape,
        colors = colors,
        tonalElevation = tonalElevation,
        border = border,
        contentPadding = contentPadding,
        interactionSource = source,
        content = content
    )
}

@Composable
fun OutlinedButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    scale: ButtonScale = OutlinedButtonDefaults.scale(),
    glow: ButtonGlow = OutlinedButtonDefaults.glow(),
    shape: ButtonShape = OutlinedButtonDefaults.shape(),
    colors: ButtonColors = OutlinedButtonDefaults.colors(),
    tonalElevation: Dp = 0.dp,
    border: ButtonBorder = OutlinedButtonDefaults.border(),
    contentPadding: PaddingValues = OutlinedButtonDefaults.ContentPadding,
    interactionSource: MutableInteractionSource? = null,
    content: @Composable RowScope.() -> Unit
) {
    val source = interactionSource ?: remember { MutableInteractionSource() }
    TvOutlinedButton(
        onClick = onClick,
        modifier = modifier.touchClickable(source, enabled, onLongClick, onClick),
        onLongClick = onLongClick,
        enabled = enabled,
        scale = scale,
        glow = glow,
        shape = shape,
        colors = colors,
        tonalElevation = tonalElevation,
        border = border,
        contentPadding = contentPadding,
        interactionSource = source,
        content = content
    )
}

@Composable
fun IconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    scale: ButtonScale = IconButtonDefaults.scale(),
    glow: ButtonGlow = IconButtonDefaults.glow(),
    shape: ButtonShape = IconButtonDefaults.shape(),
    colors: ButtonColors = IconButtonDefaults.colors(),
    border: ButtonBorder = IconButtonDefaults.border(),
    interactionSource: MutableInteractionSource? = null,
    content: @Composable BoxScope.() -> Unit
) {
    val source = interactionSource ?: remember { MutableInteractionSource() }
    TvIconButton(
        onClick = onClick,
        modifier = modifier.touchClickable(source, enabled, onLongClick, onClick),
        onLongClick = onLongClick,
        enabled = enabled,
        scale = scale,
        glow = glow,
        shape = shape,
        colors = colors,
        border = border,
        interactionSource = source,
        content = content
    )
}

@Composable
fun NavigationDrawerScope.NavigationDrawerItem(
    selected: Boolean,
    onClick: () -> Unit,
    leadingContent: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onLongClick: (() -> Unit)? = null,
    supportingContent: (@Composable () -> Unit)? = null,
    trailingContent: (@Composable () -> Unit)? = null,
    tonalElevation: Dp = NavigationDrawerItemDefaults.NavigationDrawerItemElevation,
    shape: NavigationDrawerItemShape = NavigationDrawerItemDefaults.shape(),
    colors: NavigationDrawerItemColors = NavigationDrawerItemDefaults.colors(),
    scale: NavigationDrawerItemScale = NavigationDrawerItemScale.None,
    border: NavigationDrawerItemBorder = NavigationDrawerItemDefaults.border(),
    glow: NavigationDrawerItemGlow = NavigationDrawerItemDefaults.glow(),
    interactionSource: MutableInteractionSource? = null,
    content: @Composable () -> Unit
) {
    val source = interactionSource ?: remember { MutableInteractionSource() }
    TvNavigationDrawerItem(
        selected = selected,
        onClick = onClick,
        leadingContent = leadingContent,
        modifier = modifier.touchClickable(source, enabled, onLongClick, onClick),
        enabled = enabled,
        onLongClick = onLongClick,
        supportingContent = supportingContent,
        trailingContent = trailingContent,
        tonalElevation = tonalElevation,
        shape = shape,
        colors = colors,
        scale = scale,
        border = border,
        glow = glow,
        interactionSource = source,
        content = content
    )
}
