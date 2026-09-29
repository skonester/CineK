package com.sasami.vlc.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sasami.vlc.player.DesktopPlayback
import com.sasami.vlc.store.*
import com.sasami.vlc.theme.Theme
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter

@Composable
fun LiveChannelsPage() {
    var selected by remember { mutableStateOf<String?>(null) }
    var category by remember { mutableStateOf("All channels") }
    var query by remember { mutableStateOf("") }
    var addOpen by remember { mutableStateOf(false) }
    var featuredMenu by remember { mutableStateOf(false) }
    var sourceName by remember { mutableStateOf("") }
    var sourceUrl by remember { mutableStateOf("") }
    val sources = LiveChannels.sources
    val source = sources.firstOrNull { it.id == selected } ?: sources.firstOrNull()
    val channels = LiveChannels.channels.filter { it.sourceId == source?.id }
    val groups = remember(channels) { listOf("All channels") + channels.map { it.group }.distinct().sortedBy { it.lowercase() } }
    val visible = remember(channels, category, query) {
        channels.filter { (category == "All channels" || it.group == category) &&
            (query.isBlank() || it.name.contains(query, true) || it.number.contains(query, true)) }
    }
    Column(Modifier.fillMaxSize().padding(24.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Live Channels", color = Theme.TextPrimary, fontSize = 29.sp)
                Text("Browse M3U playlists and watch live TV", color = Theme.TextSecondary, fontSize = 12.sp)
            }
            OutlinedButton(onClick = {
                val chooser = JFileChooser().apply { fileFilter = FileNameExtensionFilter("M3U playlists", "m3u", "m3u8") }
                if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
                    val file = chooser.selectedFile
                    LiveChannels.add(file.nameWithoutExtension, file.absolutePath)
                    selected = LiveChannels.sources.lastOrNull()?.id
                }
            }) { Icon(Icons.Default.FolderOpen, null); Spacer(Modifier.width(5.dp)); Text("Import file") }
            Spacer(Modifier.width(8.dp))
            Button(onClick = { addOpen = true }, shape = RoundedCornerShape(12.dp)) {
                Icon(Icons.Default.Add, null); Spacer(Modifier.width(4.dp)); Text("Add playlist URL")
            }
        }
        LiveChannels.error?.let { message ->
            TextButton(onClick = { LiveChannels.clearError() }) { Text("$message  ×", color = Theme.Danger) }
        }
        Spacer(Modifier.height(15.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.width(205.dp).fillMaxHeight().background(Theme.SurfaceBar, RoundedCornerShape(16.dp)).padding(10.dp)) {
                SectionLabel("PLAYLISTS")
                if (sources.isEmpty()) Text("Add a playlist to start watching.", color = Theme.TextMuted, fontSize = 12.sp,
                    modifier = Modifier.padding(8.dp))
                sources.forEach { entry ->
                    val count = LiveChannels.channels.count { it.sourceId == entry.id }
                    Column(Modifier.fillMaxWidth().background(if (entry.id == source?.id) Theme.AccentSoft else Theme.SurfaceBar,
                        RoundedCornerShape(10.dp)).clickable { selected = entry.id; category = "All channels" }.padding(9.dp)) {
                        Text(entry.name, color = if (entry.id == source?.id) Theme.Accent else Theme.TextPrimary,
                            maxLines = 2, overflow = TextOverflow.Ellipsis, fontSize = 13.sp)
                        Text(if (LiveChannels.loading == entry.id) "Loading…" else "$count channels",
                            color = Theme.TextMuted, fontSize = 11.sp)
                    }
                }
                if (source != null) {
                    Spacer(Modifier.height(12.dp))
                    OutlinedButton(onClick = { LiveChannels.refresh(source) }, enabled = LiveChannels.loading == null,
                        modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.Refresh, null, Modifier.size(16.dp)); Text("Refresh") }
                    TextButton(onClick = { LiveChannels.remove(source); selected = null; category = "All channels" },
                        modifier = Modifier.fillMaxWidth()) { Text("Remove playlist", color = Theme.Danger, fontSize = 11.sp) }
                }
                Spacer(Modifier.height(8.dp))
                SectionLabel("FREE CHANNEL SOURCES")
                Box {
                    OutlinedButton(onClick = { featuredMenu = true }, modifier = Modifier.fillMaxWidth()) { Text("Choose service / region", fontSize = 11.sp) }
                    AppDropdownMenu(featuredMenu, { featuredMenu = false }) {
                        LiveChannels.featured.forEach { featured ->
                            DropdownMenuItem(onClick = {
                                featuredMenu = false
                                LiveChannels.add(featured.name, featured.url)
                                selected = LiveChannels.sources.lastOrNull()?.id
                            }, enabled = sources.none { it.location == featured.url }) { Text(featured.name) }
                        }
                    }
                }
            }
            Column(Modifier.width(185.dp).fillMaxHeight().background(Theme.SurfaceBar, RoundedCornerShape(16.dp)).padding(10.dp)) {
                SectionLabel("CATEGORIES")
                LazyColumn {
                    items(groups) { group ->
                        val count = if (group == "All channels") channels.size else channels.count { it.group == group }
                        Row(Modifier.fillMaxWidth().background(if (category == group) Theme.AccentSoft else Theme.SurfaceBar,
                            RoundedCornerShape(9.dp)).clickable { category = group }.padding(9.dp)) {
                            Text(group, Modifier.weight(1f), color = if (category == group) Theme.Accent else Theme.TextSecondary,
                                maxLines = 2, overflow = TextOverflow.Ellipsis, fontSize = 12.sp)
                            Text("$count", color = Theme.TextMuted, fontSize = 11.sp)
                        }
                    }
                }
            }
            Column(Modifier.weight(1f).fillMaxHeight()) {
                OutlinedTextField(query, { query = it }, modifier = Modifier.fillMaxWidth(), singleLine = true,
                    placeholder = { Text("Search channels") }, leadingIcon = { Icon(Icons.Default.Search, null) })
                Spacer(Modifier.height(10.dp))
                Text("${visible.size} channels", color = Theme.TextSecondary, fontSize = 12.sp)
                Spacer(Modifier.height(8.dp))
                if (LiveChannels.loading == source?.id) LinearProgressIndicator(Modifier.fillMaxWidth())
                if (visible.isEmpty()) Text(if (source == null) "Choose a free source or add your own M3U playlist."
                    else "No channels here. Refresh the playlist or try another category.",
                    color = Theme.TextMuted, modifier = Modifier.padding(16.dp))
                LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(visible, key = { it.url }) { channel ->
                        Row(Modifier.fillMaxWidth().background(if (PlayerStore.activeId == channel.url) Theme.AccentSoft else Theme.Surface,
                            RoundedCornerShape(12.dp)).clickable { DesktopPlayback.play(channel.playerItem(), visible.map { it.playerItem() }, persist = false) }
                            .padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.LiveTv, null, Modifier.size(25.dp), tint = Theme.Accent)
                            Column(Modifier.weight(1f).padding(start = 11.dp)) {
                                Text(channel.name, color = Theme.TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(listOf(channel.number, channel.group).filter { it.isNotBlank() }.joinToString(" · "),
                                    color = Theme.TextMuted, fontSize = 11.sp)
                            }
                            Icon(Icons.Default.PlayArrow, "Watch channel", tint = Theme.Accent)
                        }
                    }
                }
            }
        }
    }
    if (addOpen) AlertDialog(onDismissRequest = { addOpen = false }, title = { Text("Add M3U playlist") },
        backgroundColor = Theme.SurfaceLow, contentColor = Theme.TextPrimary,
        text = { Column {
            OutlinedTextField(sourceName, { sourceName = it }, label = { Text("Name") }, singleLine = true)
            OutlinedTextField(sourceUrl, { sourceUrl = it }, label = { Text("Playlist URL") }, singleLine = true)
        } }, confirmButton = { TextButton(onClick = {
            LiveChannels.add(sourceName, sourceUrl)
            selected = LiveChannels.sources.lastOrNull()?.id
            if (LiveChannels.error == null) { addOpen = false; sourceName = ""; sourceUrl = "" }
        }, enabled = sourceUrl.isNotBlank()) { Text("Add") } },
        dismissButton = { TextButton(onClick = { addOpen = false }) { Text("Cancel") } })
}
