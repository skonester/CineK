package me.xdan.aperture.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import me.xdan.aperture.domain.repository.MediaRepository
import me.xdan.aperture.domain.repository.LibraryPreparationStage
import me.xdan.aperture.domain.repository.UserPreferencesRepository
import javax.inject.Inject
import android.content.Context
import androidx.core.graphics.drawable.toBitmap
import coil.imageLoader
import coil.request.ImageRequest
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.xdan.aperture.data.remote.api.TmdbApi
import me.xdan.aperture.ui.artwork.extractArtworkAccent

@HiltViewModel
class MainViewModel @Inject constructor(
    private val userPreferencesRepository: UserPreferencesRepository,
    private val mediaRepository: MediaRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private var preparationJob: Job? = null
    private var hasStartedPreparation = false

    val libraryPreparation = mediaRepository.preparationProgress

    val themeId = userPreferencesRepository.themeId.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        "cinek"
    )

    private val _dynamicAccentArgb = MutableStateFlow<Int?>(null)
    val dynamicAccentArgb: StateFlow<Int?> = _dynamicAccentArgb
    private val artworkAccentCache = mutableMapOf<String, Int>()
    private var requestedActiveMediaId: Long? = null
    private var dynamicThemeJob: Job? = null

    init {
        startLibraryPreparation()
    }

    fun startLibraryPreparation(force: Boolean = false) {
        if (preparationJob?.isActive == true) return
        if (!force &&
            hasStartedPreparation &&
            mediaRepository.preparationProgress.value.stage != LibraryPreparationStage.ERROR
        ) return
        hasStartedPreparation = true
        preparationJob = viewModelScope.launch {
            mediaRepository.scanLocalFiles()
        }
    }

    fun setActiveMedia(mediaId: Long) {
        if (themeId.value != "dynamic") return
        requestedActiveMediaId = mediaId
        dynamicThemeJob?.cancel()

        viewModelScope.launch {
            val media = mediaRepository.getMediaById(mediaId) ?: return@launch
            val artworkPath = media.backdropPath ?: media.posterPath ?: return@launch
            val cacheKey = "$mediaId:$artworkPath"

            artworkAccentCache[cacheKey]?.let {
                _dynamicAccentArgb.value = it
                return@launch
            }

            dynamicThemeJob = viewModelScope.launch {
                // Avoid recolouring the whole app while the user is rapidly
                // traversing a row; settle briefly on the focused card first.
                kotlinx.coroutines.delay(140)
                if (requestedActiveMediaId != mediaId) return@launch
                
                val url = TmdbApi.IMAGE_BASE_URL + "w780" + artworkPath
                val accent = withContext(Dispatchers.IO) {
                    runCatching {
                        val result = context.imageLoader.execute(
                            ImageRequest.Builder(context).data(url).allowHardware(false).build()
                        )
                        result.drawable?.toBitmap()?.let(::extractArtworkAccent)
                    }.getOrNull()
                }
                if (accent != null) {
                    artworkAccentCache[cacheKey] = accent
                    if (requestedActiveMediaId == mediaId) _dynamicAccentArgb.value = accent
                }
            }
        }
    }

}
