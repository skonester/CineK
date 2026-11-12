package com.sasami.vlc.ui

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.*
import com.sasami.vlc.store.LibraryStore
import com.sasami.vlc.store.PlayerStore
import kotlinx.coroutines.delay
import dev.nucleusframework.application.LocalNucleusWindow

object PlayerChrome {
    const val IDLE_MILLIS = 2500L
    var visible by mutableStateOf(true)
        private set
    var toolbarBounds: Rect? = null
    var pointer = Offset.Unspecified
    var pointerDown = false
    private var lastActivity = System.nanoTime()
    fun interact() { lastActivity = System.nanoTime(); visible = true }
    fun hideIfIdle() {
        if ((System.nanoTime() - lastActivity) / 1_000_000 >= IDLE_MILLIS) visible = false
    }
}

fun Modifier.observePlayerPointer(): Modifier = pointerInput(Unit) {
    awaitPointerEventScope {
        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            event.changes.firstOrNull()?.let { PlayerChrome.pointer = it.position }
            PlayerChrome.pointerDown = event.changes.any { it.pressed }
            if (event.type in listOf(PointerEventType.Move, PointerEventType.Press, PointerEventType.Scroll))
                PlayerChrome.interact()
        }
    }
}

@Composable
fun ObservePlayerChrome() {
    val window = LocalNucleusWindow.current
    val focused by window.focusFlow.collectAsState()
    val autoHide = focused && LibraryStore.fullscreen && !LibraryStore.showLibrary && PlayerStore.activeId.isNotEmpty()
    LaunchedEffect(window, autoHide) {
        PlayerChrome.interact()
        var previous = WindowsPointer.position(window.unsafe.taoWindow!!.nativeHandle)
        try {
            while (true) {
                delay(100)
                val position = WindowsPointer.position(window.unsafe.taoWindow!!.nativeHandle)
                if (position != previous && position != null) PlayerChrome.interact()
                previous = position
                val overToolbar = position != null && PlayerChrome.visible && PlayerChrome.toolbarBounds?.contains(position) == true
                if (!autoHide || position == null || overToolbar || WindowsPointer.pressed() || LibraryStore.dialog != null || PlayerStore.isDraggingOver)
                    PlayerChrome.interact()
                else PlayerChrome.hideIfIdle()
                WindowsPointer.hideCursor(autoHide && !PlayerChrome.visible)
            }
        } finally {
            WindowsPointer.hideCursor(false)
        }
    }
}
