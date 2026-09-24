package com.sasami.vlc.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sasami.vlc.player.DesktopPlayback
import com.sasami.vlc.store.*
import com.sasami.vlc.theme.Theme
import com.sasami.vlc.utils.FileUtils
import java.io.File

private val pages = listOf(
    Triple("Library", Icons.Default.VideoLibrary, "All media"),
    Triple("Videos", Icons.Default.Movie, "Videos"),
    Triple("Music", Icons.Default.MusicNote, "Music"),
    Triple("Folders", Icons.Default.Folder, "Folders"),
    Triple("History", Icons.Default.History, "Recently played"),
    Triple("Favorites", Icons.Default.FavoriteBorder, "Favorites"),
    Triple("Playlists", Icons.Default.PlaylistPlay, "Playlists"),
    Triple("Live Channels", Icons.Default.LiveTv, "Live Channels")
)

@Composable
fun LibraryNavigation() {
    Column(Modifier.width(204.dp).fillMaxHeight().background(Theme.SurfaceBar).padding(14.dp)) {
        Row(Modifier.padding(10.dp, 20.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(color = Theme.AccentSoft, shape = RoundedCornerShape(14.dp)) {
                Image(androidx.compose.ui.res.painterResource("icon.png"), "CineK", Modifier.size(46.dp).padding(3.dp))
            }
            Spacer(Modifier.width(10.dp))
            Column {
                Text("CineK", color = Theme.TextPrimary, fontSize = 19.sp, fontWeight = FontWeight.SemiBold)

            }
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
        SectionLabel("BROWSE")
        pages.forEach { (key, icon, label) ->
            val selected = LibraryStore.page == key
            Row(Modifier.fillMaxWidth().padding(vertical = 3.dp).clip(RoundedCornerShape(16.dp))
                .background(if (selected) Theme.AccentSoft else Color.Transparent)
                .clickable { LibraryStore.page = key; LibraryStore.selectedFolder = null; LibraryStore.selectedPlaylist = null }
                .padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, Modifier.size(20.dp), tint = if (selected) Theme.Accent else Theme.TextSecondary)
                Spacer(Modifier.width(12.dp))
                Text(label, color = if (selected) Theme.Accent else Theme.TextSecondary, fontSize = 13.sp)
            }
        }
        }
        Divider(color = Theme.Border)
        TextButton(onClick = { LibraryStore.dialog = "settings" }, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.Settings, null, Modifier.size(18.dp)); Spacer(Modifier.width(10.dp)); Text("Settings")
        }
        Text("Drop files or folders anywhere", color = Theme.TextMuted, fontSize = 10.sp,
            modifier = Modifier.padding(8.dp))
    }
}

