@file:androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])

package me.xdan.aperture.ui.screen.player

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.media.AudioManager
import android.net.Uri
import android.provider.Settings
import android.util.Log
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import io.github.anilbeesetti.nextlib.media3ext.ffdecoder.NextRenderersFactory
import kotlinx.coroutines.delay
import me.xdan.aperture.R
import me.xdan.aperture.data.remoteplayback.StreamPlaybackSnapshot
import me.xdan.aperture.data.sources.SourceSubtitle
import me.xdan.aperture.domain.model.StreamPictureSize
import me.xdan.aperture.domain.model.StreamPlayerSettings
import me.xdan.aperture.ui.component.touch.Button
import me.xdan.aperture.ui.component.touch.OutlinedButton
import java.util.Date
import kotlin.math.roundToInt

/**
 * Plays a file or stream that isn't in the library: one opened from CineK's Open
 * screen or sent from another app. It has no saved progress or subtitle search;
 * the library player handles those for library items.
 */
@Composable
fun ExternalPlayerScreen(
    uri: String,
    title: String,
    onBack: () -> Unit,
    settingsViewModel: StreamPlayerViewModel = viewModel()
) {
    val items = remember(uri, title) { listOf(PlayableItem(uri, title)) }
    val settings = settingsViewModel.settings.collectAsState().value ?: return PlayerPlaceholder()
    MediaQueuePlayer(
        items = items,
        startIndex = 0,
        onBack = onBack,
        settings = settings,
        onCyclePictureSize = settingsViewModel::cyclePictureSize
    )
}

/**
 * [live] items are assumed to be HLS unless the address says otherwise; see [looksLikeHls].
 * [headers] go with this item's network requests only. [logo] and [number] label
 * live channels.
 */
data class PlayableItem(
    val uri: String,
    val title: String,
    val live: Boolean = false,
    val headers: Map<String, String> = emptyMap(),
    val logo: String = "",
    val number: String = "",
    val mimeType: String? = null,
    val subtitles: List<SourceSubtitle> = emptyList()
)

/**
 * Plays [items] starting at [startIndex]. With more than one item the controls show
 * previous and next, and a remote's channel up and down keys step through them, which
 * is how Live TV changes channel. [onItemShown] gets the index of each item as it starts.
 */
