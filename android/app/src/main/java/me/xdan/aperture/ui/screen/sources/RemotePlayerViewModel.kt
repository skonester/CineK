package me.xdan.aperture.ui.screen.sources

import androidx.lifecycle.ViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import me.xdan.aperture.data.remoteplayback.*
import me.xdan.aperture.data.sources.SourceSite
import javax.inject.Inject

data class RemotePlayerState(
    val media: RemoteMediaEntity? = null, val option: RemoteSourceOption? = null,
    val loading: Boolean = true, val error: String? = null, val startPositionMs: Long = 0,
    val playbackToken: String = "", val revision: Int = 0
)

@HiltViewModel
class RemotePlayerViewModel @Inject constructor(
    private val repository: RemoteMediaRepository, private val lookup: RemoteSourceLookup,
    private val savedState: SavedStateHandle = SavedStateHandle()
) : ViewModel() {
    private val _state = MutableStateFlow(RemotePlayerState())
    val state = _state.asStateFlow()
    val saveError = repository.saveError
    private var work: Job? = null
    private var loadedRoute: Triple<String, String?, Boolean>? = null
    fun isLoadedFor(id: String, sessionId: String?, fromBeginning: Boolean): Boolean = loadedRoute == Triple(id, sessionId, fromBeginning)

    fun load(id: String, sessionId: String?, fromBeginning: Boolean, force: Boolean = false) {
        val route = Triple(id, sessionId, fromBeginning)
        if (!force && loadedRoute == route) return
        loadedRoute = route
        work?.cancel()
        val revision = _state.value.revision + 1
        _state.value = RemotePlayerState(revision = revision)
        work = viewModelScope.launch {
            try {
                val media = repository.get(id) ?: error("Missing title")
                val request = repository.request(id) ?: error("Missing request")
                val cached = if (force) null else repository.session(sessionId, id)
                val option = cached?.option ?: run {
                    val provider = request.embedSite ?: media.lastProvider?.let { runCatching { SourceSite.valueOf(it) }.getOrNull() }
                        ?.takeIf { it in RemoteSourcesViewModel.CatalogSites } ?: SourceSite.STREAMING_UNITY
                    val result = if (request.embedSite != null) lookup.resolve(provider, requireNotNull(request.embedUrl)) else lookup.find(request.query, provider)
                    val stream = result.streams.firstOrNull { it.quality == media.lastQuality } ?: result.streams.firstOrNull()
                    stream?.let { RemoteSourceOption(provider, it) }
                }
                if (_state.value.revision != revision) return@launch
                if (option == null) {
                    _state.value = RemotePlayerState(media = media, loading = false,
                        error = "This source could not be refreshed. Choose another source or enter a fresh embed link.", revision = revision)
                    return@launch
                }
                val latest = repository.get(id) ?: media
                val restartPending = fromBeginning && savedState.get<String>(RestartConsumed) != "$id:${sessionId.orEmpty()}"
                _state.value = RemotePlayerState(latest, option, false, startPositionMs = if (restartPending) 0 else latest.resumePositionMs,
                    playbackToken = repository.beginPlayback(id), revision = revision)
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) {
                if (_state.value.revision == revision) _state.value = RemotePlayerState(loading = false,
                    error = "Could not open this saved title. Return to sources and try again.", revision = revision)
            }
        }
    }

    fun record(id: String, token: String, snapshot: StreamPlaybackSnapshot) {
        repository.record(id, token, snapshot)
        if (_state.value.playbackToken == token && snapshot.hasStarted) {
            loadedRoute?.let { savedState[RestartConsumed] = "${it.first}:${it.second.orEmpty()}" }
            _state.update { it.copy(startPositionMs = snapshot.positionMs.coerceAtLeast(0)) }
        }
    }

    fun refresh() { loadedRoute?.let { load(it.first, it.second, it.third, force = true) } }

    private companion object { const val RestartConsumed = "remote_restart_consumed" }
}
