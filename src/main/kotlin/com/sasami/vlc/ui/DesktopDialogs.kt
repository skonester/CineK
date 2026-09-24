package com.sasami.vlc.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogWindow
import androidx.compose.ui.window.rememberDialogState
import com.sasami.vlc.player.*
import com.sasami.vlc.store.*
import com.sasami.vlc.theme.Theme
import kotlinx.coroutines.delay

@Composable
fun DesktopDialogs() {
    val dialog = LibraryStore.dialog ?: return
    DialogWindow(state = rememberDialogState(width = 640.dp, height = if (dialog in listOf("stream", "newPlaylist", "clearHistory") || dialog.startsWith("deletePlaylist:")) 340.dp else 680.dp), onCloseRequest = { LibraryStore.dialog = null }, icon = androidx.compose.ui.res.painterResource("icon.png"), title = "CineK · ${dialog.substringBefore(':')}",
        resizable = false, undecorated = true) {
        MaterialTheme(colors = darkColors(primary = Theme.Accent, secondary = Theme.Accent, surface = Theme.SurfaceBar, background = Theme.Bg,
            onPrimary = Theme.Bg, onSurface = Theme.TextPrimary), typography = Theme.typography) {
            Surface(color = Theme.Bg, shape = RoundedCornerShape(24.dp)) {
                Column(Modifier.width(620.dp).heightIn(max = 660.dp).padding(26.dp).verticalScroll(rememberScrollState())) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(when {
                            dialog == "stream" -> "Open a stream"
                            dialog == "newPlaylist" -> "New playlist"
                            dialog == "tracks" -> "Audio & subtitles"
                            dialog == "playback" -> "Playback controls"
                            dialog == "settings" -> "Settings"
                            dialog == "shortcuts" -> "Keyboard shortcuts"
                            else -> "Confirm"
                        }, Modifier.weight(1f), color = Theme.TextPrimary, fontSize = 25.sp)
                        ActionIcon(Icons.Default.Close, "Close dialog") { LibraryStore.dialog = null }
                    }
                    Spacer(Modifier.height(20.dp))
                    when {
                        dialog == "stream" -> StreamDialog()
                        dialog == "newPlaylist" -> NewPlaylistDialog()
                        dialog == "tracks" -> TrackSettings()
                        dialog == "playback" -> PlaybackSettings()
                        dialog == "settings" -> AppSettings()
                        dialog == "shortcuts" -> ShortcutList()
                        dialog == "clearHistory" -> {
                            Text("Clear saved watch positions and recently played history?")
                            Spacer(Modifier.height(20.dp))
                            Button({ LibraryStore.history = emptyList(); LibraryStore.save(); LibraryStore.dialog = null }) { Text("Clear history") }
                        }
                        dialog.startsWith("deletePlaylist:") -> {
                            Text("Delete this playlist? Media files will not be deleted.")
                            Spacer(Modifier.height(20.dp))
                            Button({
                                LibraryStore.playlists = LibraryStore.playlists.filterNot { it.id == dialog.substringAfter(':') }
                                LibraryStore.save(); LibraryStore.dialog = null
                            }) { Text("Delete playlist") }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StreamDialog() {
    var address by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    Text("Play a direct media URL or live stream.", color = Theme.TextSecondary)
    Spacer(Modifier.height(16.dp))
    OutlinedTextField(address, { address = it; error = null }, label = { Text("Stream URL") },
        placeholder = { Text("https://…") }, singleLine = true, isError = error != null, modifier = Modifier.fillMaxWidth())
    if (error != null) Text(error!!, color = Theme.Danger, modifier = Modifier.padding(top = 8.dp))
    Spacer(Modifier.height(20.dp))
    Button({
        error = DesktopPlayback.stream(address)
        if (error == null) LibraryStore.dialog = null
    }, enabled = address.isNotBlank(), shape = RoundedCornerShape(14.dp)) { Icon(Icons.Default.PlayArrow, null); Text("Play stream") }
}

@Composable
private fun NewPlaylistDialog() {
    var name by remember { mutableStateOf("") }
    OutlinedTextField(name, { name = it }, label = { Text("Playlist name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
    Spacer(Modifier.height(20.dp))
    Button({
        LibraryStore.selectedPlaylist = LibraryStore.createPlaylist(name)
        LibraryStore.page = "Playlists"; LibraryStore.dialog = null
    }, enabled = name.isNotBlank(), shape = RoundedCornerShape(14.dp)) { Text("Create playlist") }
}

@Composable
private fun TrackSettings() {
    val player = VlcPlayer.mediaPlayer
    var refresh by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) { while (true) { delay(1000); refresh++ } }
    val audio = remember(refresh) { player?.audio()?.trackDescriptions().orEmpty() }
    val subs = remember(refresh) { player?.subpictures()?.trackDescriptions().orEmpty() }
    SectionLabel("AUDIO TRACK")
    if (audio.isEmpty()) Text("No audio tracks available.", color = Theme.TextMuted)
    audio.forEach { track ->
        Row(Modifier.fillMaxWidth().clickable { player?.audio()?.setTrack(track.id()); refresh++ },
            verticalAlignment = Alignment.CenterVertically) {
            RadioButton(player?.audio()?.track() == track.id(), { player?.audio()?.setTrack(track.id()); refresh++ })
            Text(track.description() ?: "Track ${track.id()}")
        }
    }
    SectionLabel("SUBTITLES")
    Row(Modifier.fillMaxWidth().clickable { player?.subpictures()?.setTrack(-1); refresh++ },
        verticalAlignment = Alignment.CenterVertically) {
        RadioButton(player?.subpictures()?.track() == -1, { player?.subpictures()?.setTrack(-1); refresh++ })
        Text("Off")
    }
    subs.filter { it.id() >= 0 }.forEach { track ->
        Row(Modifier.fillMaxWidth().clickable { player?.subpictures()?.setTrack(track.id()); refresh++ },
            verticalAlignment = Alignment.CenterVertically) {
            RadioButton(player?.subpictures()?.track() == track.id(), { player?.subpictures()?.setTrack(track.id()); refresh++ })
            Text(track.description() ?: "Track ${track.id()}")
        }
    }
    OutlinedButton({ DesktopPlayback.subtitles(); refresh++ }, enabled = PlayerStore.activeId.isNotBlank(),
        shape = RoundedCornerShape(12.dp)) { Icon(Icons.Default.Add, null); Text("Load subtitle file") }
    SectionLabel("SYNCHRONIZATION")
    DelayControl("Audio delay", player?.audio()?.delay() ?: 0) { player?.audio()?.setDelay(it); refresh++ }
    DelayControl("Subtitle delay", player?.subpictures()?.delay() ?: 0) { player?.subpictures()?.setDelay(it); refresh++ }
    SectionLabel("AUDIO OUTPUT")
    val devices = remember { VlcPlayer.getAudioDevices().filter { it.id.isNotBlank() }.distinctBy { it.id } }
    Row(verticalAlignment = Alignment.CenterVertically) {
        RadioButton(PlayerStore.audioDeviceId.isEmpty(), { VlcPlayer.setAudioDevice("") }); Text("System default")
    }
    devices.forEach { device ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            RadioButton(PlayerStore.audioDeviceId == device.id, { VlcPlayer.setAudioDevice(device.id) })
            Text(device.name, fontSize = 12.sp)
        }
    }
}

@Composable
private fun DelayControl(label: String, microseconds: Long, change: (Long) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f))
        ActionIcon(Icons.Default.Remove, "Decrease by 50 ms") { change(microseconds - 50000) }
        Text("${microseconds / 1000} ms", Modifier.width(80.dp), fontSize = 12.sp)
        ActionIcon(Icons.Default.Add, "Increase by 50 ms") { change(microseconds + 50000) }
        TextButton({ change(0) }) { Text("Reset") }
    }
}

@Composable
private fun PlaybackSettings() {
    val player = VlcPlayer.mediaPlayer
    SectionLabel("PLAYBACK SPEED")
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f).forEach { speed ->
            Pill("${speed}×", LibraryStore.speed == speed) { DesktopPlayback.speed(speed) }
        }
    }
    SectionLabel("ASPECT RATIO")
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf("Original", "16:9", "4:3", "21:9", "1:1").forEach { aspect ->
            Pill(aspect, LibraryStore.aspect == aspect) {
                player?.video()?.setAspectRatio(aspect.takeUnless { it == "Original" }); LibraryStore.aspect = aspect
            }
        }
    }
    SectionLabel("VIDEO")
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedButton({ player?.controls()?.setPause(true); player?.controls()?.nextFrame() }) { Text("Next frame") }
        OutlinedButton({ DesktopPlayback.snapshot() }) { Text("Save snapshot") }
        OutlinedButton({ DesktopPlayback.stop(); LibraryStore.dialog = null }) { Text("Stop") }
    }
    SectionLabel("CHAPTERS")
    val chapters = remember(PlayerStore.activeId) { player?.chapters()?.descriptions().orEmpty() }
    if (chapters.isEmpty()) Text("This media has no chapters.", color = Theme.TextMuted)
    chapters.forEachIndexed { index, chapter ->
        TextButton({ player?.chapters()?.setChapter(index) }, modifier = Modifier.fillMaxWidth()) {
            Text(chapter.name()?.takeIf { it.isNotBlank() } ?: "Chapter ${index + 1}", Modifier.weight(1f))
            Text(formatTime(chapter.offset()), color = Theme.TextMuted)
        }
    }
    SectionLabel("SLEEP TIMER")
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { delay(1000); now = System.currentTimeMillis() } }
    if (LibraryStore.sleepDeadline > 0) Text("Pausing in ${formatTime(LibraryStore.sleepDeadline - now)}", color = Theme.Accent)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(15, 30, 60, 90).forEach { minutes ->
            Pill("${minutes} min") { LibraryStore.sleepDeadline = System.currentTimeMillis() + minutes * 60000L }
        }
        Pill("Off", LibraryStore.sleepDeadline == 0L) { LibraryStore.sleepDeadline = 0 }
    }
    SectionLabel("VOLUME")
    Row(verticalAlignment = Alignment.CenterVertically) {
        Slider(PlayerStore.volume.toFloat(), { VlcPlayer.setVolume(it.toInt()) }, valueRange = 0f..100f,
            onValueChangeFinished = { LibraryStore.save() }, modifier = Modifier.weight(1f))
        Text("${PlayerStore.volume}%", Modifier.padding(start = 14.dp))
    }
}