@Composable
fun MediaQueuePlayer(
    items: List<PlayableItem>,
    startIndex: Int,
    onBack: () -> Unit,
    onItemShown: (Int) -> Unit = {},
    settings: StreamPlayerSettings = StreamPlayerSettings(),
    onCyclePictureSize: () -> Unit = {},
    initialPositionMs: Long = 0,
    onProgress: (StreamPlaybackSnapshot) -> Unit = {},
    onChooseSource: (() -> Unit)? = null,
    onRefreshSource: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val hostView = LocalView.current
    val start = startIndex.coerceIn(0, (items.size - 1).coerceAtLeast(0))
    var error by remember(items) { mutableStateOf<String?>(null) }
    var controlsVisible by remember { mutableStateOf(true) }
    var title by remember(items) { mutableStateOf(items.getOrNull(start)?.title.orEmpty()) }
    var currentIndex by remember(items) { mutableIntStateOf(start) }
    // Bumped each time an item starts, which shows the channel banner again.
    var bannerShows by remember(items) { mutableIntStateOf(0) }
    var bannerVisible by remember { mutableStateOf(false) }
    var pictureSizeLabel by remember { mutableStateOf<String?>(null) }
    var adjustment by remember { mutableStateOf<Pair<SwipeTarget, Float>?>(null) }
    var adjustments by remember { mutableIntStateOf(0) }
    var confirmingExit by remember { mutableStateOf(false) }
    var reconnecting by remember(items) { mutableStateOf(false) }
    val reconnects = remember(items) { ReconnectBudget() }
    var playerView by remember { mutableStateOf<PlayerView?>(null) }
    val live = items.any { it.live }
    val current = items.getOrNull(currentIndex)
    val currentSettings by rememberUpdatedState(settings)
    val currentOnItemShown by rememberUpdatedState(onItemShown)
    val currentOnProgress by rememberUpdatedState(onProgress)
    val currentOnCyclePictureSize by rememberUpdatedState(onCyclePictureSize)

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
            .setMediaSourceFactory(HeaderAwareMediaSourceFactory(context))
            .build()
    }

    val progressReporter = remember(player) { StreamProgressReporter(player) { currentOnProgress(it) } }
    LaunchedEffect(player) {
        while (true) { delay(5_000); if (player.isPlaying) progressReporter.save() }
    }
    DisposableEffect(player) {
        player.addListener(progressReporter)
        fun itemShown(index: Int) {
            currentIndex = index
            bannerShows++
            currentOnItemShown(index)
        }
        // Items already retried as the other type, so a bad stream fails instead of looping.
        val retriedAsOtherType = HashSet<Int>()
        // Items that have played, after which errors are dropouts rather than a wrong guess.
        val started = HashSet<Int>()
        val listener = object : Player.Listener {
            override fun onPlayerError(playbackError: PlaybackException) {
                val index = player.currentMediaItemIndex
                val item = items.getOrNull(index)
                if (item != null && item.mimeType == null && index !in started && playbackError.errorCode in FormatGuessErrors && retriedAsOtherType.add(index)) {
                    val openedAsHls = player.currentMediaItem?.localConfiguration?.mimeType == MimeTypes.APPLICATION_M3U8
                    player.replaceMediaItem(index, item.asMediaItem(asHls = !openedAsHls))
                    player.prepare()
                    player.play()
                    return
                }
                // Live streams that were playing drop out now and then: the playlist stops
                // updating or restarts its numbering around an ad break, or the connection
                // blips. Reconnect at the live edge a few times before giving up.
                if (item?.live == true && index in started && reconnects.tryAcquire(System.currentTimeMillis())) {
                    Log.w("MediaQueuePlayer", "Reconnecting ${item.title} after ${playbackError.errorCodeName}", playbackError)
                    reconnecting = true
                    // A new media ID means a new media source, so the stream's address is
                    // opened again. Redirecting addresses like Pluto TV's then start a new
                    // session instead of reusing the one whose playlist just rewound.
                    val asHls = player.currentMediaItem?.localConfiguration?.mimeType == MimeTypes.APPLICATION_M3U8
                    player.replaceMediaItem(
                        index,
                        item.asMediaItem(asHls, mediaId = "reconnect-${System.nanoTime()}")
                    )
                    player.seekToDefaultPosition()
                    player.prepare()
                    player.play()
                    return
                }
                reconnecting = false
                error = playbackErrorMessage(playbackError)
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) {
                    started += player.currentMediaItemIndex
                    reconnecting = false
                }
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                mediaItem?.mediaMetadata?.title?.let { title = it.toString() }
                if (reason != Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED) {
                    reconnects.reset()
                    reconnecting = false
                }
                // Replacing an item to retry it as the other type is not a new item.
                if (mediaItem != null && reason != Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED) {
                    itemShown(player.currentMediaItemIndex)
                }
            }
        }
        player.addListener(listener)
        player.setMediaItems(
            items.map { it.asMediaItem() },
            start,
            if (items.getOrNull(start)?.live == true) C.TIME_UNSET else initialPositionMs.coerceAtLeast(0)
        )
        if (items.isNotEmpty()) itemShown(start)
        player.prepare()
        player.playWhenReady = true
        val wasKeepingScreenOn = hostView.keepScreenOn
        hostView.keepScreenOn = true
        onDispose {
            progressReporter.save()
            player.removeListener(progressReporter)
            hostView.keepScreenOn = wasKeepingScreenOn
            player.removeListener(listener)
            player.release()
        }
    }

    val activity = remember(context) { context.findActivity() }
    val audio = remember(context) { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val swipe = remember(context) {
        SwipeAdjuster(
            touchSlop = ViewConfiguration.get(context).scaledTouchSlop,
            currentValue = { target ->
                when (target) {
                    SwipeTarget.BRIGHTNESS -> activity?.window?.attributes?.screenBrightness?.takeIf { it >= 0f }
                        ?: (Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, 128) / 255f)
                    SwipeTarget.VOLUME -> audio.getStreamVolume(AudioManager.STREAM_MUSIC) /
                        audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1).toFloat()
                }
            },
            onAdjust = { target, value ->
                when (target) {
                    SwipeTarget.BRIGHTNESS -> activity?.window?.let { window ->
                        // Fully dark would leave no way to see the screen to swipe back.
                        window.attributes = window.attributes.apply { screenBrightness = value.coerceAtLeast(0.02f) }
                    }
                    SwipeTarget.VOLUME -> {
                        val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                        runCatching { audio.setStreamVolume(AudioManager.STREAM_MUSIC, (value * max).roundToInt(), 0) }
                    }
                }
                adjustment = target to value
                adjustments++
            }
        )
    }
    // The brightness only holds while watching.
    DisposableEffect(activity) {
        onDispose {
            activity?.window?.let { window ->
                window.attributes = window.attributes.apply {
                    screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
                }
            }
        }
    }

    LaunchedEffect(bannerShows) {
        if (bannerShows == 0) return@LaunchedEffect
        bannerVisible = true
        delay(BannerMillis)
        bannerVisible = false
    }
    LaunchedEffect(pictureSizeLabel) {
        if (pictureSizeLabel != null) { delay(LabelMillis); pictureSizeLabel = null }
    }
    LaunchedEffect(adjustments) {
        if (adjustment != null) { delay(LabelMillis); adjustment = null }
    }

    // Leaving CineK pauses rather than keeps playing behind other apps.
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { progressReporter.save(); player.pause() }
    BackHandler {
        when {
            confirmingExit -> { confirmingExit = false; playerView?.requestFocus() }
            live && settings.confirmLiveExit && error == null -> confirmingExit = true
            else -> onBack()
        }
    }

    // A subtitle file the viewer picks is attached to the item playing, at the same spot.
    val scope = rememberCoroutineScope()
    val subtitlePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val subtitle = withContext(Dispatchers.IO) { copyAddedSubtitle(context, uri, currentSettings.subtitleEncoding) }
            val currentItem = player.currentMediaItem
            if (subtitle == null || currentItem == null) {
                Toast.makeText(context, "Choose an .srt, .vtt, .ass or .ttml subtitle file", Toast.LENGTH_LONG).show()
                return@launch
            }
            val index = player.currentMediaItemIndex
            val position = player.currentPosition
            player.replaceMediaItem(index, currentItem.buildUpon().setSubtitleConfigurations(
                currentItem.localConfiguration?.subtitleConfigurations.orEmpty() + subtitle).build())
            player.seekTo(index, position)
            player.play()
        }
    }

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

                    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
                        val wasSwiping = swipe.target != null
                        if (!swipe.onTouch(event, width, height)) return super.dispatchTouchEvent(event)
                        // The controller already saw the press; cancel it so the swipe isn't also a tap.
                        if (!wasSwiping) {
                            val cancel = MotionEvent.obtain(event).apply { action = MotionEvent.ACTION_CANCEL }
                            super.dispatchTouchEvent(cancel)
                            cancel.recycle()
                        }
                        return true
                    }
                }.apply {
                    this.player = player
                    useController = true
                    controllerAutoShow = true
                    setShowSubtitleButton(true)
                    // Channel changes and slow streams show a spinner instead of a frozen frame.
                    setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
                    defaultArtwork = ContextCompat.getDrawable(viewContext, R.drawable.cinek_logo)
                    artworkDisplayMode = PlayerView.ARTWORK_DISPLAY_MODE_FIT
                    // Each is added at the start, so the row ends up in the reverse of this order.
                    if (!live) {
                        addControllerButton(R.drawable.ic_subtitle_file, "Add subtitle file") {
                            // Subtitle files arrive with many types, so accept anything and check the name.
                            runCatching { subtitlePicker.launch(arrayOf("*/*")) }.onFailure {
                                Toast.makeText(viewContext, "No file picker is available on this device", Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                    addControllerButton(R.drawable.ic_picture_size, "Picture size") {
                        pictureSizeLabel = currentSettings.pictureSize.next.label
                        currentOnCyclePictureSize()
                    }
                    addControllerButton(R.drawable.ic_open_in_new, "Open in another app") {
                        val item = items.getOrNull(player.currentMediaItemIndex) ?: return@addControllerButton
                        player.pause()
                        if (!openInOtherApp(viewContext, item)) {
                            Toast.makeText(viewContext, "No other video app can open this", Toast.LENGTH_LONG).show()
                        }
                    }
                    // On phones the navigation bar covers the right end of the controls,
                    // where the settings button usually sits, so it goes first instead.
                    moveControllerButtonToStart(androidx.media3.ui.R.id.exo_settings)
                    setControllerVisibilityListener(PlayerView.ControllerVisibilityListener { visibility ->
                        controlsVisible = visibility == View.VISIBLE
                    })
                    // D-pad and media keys reach the controller only while it holds focus.
                    isFocusable = true
                    requestFocus()
                    playerView = this
                }
            },
            update = { view ->
                view.resizeMode = settings.pictureSize.resizeMode
                swipe.enabled = settings.swipeGestures
            },
            modifier = Modifier.fillMaxSize()
        )

        AnimatedVisibility(
            visible = (controlsVisible || (bannerVisible && live && settings.channelBanner)) && error == null && !confirmingExit,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopStart)
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.7f), Color.Transparent)))
                    .padding(horizontal = 24.dp, vertical = 20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (current?.live == true && current.logo.isNotBlank()) {
                    AsyncImage(
                        model = current.logo,
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.size(width = 64.dp, height = 40.dp)
                    )
                    Spacer(Modifier.width(14.dp))
                }
                Column(Modifier.weight(1f)) {
                    if (current?.live == true && current.number.isNotBlank()) {
                        Text(current.number, style = MaterialTheme.typography.labelLarge, color = Color.White.copy(alpha = 0.8f))
                    }
                    Text(title, style = MaterialTheme.typography.titleLarge, color = Color.White, maxLines = 1,
                        overflow = TextOverflow.Ellipsis)
                }
                if (settings.showClock) PlayerClock()
                onChooseSource?.let { choose ->
                    Spacer(Modifier.width(12.dp))
                    OutlinedButton(onClick = { progressReporter.save(); choose() }) { Text("Sources") }
                }
            }
        }

        val feedback = adjustment?.let { (target, value) ->
            when (target) {
                SwipeTarget.BRIGHTNESS -> "Brightness ${(value * 100).roundToInt()}%"
                SwipeTarget.VOLUME -> "Volume ${(value * 100).roundToInt()}%"
            }
        } ?: pictureSizeLabel ?: "Reconnecting…".takeIf { reconnecting && error == null }
        feedback?.let { message ->
            Text(
                message,
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.Center)
                    .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 18.dp, vertical = 10.dp)
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
                    Button(onClick = { progressReporter.save(); onBack() }) { Text(if (onChooseSource != null) "Choose source" else "Go back") }
                    onRefreshSource?.let { refresh ->
                        OutlinedButton(onClick = { progressReporter.save(); refresh() }) { Text("Refresh source") }
                    }
                    OutlinedButton(onClick = {
                        error = null
                        reconnects.reset()
                        // A live channel picks up at the live edge, not where it stopped.
                        if (items.getOrNull(player.currentMediaItemIndex)?.live == true) player.seekToDefaultPosition()
                        player.prepare()
                        player.play()
                    }) { Text("Try again") }
                    // Live channels go offline individually, so offer the next one.
                    if (player.hasNextMediaItem()) {
                        OutlinedButton(onClick = { step(forward = true) }) { Text("Next channel") }
                    }
                }
            }
        }

        if (confirmingExit) {
            val keepWatching = remember { FocusRequester() }
            LaunchedEffect(Unit) { runCatching { keepWatching.requestFocus() } }
            Column(
                Modifier
                    .align(Alignment.Center)
                    .widthIn(max = 520.dp)
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.96f), MaterialTheme.shapes.large)
                    .padding(28.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text("Stop watching?", style = MaterialTheme.typography.headlineSmall)
                Text("You'll go back to the channel list.", style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(
                        onClick = { confirmingExit = false; playerView?.requestFocus() },
                        modifier = Modifier.focusRequester(keepWatching)
                    ) { Text("Keep watching") }
                    OutlinedButton(onClick = onBack) { Text("Exit") }
                }
            }
        }
    }
}

