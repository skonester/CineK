package me.xdan.aperture.data.sources

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import me.xdan.aperture.ui.screen.player.toPlayableItem
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

class StreamingScrapersTest {
    private val config = ScraperConfig(
        ridomoviesOrigin = "https://rido.example/", streamingUnityOrigin = "https://unity.example/",
        vidsrcOrigin = "https://vidsrc.example/", vidsrcKeysUrl = "https://keys.example/keys.json"
    )
    private val movie = SourceQuery("Archive Film", year = 1968)
    private val show = SourceQuery("Archive Show", SourceMediaType.SHOW, season = 2, episode = 5)

    private class Fixtures : ScraperHttpClient {
        val responses = linkedMapOf<String, ScraperPage>()
        val requests = mutableListOf<Pair<String, String?>>()
        fun add(url: String, body: String, finalUrl: String = url) { responses[url] = ScraperPage(finalUrl, body) }
        override suspend fun get(url: String, referer: String?): ScraperPage {
            requests += url to referer
            return responses[url] ?: error("Unexpected fixture request: $url")
        }
    }

    private fun fixture(name: String): String = requireNotNull(javaClass.getResource("/source-fixtures/$name")).readText()
    private fun media(url: String = "https://cdn.example/film.m3u8") = "<video><source src='$url' type='application/x-mpegURL'></video>"

    @Test fun ridomoviesMovieSearchToPackedHostPreservesHeadersAndSubtitles() = runBlocking {
        val http = Fixtures().apply {
            add("https://rido.example/api/search?q=Archive%20Film", fixture("ridomovies-search.json"))
            add("https://rido.example/movie/archive-film", "<iframe src='https://filemoon.example/e/film'></iframe>")
            add("https://filemoon.example/e/film", fixture("filemoon-packed.html"))
        }
        val result = StreamingScrapers(http, config).findSources(movie, setOf(SourceSite.RIDOMOVIES))
        assertTrue(result.failures.toString(), result.failures.isEmpty())
        val stream = result.streams.single()
        assertEquals("https://cdn.example/film.m3u8", stream.url)
        assertEquals(1080, stream.quality)
        assertEquals("application/x-mpegURL", stream.mimeType)
        assertEquals("https://filemoon.example/e/film", stream.headers["Referer"])
        assertEquals("https://cdn.example/en.vtt", stream.subtitles.single().url)
        assertEquals(stream.headers, stream.toPlayableItem(movie.title).headers)
    }

    @Test fun ridomoviesSelectsRequestedEpisode() = runBlocking {
        val http = Fixtures().apply {
            add("https://rido.example/api/search?q=Archive%20Show", fixture("ridomovies-search.json"))
            add("https://rido.example/tv/archive-show/season-2/episode-5", media())
        }
        assertEquals(1, StreamingScrapers(http, config).findSources(show, setOf(SourceSite.RIDOMOVIES)).streams.size)
    }

    @Test fun titleSelectionRejectsAmbiguousRemakesAndUsesExternalIds() {
        val titles = listOf(1968, 1990).map { year ->
            SourceTitle(SourceSite.RIDOMOVIES, "$year", "Archive Film", SourceMediaType.MOVIE,
                "https://rido.example/$year", year, imdbId = "tt$year")
        }
        try { selectTitle(titles, SourceQuery("Archive Film")); fail("Ambiguous title accepted") }
        catch (error: SourceException) { assertEquals(SourceFailureKind.NO_RESULTS, error.kind) }
        assertEquals("1990", selectTitle(titles, SourceQuery("Different localized title", imdbId = "tt1990")).id)
        assertEquals("1968", selectTitle(titles, movie).id)
    }

    @Test fun ridomoviesEmptyAndMalformedCatalogsAreDifferent() = runBlocking {
        for ((body, expected) in listOf("{data:[]}" to SourceFailureKind.NO_RESULTS, "<html>changed</html>" to SourceFailureKind.INVALID_RESPONSE)) {
            val http = Fixtures().apply { add("https://rido.example/api/search?q=Archive%20Film", body) }
            val result = StreamingScrapers(http, config).findSources(movie, setOf(SourceSite.RIDOMOVIES))
            assertEquals(expected, result.failures.single().kind)
        }
    }

