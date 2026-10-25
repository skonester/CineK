package me.xdan.aperture.data.sources

import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.util.concurrent.TimeUnit

/** Explicit opt-in only: normal builds/tests never depend on third-party hosts. */
class ScraperLiveCheckTest {
    @Test fun streamingUnityMovieResolvesToAnHlsManifest() = runBlocking {
        assumeTrue("Set CINEK_SCRAPER_LIVE=1 to contact the sources.", System.getenv("CINEK_SCRAPER_LIVE") == "1")
        val client = OkHttpClient.Builder().callTimeout(25, TimeUnit.SECONDS).build()
        try {
            val http = OkHttpScraperClient(client)
            val query = SourceQuery("Night of the Living Dead", year = 1968, tmdbId = 10331, imdbId = "tt0063350")
            val result = StreamingScrapers(http).findSources(query, setOf(SourceSite.STREAMING_UNITY))
            assertTrue(result.failures.toString(), result.streams.isNotEmpty())
            val stream = result.streams.first()
            val playlist = http.get(stream.url, stream.headers["Referer"])
            assertTrue("Resolved response must be an HLS manifest.", playlist.body.trimStart().startsWith("#EXTM3U"))
            println("StreamingUnity: title lookup, embed resolution, and HLS manifest verified; no media segments downloaded.")
        } finally { client.dispatcher.executorService.shutdown(); client.connectionPool.evictAll() }
    }

    @Test fun reportCurrentCatalogAndVidsrcAvailability() = runBlocking {
        assumeTrue("Set CINEK_SCRAPER_LIVE=1 to contact the sources.", System.getenv("CINEK_SCRAPER_LIVE") == "1")
        val client = OkHttpClient.Builder().callTimeout(15, TimeUnit.SECONDS).build()
        try {
            val scrapers = StreamingScrapers(OkHttpScraperClient(client), ScraperConfig(providerTimeoutMs = 20_000))
            val query = SourceQuery("Night of the Living Dead", year = 1968, tmdbId = 10331, imdbId = "tt0063350")
            for (site in listOf(SourceSite.RIDOMOVIES, SourceSite.VIDSRC)) {
                val result = scrapers.findSources(query, setOf(site))
                assertTrue("An unavailable source must report a failure.", result.streams.isNotEmpty() || result.failures.isNotEmpty())
                println("${site.displayName}: streams=${result.streams.size}; failures=${result.failures.map { it.kind }}")
            }
        } finally { client.dispatcher.executorService.shutdown(); client.connectionPool.evictAll() }
    }
}