/** A black screen for the moment before a player can start. */
@Composable
fun PlayerPlaceholder() {
    Box(Modifier.fillMaxSize().background(Color.Black))
}

private const val BannerMillis = 4_000L
private const val LabelMillis = 1_200L

private val StreamPictureSize.resizeMode: Int
    get() = when (this) {
        StreamPictureSize.FIT -> AspectRatioFrameLayout.RESIZE_MODE_FIT
        StreamPictureSize.STRETCH -> AspectRatioFrameLayout.RESIZE_MODE_FILL
        StreamPictureSize.ZOOM -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
    }

/**
 * Adds a button at the start of the controller's subtitle and settings buttons,
 * styled like them, so it is reachable by touch and by the remote's D-pad.
 */
private fun PlayerView.addControllerButton(icon: Int, description: String, onClick: () -> Unit) {
    val controls = findViewById<ViewGroup>(androidx.media3.ui.R.id.exo_basic_controls) ?: return
    val reference = controls.findViewById<ImageView>(androidx.media3.ui.R.id.exo_subtitle)
    val button = ImageButton(context).apply {
        setImageResource(icon)
        contentDescription = description
        reference?.let {
            background = it.background?.constantState?.newDrawable()
            scaleType = it.scaleType
            setPadding(it.paddingLeft, it.paddingTop, it.paddingRight, it.paddingBottom)
        }
        setOnClickListener { onClick() }
    }
    val params = reference?.layoutParams?.let { ViewGroup.LayoutParams(it) }
        ?: ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    controls.addView(button, 0, params)
}

