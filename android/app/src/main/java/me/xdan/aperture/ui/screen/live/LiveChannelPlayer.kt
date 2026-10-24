package me.xdan.aperture.ui.screen.live

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import me.xdan.aperture.ui.navigation.Destination
import me.xdan.aperture.ui.screen.player.MediaQueuePlayer
import me.xdan.aperture.ui.screen.player.PlayableItem

/** Plays [destination]'s channel with the rest of its category queued for channel up and down. */
@Composable
fun LiveChannelPlayer(destination: Destination.LivePlayer, viewModel: LiveTvViewModel, onBack: () -> Unit) {
    val state by viewModel.channelState.collectAsState()
    // Keyed on loaded rather than the channel list, so a playlist refresh in the
    // background doesn't restart the channel being watched.
    val (items, startIndex) = remember(destination, state.loaded) {
        val queue = liveTvQueue(state, destination.sourceId, destination.category)
        val index = queue.indexOfFirst { it.url == destination.channelUrl }
        if (index < 0) {
            listOf(PlayableItem(destination.channelUrl, destination.channelName, live = true)) to 0
        } else {
            queue.map { PlayableItem(it.url, it.name, live = true) } to index
        }
    }
    MediaQueuePlayer(items = items, startIndex = startIndex, onBack = onBack)
}
