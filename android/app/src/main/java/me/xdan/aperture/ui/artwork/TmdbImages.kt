package me.xdan.aperture.ui.artwork

import androidx.compose.runtime.staticCompositionLocalOf
import me.xdan.aperture.data.remote.api.TmdbApi

/** Whether posters and stills load at a higher resolution, from Settings. */
val LocalHdArtwork = staticCompositionLocalOf { false }

/**
 * TMDB image sizes for fixed-size artwork, standard and HD. Backdrops pick their
 * size from the screen instead; see backdropImageSpec.
 */
enum class TmdbImageSize(val standard: String, val hd: String) {
    POSTER("w500", "w780"),
    POSTER_SMALL("w342", "w500"),
    ARTWORK("w780", "w1280"),
    STILL("w300", "w780"),
    PROFILE("w185", "h632");

    fun url(path: String, hd: Boolean): String = TmdbApi.IMAGE_BASE_URL + (if (hd) this.hd else standard) + path
}
