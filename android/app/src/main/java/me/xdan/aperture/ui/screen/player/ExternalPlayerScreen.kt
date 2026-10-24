@file:androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])

package me.xdan.aperture.ui.screen.player

import android.net.Uri
import android.view.KeyEvent
import android.view.View
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import io.github.anilbeesetti.nextlib.media3ext.ffdecoder.NextRenderersFactory
import me.xdan.aperture.R
import me.xdan.aperture.ui.component.touch.Button
import me.xdan.aperture.ui.component.touch.OutlinedButton

/**
 * Plays a file or stream that isn't in the library: one opened from CineK's Open
 * screen or sent from another app. It has no saved progress or subtitle search;
 * the library player handles those for library items.
 */
@Composable
fun ExternalPlayerScreen(uri: String, title: String, onBack: () -> Unit) {
    val items = remember(uri, title) { listOf(PlayableItem(uri, title)) }
    MediaQueuePlayer(items = items, startIndex = 0, onBack = onBack)
}

/** [live] items are assumed to be HLS unless the address says otherwise; see [looksLikeHls]. */
data class PlayableItem(val uri: String, val title: String, val live: Boolean = false)

/**
 * Plays [items] starting at [startIndex]. With more than one item the controls show
 * previous and next, and a remote's channel up and down keys step through them, which
 * is how Live TV changes channel.
 */
@Composable
fun MediaQueuePlayer(items: List<PlayableItem>, startIndex: Int, onBack: () -> Unit) {
    val context = LocalContext.current
    val hostView = LocalView.current
    var error by remember(items) { mutableStateOf<String?>(null) }
    var controlsVisible by remember { mutableStateOf(true) }
    var title by remember(items) { mutableStateOf(items.getOrNull(startIndex)?.title.orEmpty()) }

    val player = remember(items) {
        // Extension decoders cover audio formats the device can't decode, such as AC-3 and DTS.
        val renderers = NextRenderersFactory(context)
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)
            .setEnableDecoderFallback(true)
        ExoPlayer.Builder(context, renderers)
            .setAudioAttributes(
                AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(),
                true
            )
            .setHandleAudioBecomingNoisy(true)
            .build()
    }

    DisposableEffect(player) {
        // Items already retried as the other type, so a bad stream fails instead of looping.
        val retriedAsOtherType = HashSet<Int>()
        val listener = object : Player.Listener {
            override fun onPlayerError(playbackError: PlaybackException) {
                val index = player.currentMediaItemIndex
                val item = items.getOrNull(index)
                if (item != null && playbackError.errorCode in FormatGuessErrors && retriedAsOtherType.add(index)) {
                    val openedAsHls = player.currentMediaItem?.localConfiguration?.mimeType == MimeTypes.APPLICATION_M3U8
                    player.replaceMediaItem(index, externalMediaItem(item.uri, item.title, asHls = !openedAsHls))
                    player.prepare()
                    player.play()
                    return
                }
                error = playbackErrorMessage(playbackError)
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                mediaItem?.mediaMetadata?.title?.let { title = it.toString() }
            }
        }
        player.addListener(listener)
        player.setMediaItems(
            items.map { externalMediaItem(it.uri, it.title, asHls = looksLikeHls(it.uri, it.live)) },
            startIndex.coerceIn(0, (items.size - 1).coerceAtLeast(0)),
            C.TIME_UNSET
        )
        player.prepare()
        player.playWhenReady = true
        val wasKeepingScreenOn = hostView.keepScreenOn
        hostView.keepScreenOn = true
        onDispose {
            hostView.keepScreenOn = wasKeepingScreenOn
            player.removeListener(listener)
            player.release()
        }
    }

    // Leaving CineK pauses rather than keeps playing behind other apps.
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { player.pause() }
    BackHandler(onBack = onBack)

    // Moving on from an item that failed needs a fresh prepare.
    fun step(forward: Boolean) {
        if (forward) player.seekToNextMediaItem() else player.seekToPreviousMediaItem()
        if (error != null) {
            error = null
            player.prepare()
        }
        player.play()
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            factory = { viewContext ->
                object : PlayerView(viewContext) {
                    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
                        val channelStep = when (event.keyCode) {
                            KeyEvent.KEYCODE_CHANNEL_UP -> true
                            KeyEvent.KEYCODE_CHANNEL_DOWN -> false
                            else -> return super.dispatchKeyEvent(event)
                        }
                        if (event.action == KeyEvent.ACTION_DOWN && player.mediaItemCount > 1) {
                            step(forward = channelStep)
                            showController()
                        }
                        return true
                    }
                }.apply {
                    this.player = player
                    useController = true
                    controllerAutoShow = true
                    setShowSubtitleButton(true)
                    defaultArtwork = ContextCompat.getDrawable(viewContext, R.drawable.cinek_logo)
                    artworkDisplayMode = PlayerView.ARTWORK_DISPLAY_MODE_FIT
                    setControllerVisibilityListener(PlayerView.ControllerVisibilityListener { visibility ->
                        controlsVisible = visibility == View.VISIBLE
                    })
                    // D-pad and media keys reach the controller only while it holds focus.
                    isFocusable = true
                    requestFocus()
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        AnimatedVisibility(
            visible = controlsVisible && error == null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopStart)
        ) {
            Text(
                title,
                style = MaterialTheme.typography.titleLarge,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.7f), Color.Transparent)))
                    .padding(horizontal = 24.dp, vertical = 20.dp)
            )
        }

        error?.let { message ->
            Column(
                Modifier
                    .align(Alignment.Center)
                    .widthIn(max = 520.dp)
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.96f), MaterialTheme.shapes.large)
                    .padding(28.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text("Can't play $title", style = MaterialTheme.typography.headlineSmall)
                Text(message, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(onClick = onBack) { Text("Go back") }
                    OutlinedButton(onClick = { error = null; player.prepare(); player.play() }) { Text("Try again") }
                    // Live channels go offline individually, so offer the next one.
                    if (player.hasNextMediaItem()) {
                        OutlinedButton(onClick = { step(forward = true) }) { Text("Next channel") }
                    }
                }
            }
        }
    }
}