    @Test fun streamingUnityMovieUsesInertiaAndVixcloud() = runBlocking {
        val http = unityFixtures(movie)
        http.add("https://unity.example/en/titles/42-archive-film", "<div data-page='{\"props\":{\"title\":{\"id\":42}}}'></div>")
        http.add("https://unity.example/en/iframe/42", "<iframe src='https://vixcloud.example/embed/718'></iframe>")
        http.add("https://vixcloud.example/embed/718", fixture("vixcloud.html"))
        val result = StreamingScrapers(http, config).findSources(movie, setOf(SourceSite.STREAMING_UNITY))
        assertTrue(result.failures.toString(), result.failures.isEmpty())
        val address = requireNotNull(httpUrl(result.streams.single().url))
        assertEquals("/playlist/718", address.encodedPath)
        assertEquals("fixture+token", address.queryParameter("token"))
        assertEquals("1999999999", address.queryParameter("expires"))
        assertEquals("1", address.queryParameter("h"))
        assertEquals("application/x-mpegURL", result.streams.single().mimeType)
    }

    @Test fun streamingUnityLoadsRequestedSeasonBeforeEpisodeLookup() = runBlocking {
        val http = unityFixtures(show).apply {
            add("https://unity.example/en/titles/81-archive-show", fixture("streaming-show.html"))
            add("https://unity.example/en/titles/81-archive-show?season_id=220", fixture("streaming-season-two.html"))
            add("https://unity.example/en/iframe/81?episode_id=2205", media())
        }
        val result = StreamingScrapers(http, config).findSources(show, setOf(SourceSite.STREAMING_UNITY))
        assertTrue(result.failures.toString(), result.failures.isEmpty())
        assertEquals(1, result.streams.size)
        assertTrue(http.requests.any { it.first.endsWith("episode_id=2205") })
    }

    @Test fun streamingUnityRejectsWrongSeasonAndMissingEpisode() = runBlocking {
        for (body in listOf(fixture("streaming-show.html"), fixture("streaming-season-two.html").replace("2205", "2299").replace("\"number\":5", "\"number\":99"))) {
            val http = unityFixtures(show).apply {
                add("https://unity.example/en/titles/81-archive-show", fixture("streaming-show.html"))
                add("https://unity.example/en/titles/81-archive-show?season_id=220", body)
            }
            val result = StreamingScrapers(http, config).findSources(show, setOf(SourceSite.STREAMING_UNITY))
            assertTrue(result.streams.isEmpty())
            assertEquals(1, result.failures.size)
            assertFalse(http.requests.any { it.first.contains("/iframe/") })
        }
    }

    private fun unityFixtures(query: SourceQuery) = Fixtures().apply {
        add(queryUrl("https://unity.example/en/search", "q", query.title), fixture("streaming-search.html"))
    }

    @Test fun filemoonNestedIframeAndPackedJavascriptResolveWithoutExecutingScripts() = runBlocking {
        val http = Fixtures().apply {
            add("https://filemoon.example/e/one", "<iframe src='/e/two'></iframe>")
            add("https://filemoon.example/e/two", fixture("filemoon-packed.html"))
        }
        val result = StreamingScrapers(http, config).resolve(SourceSite.FILEMOON, "https://filemoon.example/e/one")
        assertEquals("https://cdn.example/film.m3u8", result.streams.single().url)
        assertEquals("https://filemoon.example/e/one", http.requests.last().second)
    }

    @Test fun packedDictionaryReplacementDoesNotCascade() {
        val packed = "eval(function(p,a,c,k,e,d){return p;}('0 1',36,2,'1|word'.split('|'),0,{}));"
        assertEquals("1 word", PackedScript.unpack(packed))
        assertEquals("bad", PackedScript.unpack("bad"))
    }

    @Test fun javascriptTrailingCommaRemovalPreservesLiteralContents() {
        val value = literalAfter("window.value = {url: 'https://cdn.example/a,}.m3u8', params:{token:'quoted\\\"value',},};", "window\\.value\\s*=\\s*").obj()
        assertEquals("https://cdn.example/a,}.m3u8", value.text("url"))
        assertEquals("quoted\"value", value["params"].obj().text("token"))
    }

    @Test fun doodPassTokenUsesFinalRedirectHostAndIsNotAnEmbedUrl() = runBlocking {
        val http = Fixtures().apply {
            add("https://dood.example/e/old", fixture("dood.html"), "https://dood-new.example/e/film")
            add("https://dood-new.example/pass_md5/session/fixture-token", "https://cdn.example/media/")
        }
        val stream = StreamingScrapers(http, config).resolve(SourceSite.DOOD, "https://dood.example/e/old").streams.single()
        val url = requireNotNull(httpUrl(stream.url))
        assertEquals("cdn.example", url.host)
        assertEquals("fixture-token", url.queryParameter("token"))
        assertTrue(requireNotNull(url.queryParameter("expiry")).toLong() > 0)
        assertEquals(720, stream.quality)
        assertEquals("https://dood-new.example/e/film", stream.headers["Referer"])
    }

