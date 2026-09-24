package com.sasami.vlc.ui

import androidx.compose.runtime.*
import com.sasami.vlc.store.LibraryStore
import com.sasami.vlc.store.PlayerStore
import com.sasami.vlc.player.VlcPlayer
import kotlinx.coroutines.delay
import java.awt.*
import java.awt.event.AWTEventListener
import java.awt.event.MouseEvent
import java.awt.image.BufferedImage
import javax.swing.SwingUtilities

/** Runtime state shared by the window observer and the transport bar. */
object PlayerChrome {
    const val IDLE_MILLIS = 2500L
    var visible by mutableStateOf(true)
        private set
    var toolbarBounds: Rectangle? = null
    private var lastActivity = System.nanoTime()

    fun interact() { lastActivity = System.nanoTime(); visible = true }
    fun hideIfIdle() {
        if ((System.nanoTime() - lastActivity) / 1_000_000 >= IDLE_MILLIS) visible = false
    }
}

@Composable
fun ObservePlayerChrome(window: Window) {
    var pointerDown by remember { mutableStateOf(false) }
    val hiddenCursor = remember {
        Toolkit.getDefaultToolkit().createCustomCursor(BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB), Point(), "Hidden")
    }
    DisposableEffect(window) {
        val listener = AWTEventListener { event ->
            if (event is MouseEvent) {
                val belongsToWindow = SwingUtilities.getWindowAncestor(event.component) === window
                if (event.id == MouseEvent.MOUSE_RELEASED) pointerDown = false
                if (belongsToWindow) when (event.id) {
                    MouseEvent.MOUSE_PRESSED -> { pointerDown = true; PlayerChrome.interact() }
                    MouseEvent.MOUSE_MOVED, MouseEvent.MOUSE_DRAGGED, MouseEvent.MOUSE_WHEEL -> PlayerChrome.interact()
                }
            }
        }
        val toolkit = Toolkit.getDefaultToolkit()
        toolkit.addAWTEventListener(listener,
            AWTEvent.MOUSE_EVENT_MASK or AWTEvent.MOUSE_MOTION_EVENT_MASK or AWTEvent.MOUSE_WHEEL_EVENT_MASK)
        onDispose {
            toolkit.removeAWTEventListener(listener)
            window.cursor = Cursor.getDefaultCursor()
            VlcPlayer.mediaPlayerComponent?.videoSurfaceComponent()?.cursor = Cursor.getDefaultCursor()
            PlayerChrome.interact()
        }
    }
    val autoHide = LibraryStore.fullscreen && !LibraryStore.showLibrary && PlayerStore.activeId.isNotEmpty()
    LaunchedEffect(window, autoHide) {
        PlayerChrome.interact()
        var previousPointer = MouseInfo.getPointerInfo()?.location
        while (true) {
            delay(100)
            // Polling also sees movement over VLC's native child window, which may
            // consume Windows mouse messages before AWT listeners can receive them.
            val pointer = MouseInfo.getPointerInfo()?.location
            val origin = if (window.isShowing) window.locationOnScreen else window.location
            val insideWindow = pointer != null && Rectangle(origin, window.size).contains(pointer)
            if (pointer != previousPointer && insideWindow) PlayerChrome.interact()
            previousPointer = pointer
            val overToolbar = pointer != null && PlayerChrome.visible &&
                PlayerChrome.toolbarBounds?.contains(Point(pointer.x - origin.x, pointer.y - origin.y)) == true
            if (!autoHide || overToolbar || pointerDown || LibraryStore.dialog != null || PlayerStore.isDraggingOver) {
                PlayerChrome.interact()
            } else PlayerChrome.hideIfIdle()
            val cursor = if (autoHide && !PlayerChrome.visible) hiddenCursor else Cursor.getDefaultCursor()
            if (window.cursor !== cursor) window.cursor = cursor
            VlcPlayer.mediaPlayerComponent?.videoSurfaceComponent()?.let { canvas ->
                if (canvas.cursor !== cursor) canvas.cursor = cursor
            }
        }
    }
}
