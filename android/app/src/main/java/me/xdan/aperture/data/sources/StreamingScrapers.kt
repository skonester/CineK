package me.xdan.aperture.data.sources

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withTimeout

/**
 * Public entry point for movie/episode lookup and embed resolution. Network work is suspending;
 * no browser, JavaScript runtime, account credentials, or Flixvision initialization is involved.
 */
class StreamingScrapers(private val http: ScraperHttpClient, private val config: ScraperConfig = ScraperConfig()) {
    private val catalogs = mapOf(
        SourceSite.RIDOMOVIES to RidomoviesScraper(http, config),
        SourceSite.STREAMING_UNITY to StreamingUnityScraper(http, config)
    )
    private val resolvers = HostResolvers(http, config)

    suspend fun search(site: SourceSite, query: SourceQuery): List<SourceTitle> = timed {
        val catalog = catalogs[site] ?: throw SourceException(SourceFailureKind.UNSUPPORTED,
            "${site.displayName} resolves embed links; it has no title search.")
        catalog.search(query)
    }

    suspend fun findSources(
        query: SourceQuery,
        sites: Set<SourceSite> = setOf(SourceSite.RIDOMOVIES, SourceSite.STREAMING_UNITY, SourceSite.VIDSRC)
    ): SourceResults = coroutineScope {
        val results = sites.map { site ->
            async {
                attempt(site) {
                    val page = when (site) {
                        SourceSite.RIDOMOVIES, SourceSite.STREAMING_UNITY -> catalogs.getValue(site).playbackPage(query)
                        SourceSite.VIDSRC -> http.get(vidsrcEmbed(query), null)
                        else -> throw SourceException(SourceFailureKind.UNSUPPORTED, "Supply an embed URL to ${site.displayName}.")
                    }
                    resolvers.resolvePage(page, site)
                }
            }
        }.awaitAll()
        SourceResults(results.flatMap { it.streams }.distinctBy { it.url to it.headers }
            .sortedByDescending { it.quality ?: 0 }, results.flatMap { it.failures })
    }

    /** Resolve a selected host/mirror link directly, without searching the catalogs. */
    suspend fun resolve(site: SourceSite, embedUrl: String, referer: String? = null): SourceResults = attempt(site) {
        if (httpUrl(embedUrl) == null) throw SourceException(SourceFailureKind.INVALID_RESPONSE, "Invalid embed URL.")
        resolvers.resolve(embedUrl, site, referer)
    }

    private fun vidsrcEmbed(query: SourceQuery): String {
        val id = query.tmdbId?.toString() ?: query.imdbId
            ?: throw SourceException(SourceFailureKind.NO_RESULTS, "Vidsrc needs a TMDB or IMDb title ID.")
        val path = if (query.type == SourceMediaType.MOVIE) "embed/movie/$id" else {
            val season = query.season ?: throw SourceException(SourceFailureKind.NO_RESULTS, "Select a season and episode.")
            "embed/tv/$id/$season/${query.episode}"
        }
        return endpoint(config.vidsrcOrigin, path)
    }

    private suspend fun attempt(site: SourceSite, block: suspend () -> SourceResults): SourceResults = try {
        val result = timed(block)
        if (result.streams.isEmpty() && result.failures.isEmpty()) {
            result.copy(failures = listOf(SourceFailure(site, SourceFailureKind.NO_RESULTS, "No playable sources were found.")))
        } else result
    } catch (e: CancellationException) { throw e }
    catch (e: Exception) { SourceResults(emptyList(), listOf(failure(site, e))) }

    private suspend fun <T> timed(block: suspend () -> T): T = try {
        withTimeout(config.providerTimeoutMs) { block() }
    } catch (e: TimeoutCancellationException) {
        // A provider deadline becomes a result, while cancellation by the caller still propagates.
        currentCoroutineContext().ensureActive()
        throw SourceException(SourceFailureKind.TIMEOUT, "Source lookup timed out.")
    }
}
