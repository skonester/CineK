package com.sasami.vlc.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.WindowScope
import com.sasami.vlc.store.PlayerStore
import com.sasami.vlc.store.LibraryStore
import com.sasami.vlc.player.VlcPlayer
import com.sasami.vlc.player.DesktopPlayback
import com.sasami.vlc.theme.Theme
import com.sasami.vlc.utils.I18n
import java.io.File
import javax.swing.JFrame

@Composable
fun WindowScope.TitleBar(onClose: () -> Unit) {
    val jFrame = (window as? JFrame)
    var showBackendMenu by remember { mutableStateOf(false) }

    val displayTitle = remember(PlayerStore.activeId, PlayerStore.playerList) {
        if (PlayerStore.activeId.isEmpty()) {
            "CineK"
        } else {
            val name = (DesktopPlayback.transientItems + PlayerStore.playerList).find { it.path == PlayerStore.activeId }?.name
                ?: File(PlayerStore.activeId).name
            name
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(42.dp)
            .background(Theme.SurfaceBar)
            .pointerInput(Unit) {
                if (jFrame != null) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        val startPoint = java.awt.MouseInfo.getPointerInfo().location
                        val startLoc = jFrame.location
                        drag(down.id) { change ->
                            change.consume()
                            val currentPoint = java.awt.MouseInfo.getPointerInfo().location
                            jFrame.setLocation(
                                startLoc.x + (currentPoint.x - startPoint.x),
                                startLoc.y + (currentPoint.y - startPoint.y)
                            )
                        }
                    }
                }
            }
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        androidx.compose.foundation.Image(
            painter = androidx.compose.ui.res.painterResource("icon.png"),
            contentDescription = "CineK",
            modifier = Modifier.padding(end = 8.dp).size(26.dp)
        )
        Text(
            text = displayTitle, color = Theme.TextPrimary,
            style = Theme.typography.body1.copy(fontWeight = FontWeight.Medium),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )

        // Actions
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // VLC video output selector
            Box {
                IconButton(
                    onClick = { showBackendMenu = true },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Tune,
                        contentDescription = "Video backend",
                        tint = Theme.TextSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                }

                AppDropdownMenu(
                    expanded = showBackendMenu,
                    onDismissRequest = { showBackendMenu = false }
                ) {
                    Text("VIDEO BACKEND", modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        color = Theme.TextMuted, fontSize = 11.sp)
                    VlcPlayer.availableVideoBackends().forEach { backend ->
                        DropdownMenuItem(onClick = {
                            PlayerStore.videoOutput = backend.key
                            PlayerStore.save()
                            showBackendMenu = false
                            if (backend != VlcPlayer.activeVideoBackend)
                                LibraryStore.message = "Restart CineK to use ${backend.label}."
                        }) {
                            Text(backend.label, modifier = Modifier.weight(1f),
                                color = if (PlayerStore.videoOutput == backend.key) Theme.Accent else Theme.TextPrimary)
                            if (PlayerStore.videoOutput == backend.key) Icon(Icons.Default.Check, null,
                                Modifier.size(16.dp), tint = Theme.Accent)
                        }
                    }
                    if (PlayerStore.videoOutput != VlcPlayer.activeVideoBackend.key)
                        Text("Restart CineK to apply", modifier = Modifier.padding(16.dp, 7.dp),
                            color = Theme.TextMuted, fontSize = 11.sp)
                }
            }

            // About Button
            IconButton(
                onClick = { com.sasami.vlc.store.LibraryStore.dialog = "settings" },
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = I18n.t("about.title"),
                    tint = Theme.TextSecondary,
                    modifier = Modifier.size(16.dp)
                )
            }

            // Minimize Button (using Remove icon)
            IconButton(
                onClick = { jFrame?.extendedState = JFrame.ICONIFIED },
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Remove,
                    contentDescription = I18n.t("titleBar.minimize"),
                    tint = Theme.TextSecondary,
                    modifier = Modifier.size(16.dp)
                )
            }

            // Maximize Button
            IconButton(
                onClick = {
                    jFrame?.let {
                        if (it.extendedState == JFrame.MAXIMIZED_BOTH) {
                            it.extendedState = JFrame.NORMAL
                        } else {
                            it.extendedState = JFrame.MAXIMIZED_BOTH
                        }
                    }
                },
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.CropSquare,
                    contentDescription = I18n.t("titleBar.maximize"),
                    tint = Theme.TextSecondary,
                    modifier = Modifier.size(16.dp)
                )
            }

            // Close Button
            IconButton(
                onClick = { onClose() },
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = I18n.t("titleBar.close"),
                    tint = Theme.Danger,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}
