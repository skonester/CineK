package me.xdan.aperture.ui.screen.live

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import me.xdan.aperture.data.live.LiveChannel
import me.xdan.aperture.domain.model.LivePlayerApp
import me.xdan.aperture.ui.navigation.Destination
import me.xdan.aperture.ui.screen.player.MediaQueuePlayer
import me.xdan.aperture.ui.screen.player.PlayableItem
import me.xdan.aperture.ui.screen.player.PlayerPlaceholder
import me.xdan.aperture.ui.screen.player.StreamPlayerViewModel
import me.xdan.aperture.ui.screen.player.openInApp

/**
 * Plays [destination]'s channel with the rest of its category queued for channel up
 * and down, or hands the channel to MX Player or VLC when Settings asks for one.
 */
@Composable
fun LiveChannelPlayer(
    destination: Destination.LivePlayer,
    viewModel: LiveTvViewModel,
    onBack: () -> Unit,
    settingsViewModel: StreamPlayerViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
) {
    val context = LocalContext.current
    val state by viewModel.channelState.collectAsState()
    val settings = settingsViewModel.settings.collectAsState().value ?: return PlayerPlaceholder()
    // Keyed on loaded rather than the channel list, so a playlist refresh in the
    // background doesn't restart the channel being watched. Watching a channel also
    // reorders Recently watched, which must not reshuffle the queue being played.
    val queue: List<LiveChannel> = remember(destination, state.loaded) {
        liveTvQueue(state, destination.sourceId, destination.category)
    }
    val startIndex = remember(queue) { queue.indexOfFirst { it.url == destination.channelUrl } }
    val items = remember(queue) {
        if (startIndex < 0) listOf(PlayableItem(destination.channelUrl, destination.channelName, live = true))
        else queue.map { PlayableItem(it.url, it.name, live = true, headers = it.headers, logo = it.logo, number = it.number) }
    }

    // Chosen once per channel, so changing the setting mid-way doesn't switch players.
    var handOff by remember(destination) { mutableStateOf(settings.livePlayerApp.takeIf { it != LivePlayerApp.CINEK }) }
    handOff?.let { app ->
        LaunchedEffect(destination, app) {
            if (openInApp(context, items[startIndex.coerceAtLeast(0)], app)) {
                queue.getOrNull(startIndex)?.let(viewModel::markWatched)
                onBack()
            } else {
                Toast.makeText(context, "${app.label} isn't installed, so CineK is playing this channel", Toast.LENGTH_LONG).show()
                handOff = null
            }
        }
        return PlayerPlaceholder()
    }

    MediaQueuePlayer(
        items = items,
        startIndex = startIndex.coerceAtLeast(0),
        onBack = onBack,
        // A channel missing from its playlist plays alone and isn't recorded.
        onItemShown = { index -> if (startIndex >= 0) queue.getOrNull(index)?.let(viewModel::markWatched) },
        settings = settings,
        onCyclePictureSize = settingsViewModel::cyclePictureSize
    )
}
