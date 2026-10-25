package me.xdan.aperture.data.remote.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class TmdbPage(
    @Json(name = "page") val page: Int = 1,
    @Json(name = "total_pages") val totalPages: Int = 1,
    @Json(name = "results") val results: List<TmdbResult> = emptyList()
)

@JsonClass(generateAdapter = true)
data class TmdbGenre(
    @Json(name = "id") val id: Int,
    @Json(name = "name") val name: String
)

@JsonClass(generateAdapter = true)
data class TmdbGenreList(
    @Json(name = "genres") val genres: List<TmdbGenre> = emptyList()
)

@JsonClass(generateAdapter = true)
data class TmdbCastMember(
    @Json(name = "id") val id: Int,
    @Json(name = "name") val name: String,
    @Json(name = "character") val character: String? = null,
    @Json(name = "profile_path") val profilePath: String? = null,
    @Json(name = "order") val order: Int = Int.MAX_VALUE
)

@JsonClass(generateAdapter = true)
data class TmdbCredits(
    @Json(name = "cast") val cast: List<TmdbCastMember> = emptyList()
)

/** A movie or show with its credits and recommendations appended. */
@JsonClass(generateAdapter = true)
data class TmdbDetails(
    @Json(name = "id") val id: Int,
    @Json(name = "title") val title: String? = null,
    @Json(name = "name") val name: String? = null,
    @Json(name = "tagline") val tagline: String? = null,
    @Json(name = "overview") val overview: String? = null,
    @Json(name = "poster_path") val posterPath: String? = null,
    @Json(name = "backdrop_path") val backdropPath: String? = null,
    @Json(name = "release_date") val releaseDate: String? = null,
    @Json(name = "first_air_date") val firstAirDate: String? = null,
    @Json(name = "vote_average") val voteAverage: Double? = null,
    @Json(name = "runtime") val runtime: Int? = null,
    @Json(name = "episode_run_time") val episodeRunTime: List<Int>? = null,
    @Json(name = "number_of_seasons") val numberOfSeasons: Int? = null,
    @Json(name = "genres") val genres: List<TmdbGenre> = emptyList(),
    @Json(name = "credits") val credits: TmdbCredits? = null,
    @Json(name = "recommendations") val recommendations: TmdbPage? = null,
    @Json(name = "seasons") val seasonList: List<TmdbSeasonSummary> = emptyList()
)

@JsonClass(generateAdapter = true)
data class TmdbSeasonSummary(
    @Json(name = "season_number") val number: Int,
    @Json(name = "name") val name: String,
    @Json(name = "episode_count") val episodeCount: Int = 0
)

@JsonClass(generateAdapter = true)
data class TmdbSeason(
    @Json(name = "episodes") val episodes: List<TmdbSeasonEpisode> = emptyList()
)

@JsonClass(generateAdapter = true)
data class TmdbSeasonEpisode(
    @Json(name = "episode_number") val number: Int,
    @Json(name = "season_number") val season: Int,
    @Json(name = "name") val name: String = "",
    @Json(name = "air_date") val airDate: String? = null
)
