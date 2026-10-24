package me.xdan.aperture.ui.screen.live

import android.app.Application
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import me.xdan.aperture.data.live.ChannelSource
import me.xdan.aperture.data.live.LiveChannel
import me.xdan.aperture.data.live.LiveChannelRepository
import me.xdan.aperture.data.live.LiveChannelState
import me.xdan.aperture.data.live.M3uChannels
import me.xdan.aperture.ui.screen.player.looksLikeHls
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
// A landscape phone, the size the app is locked to on phones.
@Config(sdk = [35], qualifiers = "w800dp-h360dp-land")
class LiveTvTest {
    @get:Rule
    val compose = createComposeRule()

    private val app get() = ApplicationProvider.getApplicationContext<Application>()

    // Two entries in the format of the free Pluto TV and Samsung TV Plus playlists.
    private val freePlaylist = """
        #EXTM3U url-tvg="https://example.com/epg.xml.gz"
        #EXTINF:-1 channel-id="62ba" tvg-id="62ba" tvg-chno="160" tvg-name="00s Replay" tvg-logo="https://images.example.com/62ba.png" group-title="Movies",00s Replay
        https://jmp2.uk/plu-62ba60f059624e000781c436.m3u8
        #EXTINF:-1 tvg-chno="2050" tvg-logo="https://images.example.com/news.png" group-title="News, Weather",Breaking, Live
        https://jmp2.uk/stvp-USBB320000397
    """.trimIndent()

    @Test
    fun parsesFreePlaylistEntries() {
        val channels = M3uChannels.parse(freePlaylist, "pluto", "https://example.com/plutotv_us.m3u")
        assertEquals(2, channels.size)
        with(channels[0]) {
            assertEquals("00s Replay", name)
            assertEquals("Movies", group)
            assertEquals("160", number)
            assertEquals("https://images.example.com/62ba.png", logo)
            assertEquals("https://jmp2.uk/plu-62ba60f059624e000781c436.m3u8", url)
        }
        // Commas inside quoted attributes don't end the attribute list; the title keeps its own comma.
        assertEquals("News, Weather", channels[1].group)
        assertEquals("Breaking, Live", channels[1].name)
    }

    @Test
    fun rejectsFilesThatAreNotChannelPlaylists() {
        val notM3u = runCatching { M3uChannels.parse("<html></html>", "s", "https://example.com/a") }
        assertTrue(notM3u.exceptionOrNull()?.message.orEmpty().contains("not an M3U"))
        val hlsStream = runCatching {
            M3uChannels.parse("#EXTM3U\n#EXT-X-STREAM-INF:BANDWIDTH=1\nlow.m3u8", "s", "https://example.com/master.m3u8")
        }
        assertTrue(hlsStream.exceptionOrNull()?.message.orEmpty().contains("HLS stream"))
    }

    @Test
    fun resolvesRelativeLinksAndStripsHeaderSuffixes() {
        val text = "#EXTM3U\n#EXTINF:-1,Relative\nlive/one.m3u8\n" +
            "#EXTINF:-1,With headers\nhttp://tv.example.com/two.ts|User-Agent=Mozilla\n" +
            "#EXTINF:-1,Duplicate\nhttp://tv.example.com/two.ts\n"
        val web = M3uChannels.parse(text, "s", "https://lists.example.com/tv/list.m3u")
        assertEquals(listOf("https://lists.example.com/tv/live/one.m3u8", "http://tv.example.com/two.ts"), web.map { it.url })
        assertEquals("With headers", web[1].name)
        // An imported file has no folder to resolve against, so relative entries are skipped.
        val imported = M3uChannels.parse(text, "s", "content://com.android.providers.downloads/document/12")
        assertEquals(listOf("http://tv.example.com/two.ts"), imported.map { it.url })
    }

    private val state = LiveChannelState(
        sources = listOf(ChannelSource("a", "Pluto TV · US", "https://a"), ChannelSource("b", "Mine", "https://b")),
        channels = listOf(
            LiveChannel("a", "CNN Headlines", "https://a/1", group = "News", number = "101"),
            LiveChannel("a", "Weather Now", "https://a/2", group = "News", number = "102"),
            LiveChannel("a", "Action Movies", "https://a/3", group = "Movies", number = "200"),
            LiveChannel("b", "Local", "https://b/1", group = "Local")
        ),
        loaded = true
    )