private val ProgressiveExtensions = setOf(
    "ts", "mp4", "m4v", "mkv", "webm", "mov", "avi", "flv", "mp3", "aac", "m4a", "ogg", "opus", "flac", "wav"
)

/**
 * Whether [uri] should be opened as HLS. ExoPlayer can't tell HLS from a plain file by
 * its contents, so this goes on the address: `.m3u8` anywhere in the path, or, for
 * live channels, anything without a plain media extension. Samsung TV Plus and Plex
 * playlists, for example, link HLS through redirects with no extension at all.
 */
internal fun looksLikeHls(uri: String, live: Boolean): Boolean {
    val path = Uri.parse(uri).path.orEmpty().lowercase()
    if (path.contains(".m3u8")) return true
    return live && path.substringAfterLast('/').substringAfterLast('.', "") !in ProgressiveExtensions
}

internal fun externalMediaItem(uri: String, title: String, asHls: Boolean = looksLikeHls(uri, live = false)): MediaItem =
    MediaItem.Builder()
        .setUri(Uri.parse(uri))
        .apply { if (asHls) setMimeType(MimeTypes.APPLICATION_M3U8) }
        .setMediaMetadata(MediaMetadata.Builder().setTitle(title).build())
        .build()

/** Errors that mean the stream was opened as the wrong kind (HLS or plain file). */
private val FormatGuessErrors = setOf(
    PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED,
    PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED,
    PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED,
    PlaybackException.ERROR_CODE_PARSING_MANIFEST_UNSUPPORTED
)

private fun playbackErrorMessage(error: PlaybackException): String = when (error.errorCode) {
    PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
    PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT ->
        "CineK couldn't reach the server. Check the connection and the address."
    PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS ->
        "The server refused the request. The stream may be offline or need a different address."
    PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND,
    PlaybackException.ERROR_CODE_IO_NO_PERMISSION,
    PlaybackException.ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE ->
        "The file is no longer available. Open it again from its app or folder."
    PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED,
    PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED,
    PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED ->
        "This format isn't supported, or the file is damaged."
    PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
    PlaybackException.ERROR_CODE_DECODING_FAILED,
    PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED,
    PlaybackException.ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES ->
        "This device can't decode this media."
    else -> "Playback failed (${error.errorCodeName})."
}
