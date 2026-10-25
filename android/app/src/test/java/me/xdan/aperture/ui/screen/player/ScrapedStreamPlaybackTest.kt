package me.xdan.aperture.ui.screen.player

import me.xdan.aperture.data.sources.ResolvedStream
import me.xdan.aperture.data.sources.SourceSite
import me.xdan.aperture.data.sources.SourceSubtitle
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ScrapedStreamPlaybackTest {
    @Test fun completeMediaItemPreservesMimeHeadersAndSubtitleTracks() {
        val headers = mapOf("Referer" to "https://host.example/embed/1", "User-Agent" to "fixture")
        val source = ResolvedStream(SourceSite.FILEMOON, "https://cdn.example/stream?id=1", "1080p",
            mimeType = "application/x-mpegURL", headers = headers,
            subtitles = listOf(SourceSubtitle("https://cdn.example/en.srt?token=fixture", "English", "en")))
        val item = source.toMediaItem("Archive Film")
        val configuration = requireNotNull(item.localConfiguration)
        assertEquals("application/x-mpegURL", configuration.mimeType)
        assertEquals(headers, (configuration.tag as RequestHeaders).values)
        assertEquals("application/x-subrip", configuration.subtitleConfigurations.single().mimeType)
        assertEquals("en", configuration.subtitleConfigurations.single().language)
        assertEquals("Archive Film", item.mediaMetadata.title)
    }
}
