package com.sasami.vlc.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sasami.vlc.player.DesktopPlayback
import com.sasami.vlc.store.*
import com.sasami.vlc.theme.Theme

@Composable
fun PlayList() {
    val queue = DesktopPlayback.queue.mapNotNull { path -> (DesktopPlayback.transientItems + PlayerStore.playerList).find { it.path == path } }
    Column(Modifier.width(272.dp).fillMaxHeight().background(Theme.SurfaceLow).padding(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Up next", color = Theme.TextPrimary, fontSize = 21.sp)
                Text("${queue.size} items in queue", color = Theme.TextMuted, fontSize = 11.sp)
            }
            ActionIcon(Icons.Default.Close, "Close queue") { PlayerStore.isPlayListCollapsed = true }
        }
        Spacer(Modifier.height(16.dp))
        if (queue.isEmpty()) Text("Add media to the queue from its menu.", color = Theme.TextSecondary, fontSize = 12.sp)
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            itemsIndexed(queue, key = { _, item -> item.path }) { index, item ->
                val active = item.path == PlayerStore.activeId
                val interaction = remember { MutableInteractionSource() }
                val focused = interaction.collectIsFocusedAsState().value
                val hovered = interaction.collectIsHoveredAsState().value
                val cardColor = animateColorAsState(when {
                    focused -> Theme.SurfaceFocused
                    active -> Theme.AccentSoft
                    hovered -> Theme.SurfaceHigh
                    else -> Theme.Surface
                })
                Column(Modifier.fillMaxWidth().clip(Theme.CardShape).background(cardColor.value)
                    .border(1.dp, if (focused || active) Theme.Accent else Theme.Border, Theme.CardShape)
                    .hoverable(interaction)
                    .clickable(interactionSource = interaction, indication = null) {
                        DesktopPlayback.play(item, persist = item !in DesktopPlayback.transientItems)
                    }.padding(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(if (active) "▶" else "${index + 1}", color = Theme.Accent, fontSize = 12.sp, modifier = Modifier.width(24.dp))
                        Text(item.name, color = Theme.TextPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis, fontSize = 12.sp,
                            modifier = Modifier.weight(1f))
                    }
                    Row(Modifier.align(Alignment.End)) {
                        ActionIcon(Icons.Default.KeyboardArrowUp, "Move up", enabled = index > 0) { DesktopPlayback.moveQueue(item.path, -1) }
                        ActionIcon(Icons.Default.KeyboardArrowDown, "Move down", enabled = index < queue.lastIndex) { DesktopPlayback.moveQueue(item.path, 1) }
                        ActionIcon(Icons.Default.Close, "Remove from queue") { DesktopPlayback.queue = DesktopPlayback.queue - item.path }
                    }
                }
            }
        }
        TextButton(onClick = { DesktopPlayback.queue = listOf(PlayerStore.activeId).filter { it.isNotBlank() } }) { Text("Clear upcoming") }
    }
}