@Composable
fun LibraryDashboard() {
    if (LibraryStore.page == "Live Channels") { LiveChannelsPage(); return }
    var query by remember { mutableStateOf("") }
    var sort by remember { mutableStateOf("Recently added") }
    var sortMenu by remember { mutableStateOf(false) }
    val page = LibraryStore.page
    val playlist = LibraryStore.playlists.find { it.id == LibraryStore.selectedPlaylist }
    val folder = LibraryStore.selectedFolder
    val all = PlayerStore.playerList
    val filtered = remember(all, page, query, sort, LibraryStore.favorites, LibraryStore.history, playlist, folder) {
        val base = when (page) {
            "Videos" -> all.filter { it.type == "video" }
            "Music" -> all.filter { it.type == "audio" }
            "Favorites" -> all.filter { it.path in LibraryStore.favorites }
            "History" -> LibraryStore.history.sortedByDescending { it.playedAt }.mapNotNull { entry -> all.find { it.path == entry.path } }
            "Playlists" -> playlist?.paths?.mapNotNull { path -> all.find { it.path == path } } ?: emptyList()
            "Folders" -> all.filter { it.type != "stream" && File(it.path).parent == folder }
            else -> all
        }.filter { it.name.contains(query, true) || it.path.contains(query, true) }
        when (sort) {
            "Name A–Z" -> base.sortedBy { it.name.lowercase() }
            "Name Z–A" -> base.sortedByDescending { it.name.lowercase() }
            "Largest first" -> base.sortedByDescending { it.size }
            else -> if (page in listOf("History", "Playlists")) base else base.reversed()
        }
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 28.dp, vertical = 20.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(playlist?.name ?: folder?.let { File(it).name } ?: when (page) {
                    "Library" -> "Library"
                    "History" -> "Recently played"
                    else -> page
                }, color = Theme.TextPrimary, fontSize = 29.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(5.dp))
                Text("${filtered.size} items", color = Theme.TextSecondary, fontSize = 12.sp)
            }
            ActionIcon(Icons.Default.FolderOpen, "Add folder") { DesktopPlayback.openFolder() }
            ActionIcon(Icons.Default.Link, "Open stream") { LibraryStore.dialog = "stream" }
            Button(onClick = { DesktopPlayback.openFiles() }, shape = RoundedCornerShape(14.dp),
                contentPadding = PaddingValues(14.dp, 10.dp)) {
                Icon(Icons.Default.Add, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Open media")
            }
        }
        Spacer(Modifier.height(22.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(query, { query = it }, modifier = Modifier.weight(1f), singleLine = true,
                placeholder = { Text("Search media", fontSize = 13.sp) },
                leadingIcon = { Icon(Icons.Default.Search, null, Modifier.size(20.dp)) },
                trailingIcon = if (query.isNotEmpty()) ({ ActionIcon(Icons.Default.Close, "Clear search") { query = "" } }) else null,
                shape = RoundedCornerShape(16.dp),
                colors = TextFieldDefaults.outlinedTextFieldColors(unfocusedBorderColor = Theme.Border,
                    backgroundColor = Theme.SurfaceBar, textColor = Theme.TextPrimary))
            Box {
                TextButton(onClick = { sortMenu = true }) {
                    Icon(Icons.Default.Sort, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(sort, fontSize = 12.sp)
                }
                DropdownMenu(sortMenu, { sortMenu = false }) {
                    listOf("Recently added", "Name A–Z", "Name Z–A", "Largest first").forEach { option ->
                        DropdownMenuItem({ sort = option; sortMenu = false }) { Text(option) }
                    }
                }
            }
            ActionIcon(if (LibraryStore.grid) Icons.Default.ViewList else Icons.Default.GridView, "Switch grid / list") {
                LibraryStore.grid = !LibraryStore.grid; LibraryStore.save()
            }
        }
        if (LibraryStore.busy) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 8.dp))
        Spacer(Modifier.height(18.dp))

        if (page == "Playlists" && playlist == null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Pill("New playlist", true) { LibraryStore.dialog = "newPlaylist" }
                Spacer(Modifier.width(8.dp))
                Pill("Import M3U") { com.sasami.vlc.utils.PlaylistFiles.importPlaylist() }
            }
            Spacer(Modifier.height(12.dp))
            if (LibraryStore.playlists.isEmpty()) EmptyLibrary(Icons.Default.PlaylistPlay, "No playlists",
                "Create a playlist, then add media from the menu on any item.", "Create playlist") { LibraryStore.dialog = "newPlaylist" }
            else LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(LibraryStore.playlists.filter { it.name.contains(query, true) }, key = { it.id }) { saved ->
                    Surface(color = Theme.Surface, shape = RoundedCornerShape(18.dp)) {
                        Row(Modifier.fillMaxWidth().clickable { LibraryStore.selectedPlaylist = saved.id }.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.PlaylistPlay, null, Modifier.size(32.dp), tint = Theme.Accent)
                            Column(Modifier.weight(1f).padding(horizontal = 16.dp)) {
                                Text(saved.name, color = Theme.TextPrimary)
                                Text("${saved.paths.size} items", color = Theme.TextMuted, fontSize = 12.sp)
                            }
                            ActionIcon(Icons.Default.FileDownload, "Export playlist") { com.sasami.vlc.utils.PlaylistFiles.exportPlaylist(saved) }
                            ActionIcon(Icons.Default.DeleteOutline, "Delete playlist") { LibraryStore.dialog = "deletePlaylist:${saved.id}" }
                            Icon(Icons.Default.ChevronRight, null, tint = Theme.TextMuted)
                        }
                    }
                }
            }
        } else if (page == "Folders" && folder == null) {
            val folders = all.filter { it.type != "stream" }.groupBy { File(it.path).parent ?: "" }
                .filter { (path, _) -> path.contains(query, true) }
            if (folders.isEmpty()) EmptyLibrary(Icons.Default.FolderOpen, "No folders",
                "Add a folder to browse its videos and music here.", "Add folder") { DesktopPlayback.openFolder() }
            else LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(folders.entries.sortedBy { it.key }, key = { it.key }) { entry ->
                    Surface(color = Theme.Surface, shape = RoundedCornerShape(18.dp)) {
                        Row(Modifier.fillMaxWidth().clickable { LibraryStore.selectedFolder = entry.key }.padding(18.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Folder, null, Modifier.size(34.dp), tint = Theme.Accent)
                            Column(Modifier.weight(1f).padding(horizontal = 16.dp)) {
                                Text(File(entry.key).name, color = Theme.TextPrimary)
                                Text(entry.key, color = Theme.TextMuted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            Pill("${entry.value.size} items")
                        }
                    }
                }
            }
        } else {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (playlist != null || folder != null) ActionIcon(Icons.Default.ArrowBack, "Back") {
                    LibraryStore.selectedPlaylist = null; LibraryStore.selectedFolder = null
                }
                if (page == "Library") {
                    Pill("All media", true)
                    Pill("Videos · ${all.count { it.type == "video" }}") { LibraryStore.page = "Videos" }
                    Pill("Music · ${all.count { it.type == "audio" }}") { LibraryStore.page = "Music" }
                }
                Spacer(Modifier.weight(1f))
                if (page == "History" && LibraryStore.history.isNotEmpty()) TextButton(onClick = { LibraryStore.dialog = "clearHistory" }) { Text("Clear history") }
                if (filtered.isNotEmpty()) TextButton(onClick = { DesktopPlayback.play(filtered.first(), filtered) }) {
                    Icon(Icons.Default.PlayArrow, null, Modifier.size(18.dp)); Text("Play all")
                }
            }
            Spacer(Modifier.height(12.dp))
            if (filtered.isEmpty()) {
                EmptyLibrary(
                    when (page) { "Favorites" -> Icons.Default.FavoriteBorder; "History" -> Icons.Default.History; else -> Icons.Default.VideoLibrary },
                    when { query.isNotEmpty() -> "No matches"; page == "Favorites" -> "No favorites"
                        page == "History" -> "No playback history"
                        playlist != null -> "Empty playlist"; else -> "No media" },
                    when { query.isNotEmpty() -> "Try another title or folder name."
                        page == "Favorites" -> "Use the favorite button on a media item to add it."
                        page == "History" -> "Played media and saved positions appear here."
                        playlist != null -> "Use a media item's menu to add it to this playlist."
                        else -> "Open media files or add a folder." },
                    if (page in listOf("Library", "Videos", "Music") && query.isEmpty()) "Add media" else null
                ) { DesktopPlayback.openFiles() }
            } else if (LibraryStore.grid) {
                LazyVerticalGrid(columns = GridCells.Adaptive(210.dp), horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp), contentPadding = PaddingValues(bottom = 18.dp)) {
                    items(filtered, key = { it.path }) { item -> MediaCard(item, true) { DesktopPlayback.play(item, filtered) } }
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 18.dp)) {
                    items(filtered, key = { it.path }) { item -> MediaCard(item, false) { DesktopPlayback.play(item, filtered) } }
                }
            }
        }
    }
}

