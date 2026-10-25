package me.xdan.aperture.ui.screen.details

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import me.xdan.aperture.data.discover.DiscoverDetails
import me.xdan.aperture.data.discover.DiscoverRepository
import me.xdan.aperture.data.discover.DiscoverType
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import me.xdan.aperture.data.local.entity.MediaEntity
import me.xdan.aperture.data.local.entity.PlaybackProgressEntity
import me.xdan.aperture.domain.repository.MediaRepository
import me.xdan.aperture.data.remote.dto.TmdbResult
import javax.inject.Inject

@HiltViewModel
class MediaDetailsViewModel @Inject constructor(
    private val repository: MediaRepository,
    private val discover: DiscoverRepository
) : ViewModel() {

    private val _media = MutableStateFlow<MediaEntity?>(null)
    val media: StateFlow<MediaEntity?> = _media

    /**
     * TMDB's genres, runtime and cast for the matched title. Episodes share their
     * show's, so changing episode doesn't fetch again. Null while loading, without a
     * match, or when TMDB can't be reached; the panel just shows less.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val extras: StateFlow<DiscoverDetails?> = _media
        .map { media ->
            val tmdbId = media?.tmdbId
            if (tmdbId == null || !discover.isAvailable) null
            else (if (media.type == "MOVIE") DiscoverType.MOVIES else DiscoverType.SHOWS) to tmdbId
        }
        .distinctUntilChanged()
        .mapLatest { key -> key?.let { (type, id) -> runCatching { discover.details(type, id) }.getOrNull() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    private val _progress = MutableStateFlow<PlaybackProgressEntity?>(null)
    val progress: StateFlow<PlaybackProgressEntity?> = _progress
    private val _episodes = MutableStateFlow<List<MediaEntity>>(emptyList())
    val episodes: StateFlow<List<MediaEntity>> = _episodes
    private val _assetCandidates = MutableStateFlow<List<TmdbResult>>(emptyList())
    val assetCandidates: StateFlow<List<TmdbResult>> = _assetCandidates
    private val _isLoadingAssets = MutableStateFlow(false)
    val isLoadingAssets: StateFlow<Boolean> = _isLoadingAssets
    private var assetSearchJob: Job? = null

    fun loadMedia(mediaId: Long, preferActiveEpisode: Boolean = true) {
        viewModelScope.launch {
            val selected = repository.getMediaById(mediaId)
            val showEpisodes = if (selected?.type == "EPISODE") {
                repository.getEpisodesForShow(selected.title)
            } else emptyList()
            _episodes.value = showEpisodes

            // A grouped show card represents its deterministic first episode.
            // Do not scan every episode here: stale progress on a later file
            // must not make the popup open on (for example) S02E05. Continue
            // Watching already chooses the next/current episode separately.
            val selectedProgress = selected?.let { repository.getProgress(it.id) }
            val resumableSelected = selectedProgress?.takeIf { progress ->
                preferActiveEpisode && !progress.isCompleted &&
                    (progress.keepInContinueWatching ||
                        (progress.duration > 0 &&
                            progress.position >= progress.duration * 0.05 &&
                            progress.position < progress.duration * 0.95))
            }
            _media.value = selected
            _progress.value = resumableSelected ?: selectedProgress
        }
    }

    fun selectEpisode(mediaId: Long) {
        viewModelScope.launch {
            _media.value = repository.getMediaById(mediaId)
            _progress.value = repository.getProgress(mediaId)
        }
    }

    fun toggleFavorite(mediaId: Long) {
        viewModelScope.launch {
            val current = repository.getMediaById(mediaId) ?: return@launch
            repository.setFavorite(mediaId, !current.isFavorite)
            _media.value = current.copy(isFavorite = !current.isFavorite)
        }
    }

    fun findAssetCandidates(query: String? = null) {
        val current = _media.value ?: return
        assetSearchJob?.cancel()
        assetSearchJob = viewModelScope.launch {
            _isLoadingAssets.value = true
            _assetCandidates.value = runCatching {
                repository.searchMetadataCandidates(current, query)
            }.getOrDefault(emptyList())
            _isLoadingAssets.value = false
        }
    }

    fun selectAssetCandidate(candidate: TmdbResult) {
        val mediaId = _media.value?.id ?: return
        viewModelScope.launch {
            repository.applyMetadataCandidate(mediaId, candidate)
            _media.value = repository.getMediaById(mediaId)
            _media.value?.takeIf { it.type == "EPISODE" }?.let {
                _episodes.value = repository.getEpisodesForShow(it.title)
            }
        }
    }
}
