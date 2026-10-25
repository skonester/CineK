package me.xdan.aperture.ui.screen.discover

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import me.xdan.aperture.data.discover.DiscoverDetails
import me.xdan.aperture.data.discover.DiscoverEpisode
import me.xdan.aperture.data.discover.DiscoverFilter
import me.xdan.aperture.data.discover.DiscoverGenre
import me.xdan.aperture.data.discover.DiscoverPeriod
import me.xdan.aperture.data.discover.DiscoverRepository
import me.xdan.aperture.data.discover.DiscoverSort
import me.xdan.aperture.data.discover.DiscoverTitle
import me.xdan.aperture.data.discover.DiscoverType
import me.xdan.aperture.data.discover.DiscoverUnavailableException
import me.xdan.aperture.data.local.entity.MediaEntity
import me.xdan.aperture.domain.repository.MediaRepository
import me.xdan.aperture.ui.screen.home.EpisodeOrder
import retrofit2.HttpException
import java.io.IOException
import javax.inject.Inject

data class DiscoverUiState(
    val filter: DiscoverFilter = DiscoverFilter(),
    val genres: List<DiscoverGenre> = emptyList(),
    val titles: List<DiscoverTitle> = emptyList(),
    val loading: Boolean = false,
    val hasMore: Boolean = false,
    val error: String? = null,
    /** False when the build has no TMDB key, so there is nothing to browse. */
    val available: Boolean = true,
    val selected: DiscoverTitle? = null,
    val details: DiscoverDetails? = null,
    val detailsError: String? = null,
    val selectedSeason: Int? = null,
    val episodes: List<DiscoverEpisode> = emptyList(),
    val selectedEpisode: DiscoverEpisode? = null,
    val seasonLoading: Boolean = false,
    val seasonError: String? = null
)

/** The library item a TMDB title opens: the movie, or a show's first episode. Keyed by type and TMDB id. */
internal fun libraryMatches(media: List<MediaEntity>): Map<Pair<DiscoverType, Int>, Long> {
    val visible = media.filter { !it.isHidden && it.tmdbId != null }
    val movies = visible.filter { it.type == "MOVIE" }.associate { (DiscoverType.MOVIES to it.tmdbId!!) to it.id }
    // Episodes carry their show's TMDB id.
    val shows = visible.filter { it.type == "EPISODE" }.groupBy { it.tmdbId!! }
        .mapValues { (_, episodes) -> episodes.minWith(EpisodeOrder).id }
        .mapKeys { (id, _) -> DiscoverType.SHOWS to id }
    return movies + shows
}

