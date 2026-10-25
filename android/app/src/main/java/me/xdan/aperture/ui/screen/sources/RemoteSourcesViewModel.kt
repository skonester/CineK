package me.xdan.aperture.ui.screen.sources

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import me.xdan.aperture.data.remoteplayback.*
import me.xdan.aperture.data.sources.*
import javax.inject.Inject

data class RemoteSourcesUiState(
    val request: RemoteMediaRequest? = null, val media: RemoteMediaEntity? = null,
    val options: List<RemoteSourceOption> = emptyList(), val loading: Set<SourceSite> = emptySet(),
    val failures: List<SourceFailure> = emptyList(), val error: String? = null, val selecting: Boolean = false,
    val selectedIndex: Int = 0
)

@HiltViewModel
class RemoteSourcesViewModel @Inject constructor(
    private val repository: RemoteMediaRepository,
    private val lookup: RemoteSourceLookup
) : ViewModel() {
    private val _state = MutableStateFlow(RemoteSourcesUiState())
    val state = _state.asStateFlow()
    val history = repository.history.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    private var work: Job? = null
    private var generation = 0

    init {
        viewModelScope.launch {
            repository.history.collect { history ->
                _state.update { current ->
                    val saved = history.firstOrNull { it.contentId == current.media?.contentId }
                    if (saved == null) current else current.copy(media = saved)
                }
            }
        }
    }

    /** Called by Discover after selecting a TMDB movie or an actual episode. */
    fun open(request: RemoteMediaRequest, sites: Set<SourceSite> = CatalogSites) {
        if (_state.value.request == request && (_state.value.options.isNotEmpty() || work?.isActive == true)) return
        cancelLookup()
        _state.value = RemoteSourcesUiState(request = request)
        start(request, request.embedSite?.let(::setOf) ?: sites)
    }

    fun retry() {
        val request = _state.value.request ?: return
        start(request, request.embedSite?.let(::setOf) ?: CatalogSites)
    }

    fun clear() {
        cancelLookup()
        _state.value = RemoteSourcesUiState()
    }

    fun openSaved(id: String) {
        if (_state.value.media?.contentId == id && _state.value.options.isNotEmpty()) return
        cancelLookup()
        val version = generation
        _state.value = RemoteSourcesUiState()
        viewModelScope.launch {
            try {
                val request = repository.request(id)
                if (generation != version) return@launch
                if (request == null) { _state.update { it.copy(error = "This saved title is no longer available.") }; return@launch }
                _state.value = RemoteSourcesUiState(request = request)
                start(request, request.embedSite?.let(::setOf) ?: CatalogSites)
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { if (generation == version) _state.update { it.copy(error = "Could not open the saved title.") } }
        }
    }

    private fun start(request: RemoteMediaRequest, sites: Set<SourceSite>) {
        cancelLookup()
        val version = generation
        _state.update { it.copy(options = emptyList(), failures = emptyList(), loading = sites, error = null, selectedIndex = 0) }
        work = viewModelScope.launch {
            try {
                val media = repository.remember(request)
                if (generation != version) return@launch
                _state.update { it.copy(media = media) }
                coroutineScope {
                    sites.forEach { site -> launch {
                        val result = try {
                            if (request.embedSite != null) lookup.resolve(site, requireNotNull(request.embedUrl)) else lookup.find(request.query, site)
                        } catch (e: CancellationException) { throw e }
                        catch (_: Exception) { SourceResults(emptyList(), listOf(SourceFailure(site, SourceFailureKind.NETWORK, "This source could not be reached."))) }
                        if (generation != version) return@launch
                        _state.update { current ->
                            current.copy(loading = current.loading - site,
                                options = (current.options + result.streams.map { RemoteSourceOption(site, it) })
                                    .distinctBy { it.provider to (it.stream.url to it.stream.headers) }
                                    .sortedByDescending { it.stream.quality ?: 0 },
                                failures = current.failures + result.failures + if (result.streams.isEmpty() && result.failures.isEmpty()) {
                                    listOf(SourceFailure(site, SourceFailureKind.NO_RESULTS, "No playable sources were found."))
                                } else emptyList())
                        }
                    } }
                }
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { if (generation == version) _state.update { it.copy(error = "Could not prepare the title.", loading = emptySet()) } }
        }
    }

    fun select(option: RemoteSourceOption, fromBeginning: Boolean, onPlay: (String, String, Boolean) -> Unit) {
        val media = _state.value.media ?: return
        if (_state.value.selecting || option !in _state.value.options) return
        val version = generation
        _state.update { it.copy(selecting = true, selectedIndex = it.options.indexOf(option).coerceAtLeast(0)) }
        viewModelScope.launch {
            try {
                val session = repository.select(media.contentId, option)
                if (version != generation) return@launch
                onPlay(media.contentId, session.id, fromBeginning)
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { if (generation == version) _state.update { it.copy(error = "Could not open this source.") } }
            finally { if (generation == version) _state.update { it.copy(selecting = false) } }
        }
    }

    fun removeSaved(id: String) { viewModelScope.launch {
        try {
            repository.remove(id)
            if (_state.value.media?.contentId == id) { cancelLookup(); _state.value = RemoteSourcesUiState() }
        } catch (e: CancellationException) { throw e }
        catch (_: Exception) { _state.update { it.copy(error = "Could not remove this saved title.") } }
    } }
    fun cancelLookup() { generation++; work?.cancel(); _state.update { it.copy(loading = emptySet()) } }

    companion object { val CatalogSites = setOf(SourceSite.RIDOMOVIES, SourceSite.STREAMING_UNITY, SourceSite.VIDSRC) }
}
