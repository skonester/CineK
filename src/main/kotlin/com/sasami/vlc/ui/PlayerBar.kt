package com.sasami.vlc.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInWindow
import kotlin.math.roundToInt
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.WindowScope
import com.sasami.vlc.player.DesktopPlayback
import com.sasami.vlc.player.VlcPlayer
import com.sasami.vlc.store.*
import com.sasami.vlc.theme.Theme

fun formatTime(timeMs: Long): String {
    val seconds = timeMs.coerceAtLeast(0) / 1000
    return if (seconds >= 3600) "%d:%02d:%02d".format(seconds / 3600, seconds % 3600 / 60, seconds % 60)
    else "%02d:%02d".format(seconds / 60, seconds % 60)
}

@Composable
fun WindowScope.PlayerBar() {
    val active = PlayerStore.activeId.isNotEmpty()
    DisposableEffect(Unit) { onDispose { PlayerChrome.toolbarBounds = null } }
    Column(Modifier.fillMaxWidth().onGloballyPositioned { coordinates ->
        val bounds = coordinates.boundsInWindow()
        val transform = window.graphicsConfiguration.defaultTransform
        PlayerChrome.toolbarBounds = java.awt.Rectangle(
            (bounds.left / transform.scaleX).roundToInt(), (bounds.top / transform.scaleY).roundToInt(),
            (bounds.width / transform.scaleX).roundToInt(), (bounds.height / transform.scaleY).roundToInt())
    }.background(Theme.SurfaceBar).padding(horizontal = 22.dp, vertical = 6.dp)) {
        if (active) {
            TimelineSeekBar()
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(formatTime(PlayerStore.currentTime), color = Theme.TextSecondary, fontSize = 10.sp)
                Text(formatTime(PlayerStore.duration), color = Theme.TextMuted, fontSize = 10.sp)
            }
        }
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val wide = maxWidth > 1050.dp
            Row(Modifier.fillMaxWidth().height(64.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f).padding(end = 12.dp)) {
                    Text((DesktopPlayback.transientItems + PlayerStore.playerList).find { it.path == PlayerStore.activeId }?.name ?: "No media loaded",
                        color = Theme.TextPrimary, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(if (active) if (PlayerStore.isPlaying) "Playing" else "Paused" else "Open a file or select a library item",
                        color = Theme.TextMuted, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                ActionIcon(Icons.Default.Shuffle, "Shuffle", LibraryStore.shuffle) { LibraryStore.shuffle = !LibraryStore.shuffle; LibraryStore.save() }
                ActionIcon(Icons.Default.SkipPrevious, "Previous", enabled = active) { DesktopPlayback.previous() }
                ActionIcon(Icons.Default.Replay10, "Back 10 seconds · Left", enabled = active) { DesktopPlayback.skip(-10000) }
                Button(onClick = { DesktopPlayback.toggle() }, enabled = active || PlayerStore.playerList.isNotEmpty(),
                    shape = RoundedCornerShape(19.dp), modifier = Modifier.size(56.dp, 46.dp), contentPadding = PaddingValues(0.dp)) {
                    Icon(if (PlayerStore.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        if (PlayerStore.isPlaying) "Pause · Space" else "Play · Space", Modifier.size(28.dp))
                }
                ActionIcon(Icons.Default.Forward10, "Forward 10 seconds · Right", enabled = active) { DesktopPlayback.skip(10000) }
                ActionIcon(Icons.Default.SkipNext, "Next", enabled = active) { DesktopPlayback.advance() }
                ActionIcon(if (LibraryStore.repeat == "one") Icons.Default.RepeatOne else Icons.Default.Repeat,
                    "Repeat: ${LibraryStore.repeat}", LibraryStore.repeat != "off") {
                    LibraryStore.repeat = when (LibraryStore.repeat) { "off" -> "all"; "all" -> "one"; else -> "off" }
                    LibraryStore.save()
                }
                Spacer(Modifier.width(14.dp))
                ActionIcon(if (PlayerStore.volume == 0) Icons.Default.VolumeOff else Icons.Default.VolumeUp, "Mute · M") { DesktopPlayback.mute() }
                if (wide) Slider(PlayerStore.volume.toFloat(), { VlcPlayer.setVolume(it.toInt()) },
                    onValueChangeFinished = { LibraryStore.save() }, valueRange = 0f..100f, modifier = Modifier.width(90.dp))
                TextButton(onClick = { LibraryStore.dialog = "playback" }, enabled = active, contentPadding = PaddingValues(6.dp)) {
                    Text("${LibraryStore.speed}×", fontSize = 12.sp)
                }
                ActionIcon(Icons.Default.Subtitles, "Audio & subtitles", enabled = active) { LibraryStore.dialog = "tracks" }
                if (wide) ActionIcon(Icons.Default.Stop, "Stop", enabled = active) { DesktopPlayback.stop() }
                ActionIcon(Icons.Default.QueueMusic, "Queue", !PlayerStore.isPlayListCollapsed, active) {
                    PlayerStore.isPlayListCollapsed = !PlayerStore.isPlayListCollapsed
                }
                ActionIcon(if (LibraryStore.showLibrary) Icons.Default.OndemandVideo else Icons.Default.VideoLibrary,
                    if (LibraryStore.showLibrary) "Now playing" else "Library", enabled = active) {
                    LibraryStore.showLibrary = !LibraryStore.showLibrary
                    if (LibraryStore.showLibrary) LibraryStore.fullscreen = false
                }
                ActionIcon(if (LibraryStore.fullscreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                    "Fullscreen · F", enabled = active) { LibraryStore.showLibrary = false; LibraryStore.fullscreen = !LibraryStore.fullscreen }
            }
        }
    }
}

@Composable
fun TimelineSeekBar() {
    var dragging by remember { mutableStateOf(false) }
    var preview by remember { mutableStateOf(0f) }
    val fraction = if (PlayerStore.duration > 0) (PlayerStore.currentTime.toFloat() / PlayerStore.duration).coerceIn(0f, 1f) else 0f
    Slider(if (dragging) preview else fraction, {
        dragging = true; preview = it
    }, onValueChangeFinished = {
        DesktopPlayback.seek((preview * PlayerStore.duration).toLong()); dragging = false
    }, enabled = PlayerStore.duration > 0 && VlcPlayer.mediaPlayer?.status()?.isSeekable == true,
        modifier = Modifier.fillMaxWidth().height(22.dp),
        colors = SliderDefaults.colors(thumbColor = Theme.Accent, activeTrackColor = Theme.Accent, inactiveTrackColor = Theme.Border))
}
