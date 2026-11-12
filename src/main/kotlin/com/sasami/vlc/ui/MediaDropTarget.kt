package com.sasami.vlc.ui

import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draganddrop.*
import com.sasami.vlc.player.DesktopPlayback
import com.sasami.vlc.store.PlayerStore
import java.awt.datatransfer.DataFlavor
import java.io.File

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun Modifier.mediaDropTarget(): Modifier {
    val target = remember {
        object : DragAndDropTarget {
            override fun onEntered(event: DragAndDropEvent) { PlayerStore.isDraggingOver = true }
            override fun onExited(event: DragAndDropEvent) { PlayerStore.isDraggingOver = false }
            override fun onEnded(event: DragAndDropEvent) { PlayerStore.isDraggingOver = false }
            override fun onDrop(event: DragAndDropEvent): Boolean {
                PlayerStore.isDraggingOver = false
                val files = runCatching {
                    (event.awtTransferable.getTransferData(DataFlavor.javaFileListFlavor) as? List<*>)?.filterIsInstance<File>()
                }.getOrNull().orEmpty()
                if (files.isEmpty()) return false
                DesktopPlayback.importFiles(files, playFirst = true)
                return true
            }
        }
    }
    return dragAndDropTarget(shouldStartDragAndDrop = {
        it.awtTransferable.isDataFlavorSupported(DataFlavor.javaFileListFlavor)
    }, target = target)
}