@Composable
private fun MediaArtwork(item: PlayerItem, modifier: Modifier) {
    val history = LibraryStore.history.find { it.path == item.path }
    val tint = when (item.type) { "audio" -> Theme.AccentHover; "stream" -> Theme.AccentHighlight; else -> Theme.Accent }
    Box(modifier.clip(RoundedCornerShape(15.dp)).background(
        Brush.linearGradient(listOf(tint.copy(alpha = 0.16f), Theme.SurfaceBar))), contentAlignment = Alignment.Center) {
        Icon(when (item.type) { "audio" -> Icons.Default.MusicNote; "stream" -> Icons.Default.Language; else -> Icons.Default.Movie },
            null, Modifier.size(40.dp), tint = tint.copy(alpha = 0.7f))
        Text(if (item.type == "stream") "STREAM" else File(item.name).extension.uppercase(),
            Modifier.align(Alignment.TopStart).padding(10.dp), color = tint, fontSize = 10.sp, letterSpacing = 1.sp)
        if (history != null && history.duration > 0) {
            Text(formatTime(history.duration), Modifier.align(Alignment.BottomEnd).padding(8.dp)
                .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(5.dp)).padding(4.dp), color = Color.White, fontSize = 11.sp)
            LinearProgressIndicator((history.position.toFloat() / history.duration).coerceIn(0f, 1f),
                Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(3.dp), color = Theme.Accent, backgroundColor = Theme.Border)
        }
    }
}