    @Test
    fun liveTvUiFiltersBySourceCategoryAndSearch() {
        val first = buildLiveTvUi(state, LiveTvSelection())
        assertEquals("a", first.source?.id)
        assertEquals(listOf(ALL_CHANNELS to 3, "Movies" to 1, "News" to 2), first.categories)
        assertEquals(3, first.visibleChannels.size)
        assertEquals(mapOf("a" to 3, "b" to 1), first.channelCounts)

        val news = buildLiveTvUi(state, LiveTvSelection(sourceId = "a", category = "News"))
        assertEquals(listOf("CNN Headlines", "Weather Now"), news.visibleChannels.map { it.name })

        val byNumber = buildLiveTvUi(state, LiveTvSelection(sourceId = "a", query = " 200 "))
        assertEquals(listOf("Action Movies"), byNumber.visibleChannels.map { it.name })

        // A category that the newly selected playlist lacks falls back to all channels.
        val other = buildLiveTvUi(state, LiveTvSelection(sourceId = "b", category = "News"))
        assertEquals(ALL_CHANNELS, other.category)
        assertEquals(listOf("Local"), other.visibleChannels.map { it.name })
    }

    @Test
    fun channelQueueFollowsTheCategoryButNotTheSearch() {
        assertEquals(listOf("https://a/1", "https://a/2"), liveTvQueue(state, "a", "News").map { it.url })
        assertEquals(3, liveTvQueue(state, "a", ALL_CHANNELS).size)
    }

    @Test
    fun liveStreamsWithoutExtensionsAreTreatedAsHls() {
        assertTrue(looksLikeHls("https://jmp2.uk/plu-62ba.m3u8", live = false))
        assertTrue(looksLikeHls("https://x.example.com/master.m3u8?token=1", live = false))
        assertTrue(looksLikeHls("https://jmp2.uk/stvp-USBB320000397", live = true))
        assertTrue(looksLikeHls("https://epg.provider.plex.tv/library/parts/6a16-63ee/?X-Plex-Token=t", live = true))
        assertFalse(looksLikeHls("http://tv.example.com/channel.ts", live = true))
        assertFalse(looksLikeHls("https://example.com/movie", live = false))
    }

    private fun writeSavedChannels() {
        File(app.filesDir, "live-channels.json").writeText(
            """{"sources":[{"id":"a","name":"Pluto TV · US","location":"https://a"}],
               "channels":[
                 {"sourceId":"a","name":"CNN Headlines","url":"https://a/1","group":"News","logo":"","tvgId":"","number":"101"},
                 {"sourceId":"a","name":"Action Movies","url":"https://a/3","group":"Movies","logo":"","tvgId":"","number":"200"}
               ]}"""
        )
    }

    private fun loadedRepository() = LiveChannelRepository(app, OkHttpClient()).also { repository ->
        runBlocking { withTimeout(5_000) { repository.state.first { it.loaded } } }
    }

    @Test
    fun playlistsSurviveARestart() {
        writeSavedChannels()
        val first = loadedRepository()
        assertEquals(2, first.state.value.channels.size)

        // An unreadable file is kept as a source with an error, like the desktop app.
        assertNotNull(first.add("Imported", "content://missing.example/list.m3u"))
        runBlocking { withTimeout(5_000) { first.state.first { it.loadingSourceId == null } } }
        assertNotNull(first.state.value.error)
        assertNull(first.add("Bad", "not a url"))

        // Saving runs in the background, so wait for it to reach the file before
        // "restarting"; reading straight away raced the save on slower CI runners.
        val saved = File(app.filesDir, "live-channels.json")
        runBlocking {
            withTimeout(5_000) {
                while (runCatching { saved.readText() }.getOrDefault("").contains("Imported").not()) delay(20)
            }
        }
        val restarted = loadedRepository()
        assertEquals(listOf("Pluto TV · US", "Imported"), restarted.state.value.sources.map { it.name })
        assertEquals(2, restarted.state.value.channels.size)
    }

    @Test
    fun liveTvScreenBrowsesAndPlaysChannels() {
        writeSavedChannels()
        val viewModel = LiveTvViewModel(loadedRepository())
        var played: Pair<String, String>? = null
        compose.setContent {
            LiveTvScreen(
                viewModel = viewModel,
                onPlayChannel = { channel, category -> played = channel.name to category },
                drawerFocusRequester = null,
                contentEntryFocusRequester = FocusRequester(),
                onContentFocused = {}
            )
        }
        compose.onNodeWithText("Pluto TV · US").assertExists()
        compose.onNodeWithText("News · 1").performTouchInput { click() }
        compose.onNodeWithText("Action Movies").assertDoesNotExist()
        compose.onNodeWithText("CNN Headlines").performTouchInput { click() }
        compose.runOnIdle { assertEquals("CNN Headlines" to "News", played) }
    }

    @Test
    fun emptyLiveTvOffersTheFreeChannelList() {
        File(app.filesDir, "live-channels.json").delete()
        val viewModel = LiveTvViewModel(loadedRepository())
        compose.setContent {
            LiveTvScreen(viewModel, { _, _ -> }, null, FocusRequester(), {})
        }
        compose.onNodeWithText("Watch free live TV").assertExists()
        compose.onNodeWithText("Choose free channels").performTouchInput { click() }
        compose.onNodeWithText("Pluto TV · US").assertExists()
        compose.onNodeWithText("Pluto TV · CA").assertExists()
    }
}
