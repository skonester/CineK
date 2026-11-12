package com.sasami.vlc

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.*
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.rememberWindowState
import com.sasami.vlc.player.DesktopPlayback
import com.sasami.vlc.player.VlcPlayer
import com.sasami.vlc.store.*
import com.sasami.vlc.theme.Theme
import com.sasami.vlc.ui.*
import com.sasami.vlc.utils.DesktopFiles
import com.sasami.vlc.utils.DesktopLaunchArguments
import dev.nucleusframework.application.DecoratedWindow
import dev.nucleusframework.application.nucleusApplication
import kotlinx.coroutines.delay
import java.io.File

/** Exposed to the opt-in smoke harness; all UI work is dispatched onto Tao's event loop. */
object DesktopRuntime {
    var window: dev.nucleusframework.application.NucleusWindow? = null
        internal set
    var close: (() -> Unit)? = null
        internal set
}

private fun shortcut(event: KeyEvent): Boolean {
    val library = LibraryStore
    if (event.type != KeyEventType.KeyDown || library.dialog != null) return false
    PlayerChrome.interact()
    return when {
        event.isCtrlPressed && event.key == Key.O -> { DesktopPlayback.openFiles(); true }
        event.isCtrlPressed && event.key == Key.L -> { library.dialog = "stream"; true }
        event.isCtrlPressed && event.key == Key.Comma -> { library.dialog = "settings"; true }
        event.isCtrlPressed && event.key == Key.Q -> { PlayerStore.isPlayListCollapsed = !PlayerStore.isPlayListCollapsed; true }
        event.key == Key.Escape -> { library.fullscreen = false; true }
        !library.showLibrary && !event.isCtrlPressed && !event.isAltPressed -> when (event.key) {
            Key.Spacebar -> { DesktopPlayback.toggle(); true }
            Key.DirectionLeft -> { DesktopPlayback.skip(-10000); true }
            Key.DirectionRight -> { DesktopPlayback.skip(10000); true }
            Key.DirectionUp -> { DesktopPlayback.volume(PlayerStore.volume + 5); true }
            Key.DirectionDown -> { DesktopPlayback.volume(PlayerStore.volume - 5); true }
            Key.M -> { DesktopPlayback.mute(); true }
            Key.F -> { library.fullscreen = !library.fullscreen; true }
            Key.N -> { DesktopPlayback.advance(); true }
            Key.P -> { DesktopPlayback.previous(); true }
            Key.S -> { DesktopPlayback.stop(); true }
            else -> false
        }
        else -> false
    }
}

fun main(args: Array<String>) {
    // Exercise the production downloader in the packaged native image without opening a window.
    if (args.firstOrNull() == "--verify-live-playlist") {
        val location = args.getOrNull(1) ?: error("Provide an M3U playlist URL or path.")
        val channels = M3uChannels.parse(readChannelPlaylist(location), "verification", location)
        check(channels.isNotEmpty()) { "The playlist has no playable channels." }
        println("PASS: downloaded and parsed ${channels.size} live channels")
        return
    }
    // Preserve independent Explorer launches until all file arguments can be relayed,
    // rather than allowing a single-instance URI handler to drop later filenames.
    nucleusApplication(args, enableSingleInstance = false) {
        val launchFiles = remember { DesktopLaunchArguments.resolve(args) }
        val state = rememberWindowState(width = 1280.dp, height = 820.dp)
        var startupComplete by remember { mutableStateOf(false) }
        fun closeApp() {
            DesktopPlayback.checkpoint()
            LibraryStore.save()
            PlayerStore.save()
            VlcPlayer.release()
            exitApplication()
        }
        DecoratedWindow(onCloseRequest = ::closeApp, state = state, title = "CineK",
            icon = painterResource("icon.png"), minimumSize = DpSize(1000.dp, 650.dp),
            onPreviewKeyEvent = ::shortcut) {
            val owner = nucleusWindow
            DisposableEffect(owner) {
                DesktopRuntime.window = owner
                DesktopRuntime.close = ::closeApp
                DesktopFiles.owner = owner
                onDispose { DesktopFiles.owner = null; DesktopRuntime.window = null; DesktopRuntime.close = null }
            }
            LaunchedEffect(LibraryStore.fullscreen) { owner.setFullscreen(LibraryStore.fullscreen) }
            LaunchedEffect(owner) { owner.fullscreenFlow.collect { LibraryStore.fullscreen = it } }
            ObservePlayerChrome()
            LaunchedEffect(Unit) {
                PlayerStore.load()
                LibraryStore.load()
                LiveChannels.load()
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { VlcPlayer.init() }
                DesktopPlayback.attach()
                startupComplete = true
                if (launchFiles.isNotEmpty()) DesktopPlayback.importFiles(launchFiles.map(::File), playFirst = true)
            }
            LaunchedEffect(PlayerStore.activeId) {
                while (true) { delay(5000); DesktopPlayback.checkpoint() }
            }
            LaunchedEffect(LibraryStore.sleepDeadline) {
                if (LibraryStore.sleepDeadline > 0) {
                    delay((LibraryStore.sleepDeadline - System.currentTimeMillis()).coerceAtLeast(0))
                    VlcPlayer.pause(); LibraryStore.sleepDeadline = 0; LibraryStore.message = "Sleep timer: playback paused"
                }
            }
            LaunchedEffect(LibraryStore.message) {
                if (LibraryStore.message != null) { delay(6000); LibraryStore.message = null }
            }
            MaterialTheme(colors = Theme.colors, typography = Theme.typography, shapes = Theme.shapes) {
                Box(Modifier.fillMaxSize().background(Theme.Bg).observePlayerPointer().mediaDropTarget()) {
                    if (!startupComplete) {
                        Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                            androidx.compose.foundation.Image(painterResource("icon.png"), "CineK", Modifier.size(128.dp))
                            Spacer(Modifier.height(24.dp))
                            CircularProgressIndicator(color = Theme.Accent)
                        }
                    } else Column(Modifier.fillMaxSize()) {
                        if (!LibraryStore.fullscreen) TitleBar(onClose = ::closeApp)
                        Box(Modifier.weight(1f)) { VideoPlayerContent() }
                        if (DesktopPlayback.error != null) {
                            Row(Modifier.fillMaxWidth().background(Theme.AccentSoft).padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically) {
                                Text(DesktopPlayback.error!!, Modifier.weight(1f), color = Theme.TextPrimary)
                                TextButton({ DesktopPlayback.stop() }) { Text("Back to library") }
                            }
                        }
                        if ((LibraryStore.message != null || PlayerStore.isDraggingOver) && (!LibraryStore.fullscreen || PlayerChrome.visible)) {
                            Text(if (PlayerStore.isDraggingOver) "Drop media files or folders to add them" else LibraryStore.message!!,
                                Modifier.fillMaxWidth().background(Theme.AccentSoft).padding(10.dp), color = Theme.Accent)
                        }
                        if (!LibraryStore.fullscreen || PlayerChrome.visible) PlayerBar()
                    }
                    DesktopDialogs()
                }
            }
        }
    }
}