@Composable
private fun MediaCard(item: PlayerItem, grid: Boolean, onPlay: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    var playlists by remember { mutableStateOf(false) }
    val favorite = item.path in LibraryStore.favorites
    val active = item.path == PlayerStore.activeId
    Surface(color = if (active) Theme.AccentSoft.copy(alpha = 0.45f) else Theme.Surface,
        shape = RoundedCornerShape(20.dp), border = BorderStroke(1.dp, if (active) Theme.Accent.copy(alpha = 0.4f) else Theme.Border)) {
        @Composable fun details(modifier: Modifier) {
            Column(modifier) {
                Text(item.name, color = Theme.TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium,
                    maxLines = if (grid) 2 else 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(7.dp))
                Text(if (item.type == "stream") "Network stream" else FileUtils.formatFileSize(item.size),
                    color = Theme.TextMuted, fontSize = 11.sp)
            }
        }
        @Composable fun actions() {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ActionIcon(if (favorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder, "Favorite", favorite) { LibraryStore.favorite(item.path) }
                Box {
                    ActionIcon(Icons.Default.MoreVert, "Media actions") { menu = true }
                    DropdownMenu(menu, { menu = false; playlists = false }) {
                        if (playlists) {
                            DropdownMenuItem({ playlists = false }) { Text("‹ Back") }
                            if (LibraryStore.playlists.isEmpty()) DropdownMenuItem({ menu = false; LibraryStore.dialog = "newPlaylist" }) { Text("Create a playlist first") }
                            LibraryStore.playlists.forEach { saved ->
                                DropdownMenuItem({ LibraryStore.addToPlaylist(saved.id, item.path); menu = false; playlists = false }) { Text(saved.name) }
                            }
                        } else {
                            DropdownMenuItem({ menu = false; onPlay() }) { Text("Play / resume") }
                            DropdownMenuItem({ menu = false; DesktopPlayback.play(item, fromStart = true) }) { Text("Play from beginning") }
                            DropdownMenuItem({ menu = false; DesktopPlayback.enqueue(item) }) { Text("Add to queue") }
                            DropdownMenuItem({ playlists = true }) { Text("Add to playlist…") }
                            if (LibraryStore.selectedPlaylist != null) DropdownMenuItem({
                                LibraryStore.removeFromPlaylist(LibraryStore.selectedPlaylist!!, item.path); menu = false
                            }) { Text("Remove from playlist") }
                            if (item.type != "stream") DropdownMenuItem({
                                menu = false
                                try { java.awt.Desktop.getDesktop().open(File(item.path).parentFile) }
                                catch (_: Exception) { LibraryStore.message = "This folder is no longer available." }
                            }) { Text("Open containing folder") }
                            DropdownMenuItem({ menu = false; DesktopPlayback.remove(item) }) { Text("Remove from library", color = Theme.Danger) }
                        }
                    }
                }
            }
        }
        if (grid) Column(Modifier.clickable(onClick = onPlay).padding(10.dp)) {
            MediaArtwork(item, Modifier.fillMaxWidth().height(116.dp))
            details(Modifier.padding(4.dp, 12.dp).heightIn(min = 55.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(if (active) "NOW PLAYING" else item.type.uppercase(), Modifier.weight(1f).padding(start = 4.dp),
                    color = if (active) Theme.Accent else Theme.TextMuted, fontSize = 9.sp, letterSpacing = 1.sp)
                actions()
            }
        } else Row(Modifier.fillMaxWidth().clickable(onClick = onPlay).padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            MediaArtwork(item, Modifier.width(124.dp).height(76.dp))
            details(Modifier.weight(1f).padding(horizontal = 18.dp))
            actions()
        }
    }
}
