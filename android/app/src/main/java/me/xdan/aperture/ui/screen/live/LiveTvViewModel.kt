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
import me.xdan.aperture.data.live.categoryKey
import javax.inject.Inject

const val ALL_CHANNELS = "All channels"
/**
 * Categories made from the viewer's own picks. A playlist group with the same name
 * is shadowed by these, though its channels still appear under [ALL_CHANNELS].
 */
const val FAVORITE_CHANNELS = "Favorites"
const val RECENT_CHANNELS = "Recently watched"

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
    /** [LiveChannel.key]s of favorite channels. */
    val favorites: Set<String> = emptySet(),
    /** The selected playlist's favorite category names. */
    val favoriteCategories: Set<String> = emptySet(),
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
    val favorites = categoryChannels(state, channels, FAVORITE_CHANNELS).size
    val recent = categoryChannels(state, channels, RECENT_CHANNELS).size
    // The viewer's own categories only show once they have something in them.
    val personal = listOf(FAVORITE_CHANNELS to favorites, RECENT_CHANNELS to recent).filter { it.second > 0 }
    val pinned = groups.keys.filterTo(HashSet()) { source != null && categoryKey(source.id, it) in state.favoriteCategories }
    // Favorite categories come first, then the rest, each alphabetically.
    val categories = listOf(ALL_CHANNELS to channels.size) + personal +
        groups.entries.filter { it.key !in PersonalCategories }
            .sortedWith(compareBy({ it.key !in pinned }, { it.key.lowercase() }))
            .map { it.key to it.value }
    // A category from another playlist, or one emptied since, falls back to everything.
    val category = selection.category.takeIf { name -> categories.any { it.first == name } } ?: ALL_CHANNELS
    val query = selection.query.trim()
    return LiveTvUiState(
        sources = state.sources,
        source = source,
        categories = categories,
        category = category,
        query = selection.query,
        visibleChannels = categoryChannels(state, channels, category).filter { channel ->
            query.isEmpty() || channel.name.contains(query, true) || channel.number.contains(query, true)
        },
        favorites = state.favorites,
        favoriteCategories = pinned,
        channelCounts = state.channels.groupingBy { it.sourceId }.eachCount(),
        loadingSourceId = state.loadingSourceId,
        error = state.error,
        loaded = state.loaded
    )
}

private val PersonalCategories = setOf(FAVORITE_CHANNELS, RECENT_CHANNELS)

/**
 * [category]'s share of [channels], one playlist's worth. Favorites keep playlist
 * order; Recently watched is newest first.
 */
private fun categoryChannels(state: LiveChannelState, channels: List<LiveChannel>, category: String): List<LiveChannel> =
    when (category) {
        ALL_CHANNELS -> channels
        FAVORITE_CHANNELS -> channels.filter { it.key in state.favorites }
        RECENT_CHANNELS -> {
            // Channels sharing a key all match one history entry; show the first.
            val byKey = channels.reversed().associateBy { it.key }
            state.history.mapNotNull { byKey[it.key] }
        }
        else -> channels.filter { it.group == category }
    }

/** The channels the player can step through: the chosen playlist and category, ignoring search. */
fun liveTvQueue(state: LiveChannelState, sourceId: String, category: String): List<LiveChannel> =
    categoryChannels(state, state.channels.filter { it.sourceId == sourceId }, category)

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

    fun toggleFavorite(channel: LiveChannel) = repository.toggleFavorite(channel)

    /** Pins or unpins one of the selected playlist's own categories. */
    fun toggleFavoriteCategory(category: String) {
        if (category == ALL_CHANNELS || category in PersonalCategories) return
        val source = ui.value.source ?: return
        repository.toggleFavoriteCategory(source.id, category)
    }

    fun markWatched(channel: LiveChannel) = repository.markWatched(channel)
}