@HiltViewModel
class DiscoverViewModel @Inject constructor(
    private val discover: DiscoverRepository,
    mediaRepository: MediaRepository
) : ViewModel() {
    private val _ui = MutableStateFlow(DiscoverUiState(available = discover.isAvailable))
    val ui: StateFlow<DiscoverUiState> = _ui.asStateFlow()

    /** Library items by TMDB title, so Discover can say what is already playable. */
    val library: StateFlow<Map<Pair<DiscoverType, Int>, Long>> = mediaRepository.getAllMedia()
        .map(::libraryMatches)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    private var nextPage = 1
    private var pageJob: Job? = null
    private var detailsJob: Job? = null
    private var seasonJob: Job? = null
    // Bumped by every new filter, so a slow page for an old one is dropped.
    private var generation = 0

    init {
        if (discover.isAvailable) reload()
    }

    fun setType(type: DiscoverType) {
        // Movie and TV genres have different ids, so the genre resets.
        if (type != _ui.value.filter.type) applyFilter(_ui.value.filter.copy(type = type, genreId = null), newGenres = true)
    }

    fun setSort(sort: DiscoverSort) = applyFilter(_ui.value.filter.copy(sort = sort))

    fun setGenre(genreId: Int?) = applyFilter(_ui.value.filter.copy(genreId = genreId))

    fun setPeriod(period: DiscoverPeriod) = applyFilter(_ui.value.filter.copy(period = period))

    private fun applyFilter(filter: DiscoverFilter, newGenres: Boolean = false) {
        if (filter == _ui.value.filter && !newGenres) return
        _ui.update { it.copy(filter = filter, genres = if (newGenres) emptyList() else it.genres) }
        reload()
    }

    fun retry() = if (_ui.value.titles.isEmpty()) reload() else loadMore()

    private fun reload() {
        generation++
        nextPage = 1
        pageJob?.cancel()
        _ui.update { it.copy(titles = emptyList(), hasMore = false, error = null) }
        if (_ui.value.genres.isEmpty()) loadGenres(_ui.value.filter.type)
        loadMore()
    }

    private fun loadGenres(type: DiscoverType) = viewModelScope.launch {
        // Genres are only for filtering; without them the page still works.
        runCatching { discover.genres(type) }.onSuccess { genres ->
            if (_ui.value.filter.type == type) _ui.update { it.copy(genres = genres) }
        }
    }

    /** Loads the next page; the grid calls this as it nears the end. */
    fun loadMore() {
        if (pageJob?.isActive == true) return
        val requested = generation
        val filter = _ui.value.filter
        val page = nextPage
        _ui.update { it.copy(loading = true, error = null) }
        pageJob = viewModelScope.launch {
            try {
                val result = discover.page(filter, page)
                if (requested != generation) return@launch
                nextPage = page + 1
                _ui.update { state ->
                    state.copy(
                        // Popular lists shift between pages, so a title can turn up twice.
                        titles = (state.titles + result.titles).distinctBy { it.tmdbId },
                        hasMore = result.hasMore,
                        loading = false
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (requested == generation) _ui.update { it.copy(loading = false, error = errorMessage(e)) }
            }
        }
    }

    fun open(title: DiscoverTitle) {
        detailsJob?.cancel()
        seasonJob?.cancel()
        _ui.update { it.copy(selected = title, details = null, detailsError = null,
            selectedSeason = null, episodes = emptyList(), selectedEpisode = null, seasonLoading = false, seasonError = null) }
        detailsJob = viewModelScope.launch {
            try {
                val details = discover.details(title.type, title.tmdbId)
                _ui.update { if (it.selected == title) it.copy(details = details) else it }
                if (_ui.value.selected == title && title.type == DiscoverType.SHOWS) {
                    details.seasonList.firstOrNull()?.let { selectSeason(it.number) }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _ui.update { if (it.selected == title) it.copy(detailsError = errorMessage(e)) else it }
            }
        }
    }

    fun closeDetails() {
        detailsJob?.cancel()
        seasonJob?.cancel()
        _ui.update { it.copy(selected = null, details = null, detailsError = null,
            selectedSeason = null, episodes = emptyList(), selectedEpisode = null, seasonLoading = false, seasonError = null) }
    }

    fun selectSeason(number: Int) {
        val title = _ui.value.selected?.takeIf { it.type == DiscoverType.SHOWS } ?: return
        if (_ui.value.details?.seasonList?.none { it.number == number } != false) return
        seasonJob?.cancel()
        _ui.update { it.copy(selectedSeason = number, episodes = emptyList(), selectedEpisode = null,
            seasonLoading = true, seasonError = null) }
        seasonJob = viewModelScope.launch {
            try {
                val episodes = discover.episodes(title.tmdbId, number)
                _ui.update { if (it.selected == title && it.selectedSeason == number)
                    it.copy(episodes = episodes, seasonLoading = false) else it }
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) {
                _ui.update { if (it.selected == title && it.selectedSeason == number)
                    it.copy(seasonLoading = false, seasonError = "Couldn't load episodes. Try again.") else it }
            }
        }
    }

    fun selectEpisode(episode: DiscoverEpisode) {
        if (episode in _ui.value.episodes) _ui.update { it.copy(selectedEpisode = episode) }
    }

    private fun errorMessage(error: Exception): String = when {
        error is DiscoverUnavailableException -> error.message.orEmpty()
        error is HttpException && error.code() == 401 -> "TMDB didn't accept this build's API key."
        error is HttpException && error.code() == 429 -> "TMDB is busy. Try again in a moment."
        error is IOException -> "CineK couldn't reach TMDB. Check the connection."
        else -> "Discover couldn't load this page."
    }
}