    @Test fun doodRejectsNonHttpTokenResponse() = runBlocking {
        val http = Fixtures().apply {
            add("https://dood.example/e/one", fixture("dood.html"))
            add("https://dood.example/pass_md5/session/fixture-token", "<html>blocked</html>")
        }
        assertEquals(SourceFailureKind.INVALID_RESPONSE,
            StreamingScrapers(http, config).resolve(SourceSite.DOOD, "https://dood.example/e/one").failures.single().kind)
    }

    @Test fun vidsrcUsesRequestedTitleAndEpisodeAndDecryptsAjaxHostLinks() = runBlocking {
        val query = show.copy(tmdbId = 9876)
        val http = object : ScraperHttpClient {
            val requested = mutableListOf<String>()
            override suspend fun get(url: String, referer: String?): ScraperPage {
                requested += url
                val path = requireNotNull(httpUrl(url)).encodedPath
                val body = when (path) {
                    "/embed/tv/9876/2/5" -> "<ul class='episodes'><li><a data-id='episode42'>Selected episode</a></li></ul>"
                    "/keys.json" -> "{\"encrypt\":[\"request-key\"],\"decrypt\":[\"response-key\"]}"
                    "/ajax/embed/episode/episode42/sources" -> {
                        assertNotNull(requireNotNull(httpUrl(url)).queryParameter("token"))
                        "{\"result\":[{\"id\":\"source42\"}]}"
                    }
                    "/ajax/embed/source/source42" -> {
                        val cipher = Cipher.getInstance("RC4").apply { init(Cipher.ENCRYPT_MODE, SecretKeySpec("response-key".toByteArray(), "RC4")) }
                        val encrypted = Base64.getUrlEncoder().encodeToString(cipher.doFinal("https://filemoon.example/e/film".toByteArray()))
                        "{\"result\":{\"url\":\"$encrypted\"}}"
                    }
                    "/e/film" -> fixture("filemoon-packed.html")
                    else -> error("Unexpected route: $path")
                }
                return ScraperPage(url, body)
            }
        }
        val result = StreamingScrapers(http, config).findSources(query, setOf(SourceSite.VIDSRC))
        assertTrue(result.failures.toString(), result.failures.isEmpty())
        assertEquals("https://cdn.example/film.m3u8", result.streams.single().url)
        assertTrue(http.requested.first().endsWith("/9876/2/5"))
    }

    @Test fun rc4MatchesPublishedTestVector() {
        val expected = "BBF316E8D940AF0AD3"
        assertEquals(expected, rc4("Key", "Plaintext".toByteArray()).joinToString("") { "%02X".format(it.toInt() and 255) })
    }

    @Test fun vidsrcRequiresAnExternalIdAndUnsupportedPagesAreNotReturnedAsStreams() = runBlocking {
        val missingId = StreamingScrapers(Fixtures(), config).findSources(movie, setOf(SourceSite.VIDSRC))
        assertEquals(SourceFailureKind.NO_RESULTS, missingId.failures.single().kind)
        val http = Fixtures().apply { add("https://vidsrc.example/embed/movie/42", "<html>Unsupported player</html>") }
        val unsupported = StreamingScrapers(http, config).findSources(movie.copy(tmdbId = 42), setOf(SourceSite.VIDSRC))
        assertTrue(unsupported.streams.isEmpty())
        assertEquals(SourceFailureKind.UNSUPPORTED, unsupported.failures.single().kind)
    }

    @Test fun iframeCyclesAreBoundedAndOneFailedHostDoesNotHideAnother() = runBlocking {
        val http = Fixtures().apply {
            add("https://source.example/root", "<iframe src='/cycle'></iframe><iframe src='/working'></iframe>")
            add("https://source.example/cycle", "<iframe src='/root'></iframe>")
            add("https://source.example/working", media())
        }
        val result = StreamingScrapers(http, config).resolve(SourceSite.FILEMOON, "https://source.example/root")
        assertEquals(1, result.streams.size)
        assertEquals(SourceFailureKind.UNSUPPORTED, result.failures.single().kind)
        assertTrue(http.requests.size <= 4)
    }

