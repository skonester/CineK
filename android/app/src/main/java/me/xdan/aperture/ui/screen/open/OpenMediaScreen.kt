package me.xdan.aperture.ui.screen.open

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FileOpen
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.SurfaceDefaults
import androidx.tv.material3.Text
import me.xdan.aperture.ui.component.AccessStatusRow
import me.xdan.aperture.ui.component.LocalLibraryAccess
import me.xdan.aperture.ui.component.actionLabel
import me.xdan.aperture.ui.component.guidance
import me.xdan.aperture.ui.component.musicStatus
import me.xdan.aperture.ui.component.touch.Button
import me.xdan.aperture.ui.component.touch.OutlinedButton
import me.xdan.aperture.ui.component.touch.Surface
import me.xdan.aperture.ui.component.videoStatus

/**
 * Everything that gets media into CineK: play a file or stream directly, or fix
 * what the library can see and scan again.
 */
@Composable
fun OpenMediaScreen(
    isScanning: Boolean,
    onRescan: () -> Unit,
    onOpenLibrarySettings: () -> Unit,
    drawerFocusRequester: FocusRequester?,
    contentEntryFocusRequester: FocusRequester,
    onContentFocused: (FocusRequester) -> Unit
) {
    val opener = LocalMediaOpener.current
    val libraryAccess = LocalLibraryAccess.current
    val access = libraryAccess.access

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 32.dp, vertical = 28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("Open media", style = MaterialTheme.typography.headlineMedium)
        Text(
            "Play something right away, or let CineK find the videos and music on this device.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.size(4.dp))
        OpenAction(
            title = "Open a file",
            subtitle = "Play a video or song from this device, a USB drive or cloud storage",
            icon = Icons.Rounded.FileOpen,
            onClick = opener::openFile,
            drawerFocusRequester = drawerFocusRequester,
            focusRequester = contentEntryFocusRequester,
            onFocused = onContentFocused
        )
        OpenAction(
            title = "Play a stream",
            subtitle = "Enter a web address, such as an HLS (.m3u8) or MP4 link",
            icon = Icons.Rounded.Link,
            onClick = opener::openStream,
            drawerFocusRequester = drawerFocusRequester,
            onFocused = onContentFocused
        )

        Spacer(Modifier.size(8.dp))
        Text("Library access", style = MaterialTheme.typography.titleLarge)
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = SurfaceDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
        ) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                AccessStatusRow(Icons.Rounded.VideoLibrary, "Videos", access.videoStatus(), access.videos)
                AccessStatusRow(Icons.Rounded.MusicNote, "Music", access.musicStatus(), access.music)
                libraryAccess.guidance()?.let { hint ->
                    Text(hint, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (!access.isComplete) {
                        Button(onClick = libraryAccess::request) { Text(libraryAccess.actionLabel()) }
                    }
                    OutlinedButton(onClick = onRescan, enabled = !isScanning) {
                        Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(if (isScanning) "Scanning…" else "Scan again")
                    }
                    OutlinedButton(onClick = onOpenLibrarySettings) {
                        Icon(Icons.Rounded.Settings, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Media folders")
                    }
                }
            }
        }
    }
}

@Composable
private fun OpenAction(
    title: String,
    subtitle: String,
    icon: ImageVector,
    onClick: () -> Unit,
    drawerFocusRequester: FocusRequester?,
    onFocused: (FocusRequester) -> Unit,
    focusRequester: FocusRequester? = null
) {
    val shape = RoundedCornerShape(14.dp)
    val ownRequester = remember { FocusRequester() }
    val requester = focusRequester ?: ownRequester
    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .then(if (drawerFocusRequester != null) Modifier.focusProperties { left = drawerFocusRequester } else Modifier)
            .focusRequester(requester)
            .onFocusChanged { if (it.isFocused) onFocused(requester) },
        shape = ClickableSurfaceDefaults.shape(shape),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.015f, pressedScale = 0.99f),
        border = ClickableSurfaceDefaults.border(
            focusedBorder = Border(BorderStroke(2.dp, MaterialTheme.colorScheme.primary), shape = shape)
        )
    ) {
        Row(Modifier.padding(horizontal = 20.dp, vertical = 18.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(28.dp))
            Spacer(Modifier.width(18.dp))
            Column {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, modifier = Modifier.alpha(0.72f))
            }
        }
    }
}
