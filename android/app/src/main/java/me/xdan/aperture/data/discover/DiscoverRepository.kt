package me.xdan.aperture.data.discover

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import me.xdan.aperture.BuildConfig
import me.xdan.aperture.data.remote.api.TmdbApi
import me.xdan.aperture.data.remote.dto.TmdbDetails
import me.xdan.aperture.data.remote.dto.TmdbResult
import me.xdan.aperture.data.remoteplayback.RemoteMediaRequest
import me.xdan.aperture.data.sources.SourceMediaType
import me.xdan.aperture.data.sources.SourceQuery
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

enum class DiscoverType(val tmdbPath: String, val label: String) {
    MOVIES("movie", "Movies"),
    SHOWS("tv", "Shows")
}

enum class DiscoverSort(val label: String) {
    POPULAR("Popular"),
    TOP_RATED("Top rated"),
    NEWEST("Newest")
}

/** Release periods by decade; dates are inclusive, and null leaves that end open. */
enum class DiscoverPeriod(val label: String, val from: String?, val to: String?) {
    ANY("Any year", null, null),
    D2020S("2020s", "2020-01-01", null),
    D2010S("2010s", "2010-01-01", "2019-12-31"),
    D2000S("2000s", "2000-01-01", "2009-12-31"),
    D1990S("1990s", "1990-01-01", "1999-12-31"),
    D1980S("1980s", "1980-01-01", "1989-12-31"),
    OLDER("Before 1980", null, "1979-12-31")
}

data class DiscoverFilter(
    val type: DiscoverType = DiscoverType.MOVIES,
    val sort: DiscoverSort = DiscoverSort.POPULAR,
    val genreId: Int? = null,
    val period: DiscoverPeriod = DiscoverPeriod.ANY
)

data class DiscoverGenre(val id: Int, val name: String)

/** Metadata used to look up sources; a catalogue entry does not guarantee an available stream. */
data class DiscoverTitle(
    val tmdbId: Int,
    val type: DiscoverType,
    val title: String,
    val year: Int?,
    val posterPath: String?,
    val backdropPath: String?,
    val overview: String?,
    val rating: Double?
)

data class DiscoverPage(val titles: List<DiscoverTitle>, val page: Int, val totalPages: Int) {
    val hasMore: Boolean get() = page < totalPages
}

data class CastMember(val name: String, val character: String?, val profilePath: String?)
data class DiscoverSeason(val number: Int, val name: String, val episodeCount: Int)
data class DiscoverEpisode(val season: Int, val number: Int, val name: String, val airDate: String?)

/** Movie/episode identity comes from the selected thumbnail, never a manually entered title or ID. */
fun DiscoverTitle.sourceRequest(episode: DiscoverEpisode? = null): RemoteMediaRequest {
    require(type != DiscoverType.SHOWS || episode != null) { "Choose an episode first." }
    require(type != DiscoverType.MOVIES || episode == null) { "Movies do not have episodes." }
    return RemoteMediaRequest(SourceQuery(title, if (type == DiscoverType.MOVIES) SourceMediaType.MOVIE else SourceMediaType.SHOW,
        year, tmdbId, season = episode?.season, episode = episode?.number))
}

data class DiscoverDetails(
    val title: DiscoverTitle,
    val tagline: String?,
    val runtimeMinutes: Int?,
    val seasons: Int?,
    val genres: List<String>,
    val cast: List<CastMember>,
    val related: List<DiscoverTitle>,
    val seasonList: List<DiscoverSeason> = emptyList()
)

/** Discover needs a TMDB key, which builds without one don't have. */
class DiscoverUnavailableException : IllegalStateException("Discover needs a TMDB API key in this build.")

/**
 * Browses TMDB's catalogue for the Discover page. Genre lists and details are kept
 * in memory for the session, since they rarely change.
 */
