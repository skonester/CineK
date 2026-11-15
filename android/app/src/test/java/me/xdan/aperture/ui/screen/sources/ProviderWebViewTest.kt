package me.xdan.aperture.ui.screen.sources

import android.view.View
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import me.xdan.aperture.data.sources.ProviderWebTarget
import me.xdan.aperture.data.sources.SourceSite
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ProviderWebViewTest {
    private val target = ProviderWebTarget(SourceSite.VIDSRC, "https://vidsrc.to/embed/movie/10331", "Archive Film")

    @Test fun webPlayerEnablesWebMediaAndStopsWhenTheAppLeaves() {
        val player = ProviderWebView(RuntimeEnvironment.getApplication(), target, {}, {})
        val web = player.web
        with(web.settings) {
            assertTrue(javaScriptEnabled)
            assertTrue(domStorageEnabled)
            assertTrue(mediaPlaybackRequiresUserGesture)
            assertFalse(javaScriptCanOpenWindowsAutomatically)
            assertFalse(allowFileAccess)
            assertFalse(allowContentAccess)
            assertEquals(WebSettings.MIXED_CONTENT_NEVER_ALLOW, mixedContentMode)
        }
        assertEquals(target.url, shadowOf(web).lastLoadedUrl)
        player.stopPlayback()
        assertEquals("about:blank", shadowOf(web).lastLoadedUrl)
        player.resume()
        assertEquals(target.url, shadowOf(web).lastLoadedUrl)
        player.release()
        assertEquals(0, player.childCount)
        assertFalse(player.keepScreenOn)
        player.release() // Disposal and view removal may both reach cleanup.
    }

    @Test fun backExitsTheFullScreenVideoAndRestoresTheWebPlayer() {
        val fullscreen = mutableListOf<Boolean>()
        var hidden = 0
        val player = ProviderWebView(RuntimeEnvironment.getApplication(), target, fullscreen::add, {})
        val video = View(RuntimeEnvironment.getApplication())
        requireNotNull(player.web.webChromeClient).onShowCustomView(video, object : WebChromeClient.CustomViewCallback {
            override fun onCustomViewHidden() { hidden++ }
        })
        assertEquals(View.INVISIBLE, player.web.visibility)
        assertEquals(2, player.childCount)
        assertTrue(player.exitFullscreen())
        assertEquals(listOf(true, false), fullscreen)
        assertEquals(1, hidden)
        assertEquals(View.VISIBLE, player.web.visibility)
        assertFalse(player.exitFullscreen())
        player.release()
    }
}
