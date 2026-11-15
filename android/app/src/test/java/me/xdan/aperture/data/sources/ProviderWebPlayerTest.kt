package me.xdan.aperture.data.sources

import org.junit.Assert.*
import org.junit.Test

class ProviderWebPlayerTest {
    private val movie = SourceQuery("Archive Film", tmdbId = 10331)

    @Test fun vidsrcWebPlayerUsesTheSelectedMovieOrEpisode() {
        assertEquals("https://vidsrc.to/embed/movie/10331", providerWebTarget(SourceSite.VIDSRC, movie)?.url)
        val episode = SourceQuery("Archive Show", SourceMediaType.SHOW, tmdbId = 42, season = 2, episode = 5)
        val target = requireNotNull(providerWebTarget(SourceSite.VIDSRC, episode))
        assertEquals("https://vidsrc.to/embed/tv/42/2/5", target.url)
        assertEquals("Archive Show · S2E5", target.title)
        assertEquals("https://vidsrc.to/embed/movie/tt0063350",
            providerWebTarget(SourceSite.VIDSRC, SourceQuery("Archive Film", imdbId = "tt0063350"))?.url)
        assertNull(providerWebTarget(SourceSite.VIDSRC, SourceQuery("No ID")))
    }

    @Test fun onlyBrowserFailuresOfferProviderPages() {
        assertNotNull(browserFallback(SourceFailure(SourceSite.VIDSRC, SourceFailureKind.UNSUPPORTED, "New web player"), movie))
        val blocked = SourceFailure(SourceSite.RIDOMOVIES, SourceFailureKind.BLOCKED, "HTTP 403")
        assertEquals("https://ridomovies.tv/", browserFallback(blocked, movie)?.url)
        assertNull(browserFallback(blocked, null))
        assertNull(browserFallback(blocked.copy(kind = SourceFailureKind.NO_RESULTS), movie))
        assertNull(browserFallback(blocked.copy(kind = SourceFailureKind.TIMEOUT), movie))
        assertNull(browserFallback(blocked.copy(site = SourceSite.FILEMOON), movie))
    }

    @Test fun playerNavigationAllowsProviderRedirectsAndHttpsFramesButBlocksExternalLaunches() {
        val target = requireNotNull(providerWebTarget(SourceSite.VIDSRC, movie))
        assertTrue(providerNavigationAllowed(target, "https://vidsrc.sh/embed/movie/10331/", true))
        assertTrue(providerNavigationAllowed(target, "https://player.example/embed/10331", false))
        for (url in listOf("https://ads.example/", "https://vidsrc.sh.evil.example/", "http://vidsrc.to/",
            "intent://player/", "file:///data/data/app/secret", "content://files/1", "javascript:alert(1)",
            "https://user:password@vidsrc.to/")) {
            assertFalse(url, providerNavigationAllowed(target, url, true))
        }
        val rido = requireNotNull(providerWebTarget(SourceSite.RIDOMOVIES, movie))
        assertTrue(providerNavigationAllowed(rido, "https://ridomovies.tv/movie/archive-film", true))
        assertFalse(providerNavigationAllowed(rido, "https://vidsrc.sh/", true))
    }
}
