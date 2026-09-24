package com.sasami.vlc.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.SwingPanel
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sasami.vlc.player.DesktopPlayback
import com.sasami.vlc.player.VlcPlayer
import com.sasami.vlc.store.LibraryStore
import com.sasami.vlc.store.PlayerStore
import com.sasami.vlc.theme.Theme

@Composable
fun VideoPlayerContent(modifier: Modifier = Modifier) {
    Row(modifier.fillMaxSize().background(Theme.Bg)) {
        if (!LibraryStore.fullscreen && LibraryStore.showLibrary) LibraryNavigation()
        Column(Modifier.weight(1f).fillMaxHeight()) {
            val playing = PlayerStore.activeId.isNotEmpty()
            val showVideo = playing && !LibraryStore.showLibrary
            val title = PlayerStore.playerList.find { it.path == PlayerStore.activeId }?.name ?: "Now playing"
            if (playing) {
                // One stable native surface in both views. The library preview gets
                // a bounded 16:9 viewport, rather than a full-width, shallow strip.
                Column(if (showVideo) Modifier.weight(1f).fillMaxWidth()
                    else Modifier.fillMaxWidth().height(186.dp).padding(horizontal = 28.dp, vertical = 12.dp)) {
                    if (showVideo && !LibraryStore.fullscreen) {
                        Row(Modifier.fillMaxWidth().background(Theme.SurfaceBar).padding(12.dp, 6.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            ActionIcon(Icons.Default.ArrowBack, "Back to library") { LibraryStore.showLibrary = true }
                            Text(title, Modifier.weight(1f), color = Theme.TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            ActionIcon(Icons.Default.Favorite, "Favorite", PlayerStore.activeId in LibraryStore.favorites) {
                                LibraryStore.favorite(PlayerStore.activeId)
                            }
                            ActionIcon(Icons.Default.Tune, "Playback settings") { LibraryStore.dialog = "playback" }
                        }
                    }
                    Row(Modifier.fillMaxSize().background(Theme.SurfaceBar)) {
                        Box(if (showVideo) Modifier.weight(1f).fillMaxHeight()
                            else Modifier.width(288.dp).fillMaxHeight()) {
                            VlcPlayer.mediaPlayerComponent?.let { component ->
                                val surfaceFactory = remember(component) { { component } }
                                SwingPanel(factory = surfaceFactory, background = Color.Black, modifier = Modifier.fillMaxSize())
                            } ?: Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
                                Text("VLC is unavailable", color = Theme.TextPrimary)
                            }
                        }
                        if (!showVideo) Column(Modifier.weight(1f).fillMaxHeight().padding(16.dp),
                            verticalArrangement = Arrangement.Center) {
                            Text("Now playing", color = Theme.TextMuted, fontSize = 11.sp)
                            Spacer(Modifier.height(6.dp))
                            Text(title, color = Theme.TextPrimary, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Spacer(Modifier.height(8.dp))
                            Row {
                                ActionIcon(Icons.Default.OndemandVideo, "Open player") { LibraryStore.showLibrary = false }
                                ActionIcon(if (PlayerStore.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    if (PlayerStore.isPlaying) "Pause" else "Play") { DesktopPlayback.toggle() }
                                ActionIcon(Icons.Default.Stop, "Stop") { DesktopPlayback.stop() }
                            }
                        }
                    }
                }
            }
            if (!showVideo) Box(Modifier.weight(1f).fillMaxWidth()) { LibraryDashboard() }
        }
        if (!PlayerStore.isPlayListCollapsed && PlayerStore.activeId.isNotEmpty() && !LibraryStore.fullscreen) PlayList()
    }
}
