package com.sasami.vlc.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sasami.vlc.theme.Theme

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ActionIcon(icon: ImageVector, label: String, active: Boolean = false, enabled: Boolean = true, onClick: () -> Unit) {
    val interaction = androidx.compose.runtime.remember { MutableInteractionSource() }
    val focused = interaction.collectIsFocusedAsState().value
    val hovered = interaction.collectIsHoveredAsState().value
    val background = animateColorAsState(when {
        focused -> Theme.SurfaceFocused
        hovered && enabled -> Theme.SurfaceHigh
        active -> Theme.AccentSoft
        else -> Color.Transparent
    })
    TooltipArea(tooltip = {
        Surface(color = Theme.Surface, shape = RoundedCornerShape(8.dp), elevation = 8.dp) {
            Text(label, Modifier.padding(10.dp), color = Theme.TextPrimary, fontSize = 12.sp)
        }
    }) {
        IconButton(onClick, enabled = enabled, interactionSource = interaction,
            modifier = Modifier.size(38.dp).background(background.value, Theme.ControlShape)) {
            Icon(icon, label, Modifier.size(20.dp), tint = when {
                !enabled -> Theme.TextMuted
                active || focused -> Theme.Accent
                else -> Theme.TextSecondary
            })
        }
    }
}

@Composable
fun AppDropdownMenu(expanded: Boolean, onDismissRequest: () -> Unit,
    content: @Composable ColumnScope.() -> Unit) {
    MaterialTheme(colors = MaterialTheme.colors.copy(surface = Theme.SurfaceLow, onSurface = Theme.TextPrimary),
        typography = MaterialTheme.typography, shapes = MaterialTheme.shapes) {
        DropdownMenu(expanded = expanded, onDismissRequest = onDismissRequest,
            modifier = Modifier.background(Theme.SurfaceLow), content = content)
    }
}

@Composable
fun Pill(text: String, selected: Boolean = false, onClick: (() -> Unit)? = null) {
    val interaction = remember { MutableInteractionSource() }
    val focused = interaction.collectIsFocusedAsState().value
    val hovered = interaction.collectIsHoveredAsState().value
    val color = animateColorAsState(when {
        focused -> Theme.SurfaceFocused
        selected -> Theme.AccentSoft
        hovered && onClick != null -> Theme.SurfaceHigh
        else -> Theme.Surface
    })
    Surface(color = color.value, shape = Theme.ControlShape,
        border = BorderStroke(1.dp, if (focused) Theme.Accent else Color.Transparent),
        modifier = if (onClick != null) Modifier.hoverable(interaction)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick) else Modifier) {
        Text(text, Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            color = if (selected || focused) Theme.Accent else Theme.TextSecondary, fontSize = 12.sp)
    }
}

@Composable
fun SectionLabel(text: String) {
    Text(text, color = Theme.TextMuted, fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
        letterSpacing = 1.5.sp, modifier = Modifier.padding(vertical = 12.dp))
}

@Composable
fun EmptyLibrary(icon: ImageVector, title: String, detail: String, action: String? = null, onAction: () -> Unit = {}) {
    Column(Modifier.fillMaxSize().padding(28.dp), verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(color = Theme.AccentSoft, shape = RoundedCornerShape(28.dp)) {
            Icon(icon, null, Modifier.padding(25.dp).size(42.dp), tint = Theme.Accent)
        }
        Spacer(Modifier.height(22.dp))
        Text(title, color = Theme.TextPrimary, fontSize = 24.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(10.dp))
        Text(detail, color = Theme.TextSecondary, fontSize = 13.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        if (action != null) {
            Spacer(Modifier.height(22.dp))
            Button(onAction, shape = RoundedCornerShape(16.dp), contentPadding = PaddingValues(20.dp, 12.dp)) { Text(action) }
        }
    }
}