@Singleton
class DiscoverRepository(
    private val api: TmdbApi,
    private val apiKey: String,
    private val today: () -> LocalDate
) {
    @Inject constructor(api: TmdbApi) : this(api, BuildConfig.TMDB_API_KEY, LocalDate::now)

    private val lock = Mutex()
    private val genreCache = HashMap<DiscoverType, List<DiscoverGenre>>()
    private val detailsCache = object : LinkedHashMap<Pair<DiscoverType, Int>, DiscoverDetails>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Pair<DiscoverType, Int>, DiscoverDetails>) = size > 40
    }

    val isAvailable: Boolean get() = apiKey.isNotBlank()

    private fun key(): String = apiKey.ifBlank { throw DiscoverUnavailableException() }

    suspend fun genres(type: DiscoverType): List<DiscoverGenre> {
        lock.withLock { genreCache[type] }?.let { return it }
        val genres = api.genres(type.tmdbPath, key()).genres.map { DiscoverGenre(it.id, it.name) }
        lock.withLock { genreCache[type] = genres }
        return genres
    }

    suspend fun page(filter: DiscoverFilter, page: Int): DiscoverPage {
        val key = key()
        // Newest means already released: TMDB also lists titles with future dates.
        val todayText = today().toString()
        val to = if (filter.sort == DiscoverSort.NEWEST) minOf(filter.period.to ?: todayText, todayText) else filter.period.to
        // Top rated needs enough votes, or obscure titles with one perfect score come
        // first. Newest needs a few, or it fills with listings nobody has watched yet.
        val minimumVotes = when (filter.sort) {
            DiscoverSort.TOP_RATED -> if (filter.type == DiscoverType.MOVIES) 300 else 150
            DiscoverSort.NEWEST -> if (filter.type == DiscoverType.MOVIES) 25 else 10
            DiscoverSort.POPULAR -> null
        }
        val response = when (filter.type) {
            DiscoverType.MOVIES -> api.discoverMovies(
                key, page, sortKey(filter), filter.genreId, filter.period.from, to, minimumVotes
            )
            DiscoverType.SHOWS -> api.discoverShows(
                key, page, sortKey(filter), filter.genreId, filter.period.from, to, minimumVotes
            )
        }
        return DiscoverPage(
            titles = response.results.mapNotNull { it.toTitle(filter.type) },
            page = response.page,
            // TMDB serves at most 500 pages.
            totalPages = response.totalPages.coerceAtMost(500)
        )
    }

    suspend fun details(type: DiscoverType, tmdbId: Int): DiscoverDetails {
        lock.withLock { detailsCache[type to tmdbId] }?.let { return it }
        val details = api.details(type.tmdbPath, tmdbId, key()).toDetails(type)
        lock.withLock { detailsCache[type to tmdbId] = details }
        return details
    }

    suspend fun episodes(tmdbId: Int, season: Int): List<DiscoverEpisode> {
        require(tmdbId > 0 && season > 0)
        return api.season(tmdbId, season, key()).episodes
            .filter { it.season == season && it.number > 0 }
            .distinctBy { it.number }.sortedBy { it.number }
            .map { DiscoverEpisode(it.season, it.number, it.name, it.airDate) }
    }

    private fun sortKey(filter: DiscoverFilter): String = when (filter.sort) {
        DiscoverSort.POPULAR -> "popularity.desc"
        DiscoverSort.TOP_RATED -> "vote_average.desc"
        DiscoverSort.NEWEST -> if (filter.type == DiscoverType.MOVIES) "primary_release_date.desc" else "first_air_date.desc"
    }
}

private fun yearOf(date: String?): Int? = date?.take(4)?.toIntOrNull()

internal fun TmdbResult.toTitle(type: DiscoverType): DiscoverTitle? {
    val name = (title ?: name)?.takeIf { it.isNotBlank() } ?: return null
    return DiscoverTitle(id, type, name, yearOf(releaseDate ?: firstAirDate), posterPath, backdropPath, overview, voteAverage)
}

internal fun TmdbDetails.toDetails(type: DiscoverType): DiscoverDetails = DiscoverDetails(
    title = DiscoverTitle(
        id, type, (title ?: name).orEmpty(), yearOf(releaseDate ?: firstAirDate),
        posterPath, backdropPath, overview, voteAverage
    ),
    tagline = tagline?.takeIf { it.isNotBlank() },
    runtimeMinutes = runtime?.takeIf { it > 0 } ?: episodeRunTime?.firstOrNull { it > 0 },
    seasons = numberOfSeasons,
    genres = genres.map { it.name },
    cast = credits?.cast.orEmpty().sortedBy { it.order }.take(15).map { CastMember(it.name, it.character, it.profilePath) },
    // Recommendations are always the same type as the title they come from.
    related = recommendations?.results.orEmpty().mapNotNull { it.toTitle(type) }.take(20),
    // Source adapters use positive season/episode numbers; specials (season 0) aren't selectable.
    seasonList = seasonList.filter { it.number > 0 && it.episodeCount > 0 }.sortedBy { it.number }
        .map { DiscoverSeason(it.number, it.name, it.episodeCount) }
)
