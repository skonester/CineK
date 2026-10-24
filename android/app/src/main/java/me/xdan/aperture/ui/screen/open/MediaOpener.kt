package me.xdan.aperture.ui.screen.open

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.SurfaceDefaults
import androidx.tv.material3.Text
import kotlinx.coroutines.delay
import me.xdan.aperture.ui.component.touch.Button
import me.xdan.aperture.ui.component.touch.OutlinedButton
import me.xdan.aperture.ui.component.touch.Surface
import me.xdan.aperture.ui.navigation.Destination

/**
 * Opens media that isn't in the library: a file chosen with Android's picker or a
 * stream address. Provided once near the root so any screen can offer both.
 */
@Stable
class MediaOpener internal constructor() {
    internal var pickFile: () -> Unit = {}
    internal var askForStream: () -> Unit = {}

    fun openFile() = pickFile()
    fun openStream() = askForStream()
}

val LocalMediaOpener = staticCompositionLocalOf { MediaOpener() }

private val StreamSchemes = setOf("http", "https", "rtsp", "rtmp", "udp", "rtp")
private val OpenableTypes = arrayOf("video/*", "audio/*")

@Composable
fun ProvideMediaOpener(
    onPlay: (Destination.ExternalPlayer) -> Unit,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val opener = remember { MediaOpener() }
    var showStreamDialog by remember { mutableStateOf(false) }

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        // Keep read access so the player can reopen it after a configuration change.
        runCatching {
            context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        onPlay(Destination.ExternalPlayer(uri.toString(), displayName(context, uri)))
    }

    opener.pickFile = {
        runCatching { filePicker.launch(OpenableTypes) }.onFailure {
            Toast.makeText(context, "No file picker is available on this device", Toast.LENGTH_LONG).show()
        }
    }
    opener.askForStream = { showStreamDialog = true }

    CompositionLocalProvider(LocalMediaOpener provides opener) {
        content()
        if (showStreamDialog) {
            StreamAddressDialog(
                onDismiss = { showStreamDialog = false },
                onPlay = { address ->
                    showStreamDialog = false
                    onPlay(Destination.ExternalPlayer(address, streamTitle(address)))
                }
            )
        }
    }
}

/** The name Android shows for [uri], falling back to the last path segment. */
fun displayName(context: Context, uri: Uri): String {
    if (uri.scheme == ContentResolver.SCHEME_CONTENT) {
        runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0)?.takeIf { it.isNotBlank() }?.let { return it }
            }
        }
    }
    return Uri.decode(uri.lastPathSegment.orEmpty()).substringAfterLast('/').ifBlank { "Media" }
}

private fun streamTitle(address: String): String {
    val uri = Uri.parse(address)
    return uri.lastPathSegment?.takeIf { it.isNotBlank() } ?: uri.host ?: "Stream"
}

/** Returns an error to show, or null when [address] is a playable stream address. */
internal fun streamAddressError(address: String): String? {
    val uri = Uri.parse(address.trim())
    return when {
        address.isBlank() -> "Enter a stream address."
        uri.scheme?.lowercase() !in StreamSchemes -> "Use an address that starts with http://, https://, rtsp:// or rtmp://."
        uri.host.isNullOrBlank() -> "That address has no server name."
        else -> null
    }
}

@Composable
private fun StreamAddressDialog(onDismiss: () -> Unit, onPlay: (String) -> Unit) {
    var address by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val fieldFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        delay(120)
        runCatching { fieldFocus.requestFocus() }
    }
    fun submit() {
        error = streamAddressError(address)
        if (error == null) onPlay(address.trim())
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.62f))
                .pointerInput(Unit) { detectTapGestures(onTap = { onDismiss() }) },
            contentAlignment = Alignment.Center
        ) {
            Surface(
                modifier = Modifier
                    .widthIn(max = 600.dp)
                    .padding(24.dp)
                    .pointerInput(Unit) { detectTapGestures { } },
                shape = RoundedCornerShape(28.dp),
                colors = SurfaceDefaults.colors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(Modifier.padding(28.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text("Play a stream", style = MaterialTheme.typography.headlineSmall)
                    Text(
                        "Enter the address of a video, audio or live stream, such as an HLS (.m3u8) or MP4 link.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(16.dp))
                            .padding(16.dp)
                    ) {
                        BasicTextField(
                            value = address,
                            onValueChange = { address = it; error = null },
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
                            keyboardActions = KeyboardActions(onGo = { submit() }),
                            modifier = Modifier.fillMaxWidth().focusRequester(fieldFocus),
                            decorationBox = { field ->
                                if (address.isEmpty()) Text("https://…", color = Color.Gray)
                                field()
                            }
                        )
                    }
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End)) {
                        OutlinedButton(onClick = onDismiss) { Text("Cancel") }
                        Button(onClick = ::submit) { Text("Play") }
                    }
                }
            }
        }
    }
}