/** Moves one of the controller's own buttons to the start of its button row. */
private fun PlayerView.moveControllerButtonToStart(id: Int) {
    val controls = findViewById<ViewGroup>(androidx.media3.ui.R.id.exo_basic_controls) ?: return
    val button = controls.findViewById<View>(id) ?: return
    (button.parent as? ViewGroup)?.removeView(button)
    controls.addView(button, 0)
}

@Composable
private fun PlayerClock() {
    val context = LocalContext.current
    // The device's 12 or 24 hour setting.
    val format = remember(context) { android.text.format.DateFormat.getTimeFormat(context) }
    val now by produceState(System.currentTimeMillis()) {
        while (true) {
            value = System.currentTimeMillis()
            delay(60_000 - value % 60_000)
        }
    }
    Text(format.format(Date(now)), style = MaterialTheme.typography.titleMedium, color = Color.White)
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
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

internal fun externalMediaItem(
    uri: String,
    title: String,
    asHls: Boolean = looksLikeHls(uri, live = false),
    headers: Map<String, String> = emptyMap(),
    mediaId: String? = null
): MediaItem =
    MediaItem.Builder()
        .setUri(Uri.parse(uri))
        .apply { mediaId?.let(::setMediaId) }
        .apply { if (asHls) setMimeType(MimeTypes.APPLICATION_M3U8) }
        .apply { if (headers.isNotEmpty()) setTag(RequestHeaders(headers)) }
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
    // The underlying exception, such as PlaylistStuckException, says more than a generic code.
    else -> "Playback failed (${listOfNotNull(error.errorCodeName, error.rootCauseName).joinToString(" · ")})."
}

private val Throwable.rootCauseName: String?
    get() = generateSequence(cause) { it.cause }.lastOrNull()?.javaClass?.simpleName
