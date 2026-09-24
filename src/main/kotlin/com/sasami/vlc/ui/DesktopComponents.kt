package com.sasami.vlc.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sasami.vlc.theme.Theme

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ActionIcon(icon: ImageVector, label: String, active: Boolean = false, enabled: Boolean = true, onClick: () -> Unit) {
    TooltipArea(tooltip = {
        Surface(color = Theme.Surface, shape = RoundedCornerShape(8.dp), elevation = 8.dp) {
            Text(label, Modifier.padding(10.dp), color = Theme.TextPrimary, fontSize = 12.sp)
        }
    }) {
        IconButton(onClick, enabled = enabled, modifier = Modifier.size(36.dp)) {
            Icon(icon, label, Modifier.size(20.dp), tint = when {
                !enabled -> Theme.TextMuted
                active -> Theme.Accent
                else -> Theme.TextSecondary
            })
        }
    }
}

@Composable
fun Pill(text: String, selected: Boolean = false, onClick: (() -> Unit)? = null) {
    Surface(color = if (selected) Theme.AccentSoft else Theme.Surface, shape = RoundedCornerShape(12.dp),
        modifier = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier) {
        Text(text, Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            color = if (selected) Theme.Accent else Theme.TextSecondary, fontSize = 12.sp)
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
