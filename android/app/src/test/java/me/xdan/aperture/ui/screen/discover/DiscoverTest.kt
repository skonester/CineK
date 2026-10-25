package me.xdan.aperture.ui.screen.discover

import kotlinx.coroutines.runBlocking
import me.xdan.aperture.data.discover.DiscoverFilter
import me.xdan.aperture.data.discover.DiscoverPeriod
import me.xdan.aperture.data.discover.DiscoverRepository
import me.xdan.aperture.data.discover.DiscoverSort
import me.xdan.aperture.data.discover.DiscoverType
import me.xdan.aperture.data.discover.DiscoverUnavailableException
import me.xdan.aperture.data.local.entity.MediaEntity
import me.xdan.aperture.data.remote.api.TmdbApi
import me.xdan.aperture.data.remote.dto.TmdbCastMember
import me.xdan.aperture.data.remote.dto.TmdbCredits
import me.xdan.aperture.data.remote.dto.TmdbDetails
import me.xdan.aperture.data.remote.dto.TmdbEpisodeResult
import me.xdan.aperture.data.remote.dto.TmdbGenre
import me.xdan.aperture.data.remote.dto.TmdbGenreList
import me.xdan.aperture.data.remote.dto.TmdbPage
import me.xdan.aperture.data.remote.dto.TmdbResult
import me.xdan.aperture.data.remote.dto.TmdbSearchResponse
import me.xdan.aperture.data.remote.dto.TmdbSeason
import me.xdan.aperture.data.remote.dto.TmdbSeasonEpisode
import me.xdan.aperture.data.remote.dto.TmdbSeasonSummary
import me.xdan.aperture.data.discover.sourceRequest
import me.xdan.aperture.data.sources.SourceMediaType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class DiscoverTest {
    /** Records discover requests and answers with fixed data. */
    internal class FakeTmdb : TmdbApi {
        val discoverCalls = mutableListOf<Map<String, Any?>>()
        var genreCalls = 0
        var detailCalls = 0

        private fun result(id: Int, title: String?, date: String?) =
            TmdbResult(id, title, null, "About $id", "/p$id.jpg", null, date, null, 7.5)

        override suspend fun discoverMovies(
            apiKey: String, page: Int, sortBy: String, genreId: Int?, releasedFrom: String?, releasedTo: String?,
            minimumVotes: Int?, includeAdult: Boolean
        ): TmdbPage {
            discoverCalls += mapOf("type" to "movie", "page" to page, "sort" to sortBy, "genre" to genreId,
                "from" to releasedFrom, "to" to releasedTo, "votes" to minimumVotes, "adult" to includeAdult)
            return TmdbPage(page, 900, listOf(result(page * 10 + 1, "Movie $page", "1999-03-31"), result(99, " ", null)))
        }

        override suspend fun discoverShows(
            apiKey: String, page: Int, sortBy: String, genreId: Int?, airedFrom: String?, airedTo: String?,
            minimumVotes: Int?, includeAdult: Boolean
        ): TmdbPage {
            discoverCalls += mapOf("type" to "tv", "page" to page, "sort" to sortBy, "to" to airedTo, "votes" to minimumVotes)
            return TmdbPage(page, 1, listOf(TmdbResult(5, null, "A Show", null, null, null, null, "2015-01-01", null)))
        }

        override suspend fun genres(type: String, apiKey: String): TmdbGenreList {
            genreCalls++
            return TmdbGenreList(listOf(TmdbGenre(28, "Action")))
        }

        override suspend fun details(type: String, id: Int, apiKey: String, append: String): TmdbDetails {
            detailCalls++
            return TmdbDetails(
                id = id, name = "A Show", firstAirDate = "2015-01-01", tagline = " ",
                episodeRunTime = listOf(0, 42), numberOfSeasons = 3,
                genres = listOf(TmdbGenre(18, "Drama")),
                credits = TmdbCredits(listOf(TmdbCastMember(2, "Second", order = 1), TmdbCastMember(1, "Lead", "Hero", order = 0))),
                recommendations = TmdbPage(results = listOf(TmdbResult(6, null, "Another", null, null, null, null, null, null))),
                seasonList = listOf(TmdbSeasonSummary(0, "Specials", 1), TmdbSeasonSummary(2, "Season 2", 2),
                    TmdbSeasonSummary(1, "Season 1", 3), TmdbSeasonSummary(3, "Season 3", 0))
            )
        }

        override suspend fun season(seriesId: Int, seasonNumber: Int, apiKey: String) = TmdbSeason(listOf(
            TmdbSeasonEpisode(2, seasonNumber, "Second"), TmdbSeasonEpisode(1, seasonNumber, "First"),
            TmdbSeasonEpisode(1, seasonNumber, "Duplicate"), TmdbSeasonEpisode(0, seasonNumber, "Invalid"),
            TmdbSeasonEpisode(3, seasonNumber + 1, "Wrong season")))

        override suspend fun searchMovie(query: String, year: Int?, apiKey: String) = TmdbSearchResponse(emptyList())
        override suspend fun searchTvShow(query: String, year: Int?, apiKey: String) = TmdbSearchResponse(emptyList())
        override suspend fun getTvEpisode(seriesId: Int, seasonNumber: Int, episodeNumber: Int, apiKey: String) =
            TmdbEpisodeResult(0, null, null, null, null)
    }

    private val tmdb = FakeTmdb()
    private val repository = DiscoverRepository(tmdb, "key", today = { LocalDate.of(2026, 10, 24) })

    @Test
    fun filtersBecomeTmdbDiscoverParameters() = runBlocking {
        val page = repository.page(DiscoverFilter(sort = DiscoverSort.POPULAR, genreId = 28, period = DiscoverPeriod.D1990S), 2)
        assertEquals(
            mapOf("type" to "movie", "page" to 2, "sort" to "popularity.desc", "genre" to 28,
                "from" to "1990-01-01", "to" to "1999-12-31", "votes" to null, "adult" to false),
            tmdb.discoverCalls.last()
        )
        // Untitled results are dropped; TMDB stops serving after page 500.
        assertEquals(listOf("Movie 2"), page.titles.map { it.title })
        assertEquals(1999, page.titles.single().year)
        assertEquals(500, page.totalPages)
        assertTrue(page.hasMore)
    }

    @Test
    fun newestStopsAtTodayAndTopRatedNeedsVotes() = runBlocking {
        repository.page(DiscoverFilter(sort = DiscoverSort.NEWEST), 1)
        assertEquals("primary_release_date.desc", tmdb.discoverCalls.last()["sort"])
        assertEquals("2026-10-24", tmdb.discoverCalls.last()["to"])
        assertEquals(25, tmdb.discoverCalls.last()["votes"])
        // A decade that ended keeps its own end date.
        repository.page(DiscoverFilter(sort = DiscoverSort.NEWEST, period = DiscoverPeriod.D2000S), 1)
        assertEquals("2009-12-31", tmdb.discoverCalls.last()["to"])
        repository.page(DiscoverFilter(type = DiscoverType.SHOWS, sort = DiscoverSort.TOP_RATED), 1)
        assertEquals(mapOf("type" to "tv", "page" to 1, "sort" to "vote_average.desc", "to" to null, "votes" to 150),
            tmdb.discoverCalls.last())
        val shows = repository.page(DiscoverFilter(type = DiscoverType.SHOWS), 1)
        assertEquals("A Show", shows.titles.single().title)
        assertFalse(shows.hasMore)
    }

    @Test
    fun detailsMapCastRuntimeAndRelatedAndAreCached() = runBlocking {
        val details = repository.details(DiscoverType.SHOWS, 5)
        assertEquals(42, details.runtimeMinutes)
        assertEquals(3, details.seasons)
        assertNull(details.tagline)
        assertEquals(listOf("Lead", "Second"), details.cast.map { it.name })
        assertEquals(DiscoverType.SHOWS, details.related.single().type)
        repository.details(DiscoverType.SHOWS, 5)
        repository.genres(DiscoverType.MOVIES)
        repository.genres(DiscoverType.MOVIES)
        assertEquals(1, tmdb.detailCalls)
        assertEquals(1, tmdb.genreCalls)
    }

    @Test
    fun thumbnailMetadataCreatesExactMovieAndEpisodeRequests() = runBlocking {
        val movie = repository.page(DiscoverFilter(), 1).titles.first()
        val query = movie.sourceRequest().query
        assertEquals(11, query.tmdbId)
        assertEquals("Movie 1", query.title)
        assertEquals(1999, query.year)
        assertEquals(SourceMediaType.MOVIE, query.type)
        val show = repository.details(DiscoverType.SHOWS, 5)
        assertEquals(listOf(1, 2), show.seasonList.map { it.number })
        val episodes = repository.episodes(5, 2)
        assertEquals(listOf(1, 2), episodes.map { it.number })
        val request = show.title.sourceRequest(episodes.last())
        assertEquals(5, request.query.tmdbId)
        assertEquals(SourceMediaType.SHOW, request.query.type)
        assertEquals(2, request.query.season)
        assertEquals(2, request.query.episode)
        assertTrue(runCatching { show.title.sourceRequest() }.isFailure)
        assertTrue(request.contentId != show.title.sourceRequest(episodes.first()).contentId)
    }

    @Test
    fun buildsWithoutAKeyReportDiscoverUnavailable() = runBlocking {
        val noKey = DiscoverRepository(tmdb, "", today = LocalDate::now)
        assertFalse(noKey.isAvailable)
        assertTrue(runCatching { noKey.page(DiscoverFilter(), 1) }.exceptionOrNull() is DiscoverUnavailableException)
        assertTrue(tmdb.discoverCalls.isEmpty())
    }

    @Test
    fun libraryTitlesOpenTheMovieOrTheShowsFirstEpisode() {
        fun media(id: Long, type: String, tmdbId: Int?, season: Int? = null, episode: Int? = null, hidden: Boolean = false) =
            MediaEntity(id = id, filePath = "/m/$id", title = "T", type = type, tmdbId = tmdbId,
                seasonNumber = season, episodeNumber = episode, isHidden = hidden)
        val matches = libraryMatches(listOf(
            media(1, "MOVIE", 603),
            media(2, "MOVIE", 604, hidden = true),
            media(3, "MOVIE", null),
            media(10, "EPISODE", 1399, season = 2, episode = 1),
            media(11, "EPISODE", 1399, season = 1, episode = 2),
            media(12, "EPISODE", 1399, season = 1, episode = 1, hidden = true)
        ))
        assertEquals(mapOf((DiscoverType.MOVIES to 603) to 1L, (DiscoverType.SHOWS to 1399) to 11L), matches)
    }

    @Test
    fun runtimesReadLikeTheRestOfTheApp() {
        assertEquals("48m", runtimeLabel(48))
        assertEquals("2h", runtimeLabel(120))
        assertEquals("2h 28m", runtimeLabel(148))
    }
}
