package com.sasami.vlc

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowScope
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import androidx.compose.ui.res.painterResource
import com.sasami.vlc.player.VlcPlayer
import com.sasami.vlc.store.PlayerItem
import com.sasami.vlc.store.PlayerStore
import com.sasami.vlc.theme.Theme
import com.sasami.vlc.ui.PlayerBar
import com.sasami.vlc.ui.TitleBar
import com.sasami.vlc.ui.VideoPlayerContent
import com.sasami.vlc.utils.FileUtils
import java.awt.Cursor
import java.awt.datatransfer.DataFlavor
import java.awt.dnd.DnDConstants
import java.awt.dnd.DropTarget
import java.awt.dnd.DropTargetAdapter
import java.awt.dnd.DropTargetDragEvent
import java.awt.dnd.DropTargetDropEvent
import java.io.File
import javax.swing.JFrame

fun registerDragAndDrop(frame: JFrame) {
    val dropTarget = object : DropTargetAdapter() {
        override fun dragEnter(dtde: DropTargetDragEvent) {
            if (dtde.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
                dtde.acceptDrag(DnDConstants.ACTION_COPY)
                PlayerStore.isDraggingOver = true
            } else {
                dtde.rejectDrag()
            }
        }

        override fun dragExit(dte: java.awt.dnd.DropTargetEvent) {
            PlayerStore.isDraggingOver = false
        }

        override fun drop(dtde: DropTargetDropEvent) {
            PlayerStore.isDraggingOver = false
            try {
                if (dtde.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
                    dtde.acceptDrop(DnDConstants.ACTION_COPY)
                    val transferable = dtde.transferable
                    val files = transferable.getTransferData(DataFlavor.javaFileListFlavor) as? List<*>
                    if (files != null) {
                        val fileList = files.mapNotNull { it as? File }
                        if (fileList.isNotEmpty()) {
                            com.sasami.vlc.player.DesktopPlayback.importFiles(fileList, playFirst = true)
                        }
                    }
                    dtde.dropComplete(true)
                } else {
                    dtde.rejectDrop()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                dtde.dropComplete(false)
            }
        }
    }
    DropTarget(frame, DnDConstants.ACTION_COPY, dropTarget, true)
}

@Composable
fun WindowScope.WindowResizer() {
    val jFrame = window as? JFrame ?: return
    val minWidth = 1000
    val minHeight = 650
    val edgeSize = 6.dp

    Box(modifier = Modifier.fillMaxSize()) {
        // Top edge
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(edgeSize)
                .align(Alignment.TopCenter)
                .pointerHoverIcon(PointerIcon(Cursor.getPredefinedCursor(Cursor.N_RESIZE_CURSOR)))
                .pointerInput(Unit) {
                    detectDragGestures { change, _ ->
                        change.consume()
                        val screenPoint = java.awt.MouseInfo.getPointerInfo().location
                        val bottomY = jFrame.y + jFrame.height
                        val newY = screenPoint.y
                        val newH = (bottomY - newY).coerceAtLeast(minHeight)
                        if (newH != jFrame.height) {
                            jFrame.setLocation(jFrame.x, bottomY - newH)
                            jFrame.setSize(jFrame.width, newH)
                        }
                    }
                }
        )

        // Bottom edge
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(edgeSize)
                .align(Alignment.BottomCenter)
                .pointerHoverIcon(PointerIcon(Cursor.getPredefinedCursor(Cursor.S_RESIZE_CURSOR)))
                .pointerInput(Unit) {
                    detectDragGestures { change, _ ->
                        change.consume()
                        val screenPoint = java.awt.MouseInfo.getPointerInfo().location
                        val newH = (screenPoint.y - jFrame.y).coerceAtLeast(minHeight)
                        jFrame.setSize(jFrame.width, newH)
                    }
                }
        )

        // Left edge
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .width(edgeSize)
                .align(Alignment.CenterStart)
                .pointerHoverIcon(PointerIcon(Cursor.getPredefinedCursor(Cursor.W_RESIZE_CURSOR)))
                .pointerInput(Unit) {
                    detectDragGestures { change, _ ->
                        change.consume()
                        val screenPoint = java.awt.MouseInfo.getPointerInfo().location
                        val rightX = jFrame.x + jFrame.width
                        val newX = screenPoint.x
                        val newW = (rightX - newX).coerceAtLeast(minWidth)
                        if (newW != jFrame.width) {
                            jFrame.setLocation(rightX - newW, jFrame.y)
                            jFrame.setSize(newW, jFrame.height)
                        }
                    }
                }
        )

        // Right edge
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .width(edgeSize)
                .align(Alignment.CenterEnd)
                .pointerHoverIcon(PointerIcon(Cursor.getPredefinedCursor(Cursor.E_RESIZE_CURSOR)))
                .pointerInput(Unit) {
                    detectDragGestures { change, _ ->
                        change.consume()
                        val screenPoint = java.awt.MouseInfo.getPointerInfo().location
                        val newW = (screenPoint.x - jFrame.x).coerceAtLeast(minWidth)
                        jFrame.setSize(newW, jFrame.height)
                    }
                }
        )

        // Top-Left corner
        Box(
            modifier = Modifier
                .size(edgeSize * 2)
                .align(Alignment.TopStart)
                .pointerHoverIcon(PointerIcon(Cursor.getPredefinedCursor(Cursor.NW_RESIZE_CURSOR)))
                .pointerInput(Unit) {
                    detectDragGestures { change, _ ->
                        change.consume()
                        val screenPoint = java.awt.MouseInfo.getPointerInfo().location
                        val rightX = jFrame.x + jFrame.width
                        val bottomY = jFrame.y + jFrame.height
                        val newW = (rightX - screenPoint.x).coerceAtLeast(minWidth)
                        val newH = (bottomY - screenPoint.y).coerceAtLeast(minHeight)
                        jFrame.setLocation(rightX - newW, bottomY - newH)
                        jFrame.setSize(newW, newH)
                    }
                }
        )

        // Top-Right corner
        Box(
            modifier = Modifier
                .size(edgeSize * 2)
                .align(Alignment.TopEnd)
                .pointerHoverIcon(PointerIcon(Cursor.getPredefinedCursor(Cursor.NE_RESIZE_CURSOR)))
                .pointerInput(Unit) {
                    detectDragGestures { change, _ ->
                        change.consume()
                        val screenPoint = java.awt.MouseInfo.getPointerInfo().location
                        val bottomY = jFrame.y + jFrame.height
                        val newW = (screenPoint.x - jFrame.x).coerceAtLeast(minWidth)
                        val newH = (bottomY - screenPoint.y).coerceAtLeast(minHeight)
                        jFrame.setLocation(jFrame.x, bottomY - newH)
                        jFrame.setSize(newW, newH)
                    }
                }
        )

        // Bottom-Left corner
        Box(
            modifier = Modifier
                .size(edgeSize * 2)
                .align(Alignment.BottomStart)
                .pointerHoverIcon(PointerIcon(Cursor.getPredefinedCursor(Cursor.SW_RESIZE_CURSOR)))
                .pointerInput(Unit) {
                    detectDragGestures { change, _ ->
                        change.consume()
                        val screenPoint = java.awt.MouseInfo.getPointerInfo().location
                        val rightX = jFrame.x + jFrame.width
                        val newW = (rightX - screenPoint.x).coerceAtLeast(minWidth)
                        val newH = (screenPoint.y - jFrame.y).coerceAtLeast(minHeight)
                        jFrame.setLocation(rightX - newW, jFrame.y)
                        jFrame.setSize(newW, newH)
                    }
                }
        )

        // Bottom-Right corner
        Box(
            modifier = Modifier
                .size(edgeSize * 2)
                .align(Alignment.BottomEnd)
                .pointerHoverIcon(PointerIcon(Cursor.getPredefinedCursor(Cursor.SE_RESIZE_CURSOR)))
                .pointerInput(Unit) {
                    detectDragGestures { change, _ ->
                        change.consume()
                        val screenPoint = java.awt.MouseInfo.getPointerInfo().location
                        val newW = (screenPoint.x - jFrame.x).coerceAtLeast(minWidth)
                        val newH = (screenPoint.y - jFrame.y).coerceAtLeast(minHeight)
                        jFrame.setSize(newW, newH)
                    }
                }
        )
    }
}

