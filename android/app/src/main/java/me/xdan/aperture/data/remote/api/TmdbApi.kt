package me.xdan.aperture.data.remote.api

import me.xdan.aperture.data.remote.dto.TmdbSearchResponse
import retrofit2.http.GET
import retrofit2.http.Query
import retrofit2.http.Path
import me.xdan.aperture.data.remote.dto.TmdbEpisodeResult
import me.xdan.aperture.data.remote.dto.TmdbDetails
import me.xdan.aperture.data.remote.dto.TmdbGenreList
import me.xdan.aperture.data.remote.dto.TmdbPage
import me.xdan.aperture.data.remote.dto.TmdbSeason

interface TmdbApi {
    @GET("search/movie")
    suspend fun searchMovie(
        @Query("query") query: String,
        @Query("year") year: Int? = null,
        @Query("api_key") apiKey: String
    ): TmdbSearchResponse

    @GET("search/tv")
    suspend fun searchTvShow(
        @Query("query") query: String,
        @Query("first_air_date_year") year: Int? = null,
        @Query("api_key") apiKey: String
    ): TmdbSearchResponse

    @GET("tv/{seriesId}/season/{seasonNumber}/episode/{episodeNumber}")
    suspend fun getTvEpisode(
        @Path("seriesId") seriesId: Int,
        @Path("seasonNumber") seasonNumber: Int,
        @Path("episodeNumber") episodeNumber: Int,
        @Query("api_key") apiKey: String
    ): TmdbEpisodeResult
    
    @GET("discover/movie")
    suspend fun discoverMovies(
        @Query("api_key") apiKey: String,
        @Query("page") page: Int,
        @Query("sort_by") sortBy: String,
        @Query("with_genres") genreId: Int? = null,
        @Query("primary_release_date.gte") releasedFrom: String? = null,
        @Query("primary_release_date.lte") releasedTo: String? = null,
        @Query("vote_count.gte") minimumVotes: Int? = null,
        @Query("include_adult") includeAdult: Boolean = false
    ): TmdbPage

    @GET("discover/tv")
    suspend fun discoverShows(
        @Query("api_key") apiKey: String,
        @Query("page") page: Int,
        @Query("sort_by") sortBy: String,
        @Query("with_genres") genreId: Int? = null,
        @Query("first_air_date.gte") airedFrom: String? = null,
        @Query("first_air_date.lte") airedTo: String? = null,
        @Query("vote_count.gte") minimumVotes: Int? = null,
        @Query("include_adult") includeAdult: Boolean = false
    ): TmdbPage

    @GET("genre/{type}/list")
    suspend fun genres(@Path("type") type: String, @Query("api_key") apiKey: String): TmdbGenreList

    /** [type] is "movie" or "tv"; cast and similar titles come in the same response. */
    @GET("{type}/{id}")
    suspend fun details(
        @Path("type") type: String,
        @Path("id") id: Int,
        @Query("api_key") apiKey: String,
        @Query("append_to_response") append: String = "credits,recommendations"
    ): TmdbDetails

    @GET("tv/{seriesId}/season/{seasonNumber}")
    suspend fun season(
        @Path("seriesId") seriesId: Int,
        @Path("seasonNumber") seasonNumber: Int,
        @Query("api_key") apiKey: String
    ): TmdbSeason

    companion object {
        const val BASE_URL = "https://api.themoviedb.org/3/"
        const val IMAGE_BASE_URL = "https://image.tmdb.org/t/p/"
    }
}