@Composable
private fun AppSettings() {
    SectionLabel("PLAYBACK")
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("Resume where you left off")
            Text("Remember the position of recently played media.", color = Theme.TextMuted, fontSize = 12.sp)
        }
        Switch(LibraryStore.resume, { LibraryStore.resume = it; LibraryStore.save() })
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Show media as a grid", Modifier.weight(1f))
        Switch(LibraryStore.grid, { LibraryStore.grid = it; LibraryStore.save() })
    }
    SectionLabel("AUDIO")
    Text("Output device", color = Theme.TextSecondary)
    val devices = remember { VlcPlayer.getAudioDevices().filter { it.id.isNotBlank() }.distinctBy { it.id } }
    Row(verticalAlignment = Alignment.CenterVertically) {
        RadioButton(PlayerStore.audioDeviceId.isEmpty(), { VlcPlayer.setAudioDevice("") }); Text("System default")
    }
    devices.forEach { device ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            RadioButton(PlayerStore.audioDeviceId == device.id, { VlcPlayer.setAudioDevice(device.id) })
            Text(device.name, fontSize = 12.sp)
        }
    }
    SectionLabel("KEYBOARD")
    ShortcutList()
    SectionLabel("ABOUT")
    Image(androidx.compose.ui.res.painterResource("icon.png"), "CineK", Modifier.size(64.dp))
    Text("CineK", color = Theme.Accent, fontSize = 20.sp)
    Text("Version 1.0.0", color = Theme.TextSecondary, fontSize = 12.sp)
}

@Composable
private fun ShortcutList() {
    listOf("Space" to "Play / pause", "← / →" to "Seek 10 seconds", "↑ / ↓" to "Volume",
        "M" to "Mute", "F / Esc" to "Enter / leave fullscreen", "Ctrl + O" to "Open media",
        "Ctrl + L" to "Open stream", "Ctrl + ," to "Settings", "N / P" to "Next / previous",
        "S" to "Stop", "Ctrl + Q" to "Show / hide queue").forEach { (key, action) ->
        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
            Text(key, Modifier.width(145.dp), color = Theme.Accent, fontSize = 12.sp)
            Text(action, color = Theme.TextSecondary, fontSize = 12.sp)
        }
    }
}
