package me.xdan.aperture.ui.screen.live

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import me.xdan.aperture.data.live.ChannelSource
import me.xdan.aperture.data.live.FeaturedPlaylist
import me.xdan.aperture.data.live.LiveChannel
import me.xdan.aperture.data.live.LiveChannelRepository
import me.xdan.aperture.data.live.LiveChannelState
import javax.inject.Inject

const val ALL_CHANNELS = "All channels"

data class LiveTvSelection(
    val sourceId: String? = null,
    val category: String = ALL_CHANNELS,
    val query: String = ""
)

data class LiveTvUiState(
    val sources: List<ChannelSource> = emptyList(),
    val source: ChannelSource? = null,
    /** Category names with their channel counts, starting with [ALL_CHANNELS]. */
    val categories: List<Pair<String, Int>> = emptyList(),
    val category: String = ALL_CHANNELS,
    val query: String = "",
    val visibleChannels: List<LiveChannel> = emptyList(),
    val channelCounts: Map<String, Int> = emptyMap(),
    val loadingSourceId: String? = null,
    val error: String? = null,
    val loaded: Boolean = false
)

/** Narrows the saved channels to what the Live TV page shows for [selection]. */
fun buildLiveTvUi(state: LiveChannelState, selection: LiveTvSelection): LiveTvUiState {
    val source = state.sources.firstOrNull { it.id == selection.sourceId } ?: state.sources.firstOrNull()
    val channels = state.channels.filter { it.sourceId == source?.id }
    val groups = channels.groupingBy { it.group }.eachCount()
    val categories = listOf(ALL_CHANNELS to channels.size) +
        groups.entries.sortedBy { it.key.lowercase() }.map { it.key to it.value }
    // A category from another playlist falls back to everything.
    val category = selection.category.takeIf { it == ALL_CHANNELS || it in groups } ?: ALL_CHANNELS
    val query = selection.query.trim()
    return LiveTvUiState(
        sources = state.sources,
        source = source,
        categories = categories,
        category = category,
        query = selection.query,
        visibleChannels = channels.filter { channel ->
            (category == ALL_CHANNELS || channel.group == category) &&
                (query.isEmpty() || channel.name.contains(query, true) || channel.number.contains(query, true))
        },
        channelCounts = state.channels.groupingBy { it.sourceId }.eachCount(),
        loadingSourceId = state.loadingSourceId,
        error = state.error,
        loaded = state.loaded
    )
}

/** The channels the player can step through: the chosen playlist and category, ignoring search. */
fun liveTvQueue(state: LiveChannelState, sourceId: String, category: String): List<LiveChannel> =
    state.channels.filter { it.sourceId == sourceId && (category == ALL_CHANNELS || it.group == category) }

@HiltViewModel
class LiveTvViewModel @Inject constructor(
    private val repository: LiveChannelRepository
) : ViewModel() {
    private val selection = MutableStateFlow(LiveTvSelection())

    val ui: StateFlow<LiveTvUiState> = combine(repository.state, selection, ::buildLiveTvUi)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LiveTvUiState())

    val channelState: StateFlow<LiveChannelState> = repository.state

    fun selectSource(source: ChannelSource) =
        selection.update { LiveTvSelection(sourceId = source.id, query = it.query) }

    fun selectCategory(category: String) = selection.update { it.copy(category = category) }

    fun setQuery(query: String) = selection.update { it.copy(query = query) }

    /** Returns true when the playlist was accepted and is downloading. */
    fun add(name: String, location: String): Boolean {
        val id = repository.add(name, location) ?: return false
        selection.update { LiveTvSelection(sourceId = id) }
        return true
    }

    fun addFeatured(playlist: FeaturedPlaylist) = add(playlist.name, playlist.url)

    fun refresh(source: ChannelSource) = repository.refresh(source)

    fun remove(source: ChannelSource) {
        repository.remove(source)
        selection.update { LiveTvSelection(query = it.query) }
    }

    fun clearError() = repository.clearError()
}