    @Test fun protocolRelativeSourcesDeduplicateAndJavascriptUrlsAreIgnored() {
        val body = "jwplayer('p').setup({sources:[{file:'//cdn.example/film.m3u8',label:'720p'}," +
            "{file:'//cdn.example/film.m3u8'},{file:'javascript:evil()',type:'hls'}]});"
        val streams = EmbedParsing.streams(ScraperPage("https://filemoon.example/e/one", body), SourceSite.FILEMOON)
        assertEquals(1, streams.size)
        assertEquals("https://cdn.example/film.m3u8", streams.single().url)
    }

    @Test fun directMediaLinksAreNotDownloadedByTheResolver() = runBlocking {
        val http = Fixtures()
        val result = StreamingScrapers(http, config).resolve(SourceSite.VIDSRC,
            "https://cdn.example/movie.mp4?token=fixture", "https://vidsrc.example/")
        assertEquals("video/mp4", result.streams.single().mimeType)
        assertEquals("https://vidsrc.example/", result.streams.single().headers["Referer"])
        assertTrue(http.requests.isEmpty())
    }

    @Test fun responseUrlsAndProviderFailuresAreIsolated() = runBlocking {
        val http = ScraperHttpClient { url, _ ->
            if (url.contains("rido.example")) throw SourceException(SourceFailureKind.BLOCKED, "Source refused the request (HTTP 403).")
            when {
                url.contains("/en/search") -> ScraperPage(url, fixture("streaming-search.html"))
                url.contains("/en/titles/") -> ScraperPage(url, "{props:{title:{id:42}}}")
                else -> ScraperPage(url, media())
            }
        }
        val result = StreamingScrapers(http, config).findSources(movie, setOf(SourceSite.RIDOMOVIES, SourceSite.STREAMING_UNITY))
        assertEquals(1, result.streams.size)
        assertEquals(SourceSite.RIDOMOVIES, result.failures.single().site)
        assertEquals(SourceFailureKind.BLOCKED, result.failures.single().kind)
    }

    @Test fun providerDeadlineReturnsTimeoutButCallerCancellationPropagates() = runBlocking {
        val started = CompletableDeferred<Unit>()
        val http = ScraperHttpClient { _, _ -> started.complete(Unit); awaitCancellation() }
        val scraper = StreamingScrapers(http, config.copy(providerTimeoutMs = 30))
        val result = scraper.findSources(movie, setOf(SourceSite.RIDOMOVIES))
        assertEquals(SourceFailureKind.TIMEOUT, result.failures.single().kind)
        val cancelled = async { StreamingScrapers(http, config).findSources(movie, setOf(SourceSite.RIDOMOVIES)) }
        delay(10)
        cancelled.cancelAndJoin()
        assertTrue(cancelled.isCancelled)
    }

    @Test fun transportPreservesRefererAndClassifiesBlockedResponsesWithoutLeakingUrls() = runBlocking {
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            assertEquals("https://embed.example/", chain.request().header("Referer"))
            assertEquals(ScraperUserAgent, chain.request().header("User-Agent"))
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(403).message("Forbidden")
                .body("blocked".toResponseBody()).build()
        }.build()
        try {
            OkHttpScraperClient(client).get("https://source.example/?token=private", "https://embed.example/")
            fail("403 accepted")
        } catch (error: SourceException) {
            assertEquals(SourceFailureKind.BLOCKED, error.kind)
            assertFalse(error.message.orEmpty().contains("private"))
        } finally { client.dispatcher.executorService.shutdown(); client.connectionPool.evictAll() }
    }

    @Test fun transportRejectsOversizedAndBrowserChallengeResponses() = runBlocking {
        for ((body, expected) in listOf("x".repeat(OkHttpScraperClient.MaxResponseBytes + 1) to SourceFailureKind.INVALID_RESPONSE,
            "<html>Just a moment... cf-chl-token</html>" to SourceFailureKind.BLOCKED)) {
            val client = OkHttpClient.Builder().addInterceptor { chain ->
                Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                    .body(body.toResponseBody()).build()
            }.build()
            try {
                OkHttpScraperClient(client).get("https://source.example/", null); fail("Bad response accepted")
            } catch (error: SourceException) { assertEquals(expected, error.kind) }
            finally { client.dispatcher.executorService.shutdown(); client.connectionPool.evictAll() }
        }
    }

    @Test fun malformedQueriesAndCredentialUrlsAreRejected() = runBlocking {
        try { SourceQuery("Show", SourceMediaType.SHOW, season = 1); fail("Partial episode accepted") }
        catch (_: IllegalArgumentException) { }
        val result = StreamingScrapers(Fixtures(), config).resolve(SourceSite.DOOD, "https://user:password@source.example/")
        assertEquals(SourceFailureKind.INVALID_RESPONSE, result.failures.single().kind)
    }
}