fun main(args: Array<String>) {
    val launchFiles = com.sasami.vlc.utils.DesktopLaunchArguments.resolve(args)
    val appResourcesDir = System.getProperty("compose.application.resources.dir")
    val vlcPath = if (appResourcesDir != null && File(appResourcesDir, "vlc").exists())
        File(appResourcesDir, "vlc").absolutePath else File("resources/common/vlc").absolutePath
    if (File(vlcPath).exists()) System.setProperty("jna.library.path", vlcPath)

    application {
        val state = rememberWindowState(width = 1280.dp, height = 820.dp)
        fun closeApp() {
            com.sasami.vlc.player.DesktopPlayback.checkpoint()
            com.sasami.vlc.store.LibraryStore.save()
            PlayerStore.save()
            VlcPlayer.release()
            exitApplication()
        }
        // Hidden until start-up finishes; showing it is what closes the splash logo.
        var startupComplete by remember { mutableStateOf(false) }
        Window(onCloseRequest = { closeApp() }, state = state, title = "CineK",
            icon = painterResource("icon.png"), undecorated = true, visible = startupComplete) {
            val library = com.sasami.vlc.store.LibraryStore
            val playback = com.sasami.vlc.player.DesktopPlayback
            val jFrame = window as? JFrame
            com.sasami.vlc.ui.ObservePlayerChrome(window)
            val fullscreenWindow = androidx.compose.runtime.remember(window) { com.sasami.vlc.ui.BorderlessFullscreen(window) }
            LaunchedEffect(library.fullscreen) { fullscreenWindow.setEnabled(library.fullscreen) }
            LaunchedEffect(Unit) {
                try {
                    PlayerStore.load()
                    library.load()
                    com.sasami.vlc.store.LiveChannels.load()
                    VlcPlayer.init()
                    playback.attach()
                    jFrame?.let { it.minimumSize = java.awt.Dimension(1000, 650); registerDragAndDrop(it) }
                    com.sasami.vlc.ui.StartupSplash.awaitMinimum()
                } finally {
                    startupComplete = true
                }
                // Playing needs VLC's native video surface, which exists once the window shows.
                kotlinx.coroutines.withTimeoutOrNull(5_000) { while (!window.isShowing) kotlinx.coroutines.delay(16) }
                if (launchFiles.isNotEmpty()) playback.importFiles(launchFiles.map(::File), playFirst = true)
            }

            LaunchedEffect(PlayerStore.activeId) {
                while (true) { kotlinx.coroutines.delay(5000); playback.checkpoint() }
            }
            LaunchedEffect(library.sleepDeadline) {
                if (library.sleepDeadline > 0) {
                    kotlinx.coroutines.delay((library.sleepDeadline - System.currentTimeMillis()).coerceAtLeast(0))
                    VlcPlayer.pause(); library.sleepDeadline = 0; library.message = "Sleep timer: playback paused"
                }
            }
            LaunchedEffect(library.message) {
                if (library.message != null) { kotlinx.coroutines.delay(6000); library.message = null }
            }
            androidx.compose.runtime.DisposableEffect(window) {
                val dispatcher = java.awt.KeyEventDispatcher { event ->
                    val focused = java.awt.KeyboardFocusManager.getCurrentKeyboardFocusManager().focusedWindow
                    if (focused !== window || event.id != java.awt.event.KeyEvent.KEY_PRESSED || library.dialog != null)
                        false
                    else {
                        com.sasami.vlc.ui.PlayerChrome.interact()
                        val key = event.keyCode
                        val ctrl = event.isControlDown
                        when {
                            ctrl && key == java.awt.event.KeyEvent.VK_O -> { playback.openFiles(); true }
                            ctrl && key == java.awt.event.KeyEvent.VK_L -> { library.dialog = "stream"; true }
                            ctrl && key == java.awt.event.KeyEvent.VK_COMMA -> { library.dialog = "settings"; true }
                            ctrl && key == java.awt.event.KeyEvent.VK_Q -> { PlayerStore.isPlayListCollapsed = !PlayerStore.isPlayListCollapsed; true }
                            key == java.awt.event.KeyEvent.VK_ESCAPE -> { library.fullscreen = false; true }
                            !library.showLibrary && !ctrl && !event.isAltDown -> when (key) {
                                java.awt.event.KeyEvent.VK_SPACE -> { playback.toggle(); true }
                                java.awt.event.KeyEvent.VK_LEFT -> { playback.skip(-10000); true }
                                java.awt.event.KeyEvent.VK_RIGHT -> { playback.skip(10000); true }
                                java.awt.event.KeyEvent.VK_UP -> { playback.volume(PlayerStore.volume + 5); true }
                                java.awt.event.KeyEvent.VK_DOWN -> { playback.volume(PlayerStore.volume - 5); true }
                                java.awt.event.KeyEvent.VK_M -> { playback.mute(); true }
                                java.awt.event.KeyEvent.VK_F -> { library.fullscreen = !library.fullscreen; true }
                                java.awt.event.KeyEvent.VK_N -> { playback.advance(); true }
                                java.awt.event.KeyEvent.VK_P -> { playback.previous(); true }
                                java.awt.event.KeyEvent.VK_S -> { playback.stop(); true }
                                else -> false
                            }
                            else -> false
                        }
                    }
                }
                java.awt.KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(dispatcher)
                onDispose { java.awt.KeyboardFocusManager.getCurrentKeyboardFocusManager().removeKeyEventDispatcher(dispatcher) }
            }

            MaterialTheme(colors = Theme.colors, typography = Theme.typography, shapes = Theme.shapes) {
                Box(Modifier.fillMaxSize().background(Theme.Bg)) {
                    Column(Modifier.fillMaxSize()) {
                        if (!library.fullscreen) TitleBar(onClose = { closeApp() })
                        Box(Modifier.weight(1f)) { VideoPlayerContent() }
                        if (playback.error != null) {
                            Row(Modifier.fillMaxWidth().background(Theme.AccentSoft).padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically) {
                                androidx.compose.material.Text(playback.error!!, Modifier.weight(1f), color = Theme.TextPrimary)
                                androidx.compose.material.TextButton({ playback.stop() }) { androidx.compose.material.Text("Back to library") }
                            }
                        }
                        if ((library.message != null || PlayerStore.isDraggingOver) && (!library.fullscreen || com.sasami.vlc.ui.PlayerChrome.visible)) {
                            androidx.compose.material.Text(if (PlayerStore.isDraggingOver) "Drop media files or folders to add them" else library.message!!,
                                Modifier.fillMaxWidth().background(Theme.AccentSoft).padding(10.dp), color = Theme.Accent)
                        }
                        if (!library.fullscreen || com.sasami.vlc.ui.PlayerChrome.visible) PlayerBar()
                    }
                    if (!library.fullscreen) WindowResizer()
                    com.sasami.vlc.ui.DesktopDialogs()

                }
            }
        }
    }
}
