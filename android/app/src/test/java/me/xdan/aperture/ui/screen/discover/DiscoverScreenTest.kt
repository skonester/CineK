package me.xdan.aperture.ui.screen.discover

import android.app.Application
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import me.xdan.aperture.data.discover.DiscoverRepository
import me.xdan.aperture.data.local.AppDatabase
import me.xdan.aperture.data.local.entity.MediaEntity
import me.xdan.aperture.data.remoteplayback.*
import me.xdan.aperture.data.sources.*
import me.xdan.aperture.domain.repository.MediaRepository
import me.xdan.aperture.ui.screen.sources.RemoteSourcesViewModel
import me.xdan.aperture.ui.theme.ApertureTheme
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.lang.reflect.Proxy
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w960dp-h540dp-land")
class DiscoverScreenTest {
    @get:Rule val compose = createComposeRule()
    private val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Application>(), AppDatabase::class.java)
        .allowMainThreadQueries().build()
    private val remote = RemoteMediaRepository(db.remoteMediaDao())
    private val models = ViewModelStore()
    private val requests = mutableListOf<SourceQuery>()
    private var played: String? = null
    private val visible = mutableStateOf(true)
    private lateinit var sources: RemoteSourcesViewModel
    private val lookup = object : RemoteSourceLookup {
        override suspend fun find(query: SourceQuery, site: SourceSite): SourceResults {
            requests += query
            return if (site == SourceSite.STREAMING_UNITY) SourceResults(listOf(ResolvedStream(
                SourceSite.FILEMOON, "https://cdn.example/film.m3u8?token=private", "1080p", quality = 1080)))
            else SourceResults(emptyList())
        }
        override suspend fun resolve(site: SourceSite, embedUrl: String) = error("No manual embeds")
    }

    @After fun close() { models.clear(); remote.close(); db.close() }

    private fun show() {
        val library = Proxy.newProxyInstance(MediaRepository::class.java.classLoader, arrayOf(MediaRepository::class.java)) { _, method, _ ->
            if (method.name == "getAllMedia") flowOf(emptyList<MediaEntity>()) else error("Unexpected library call: ${method.name}")
        } as MediaRepository
        val discover = DiscoverViewModel(DiscoverRepository(DiscoverTest.FakeTmdb(), "fixture", LocalDate::now), library)
        sources = RemoteSourcesViewModel(remote, lookup)
        models.put("discover", discover); models.put("sources", sources)
        compose.setContent {
            ApertureTheme {
                Surface(Modifier.fillMaxSize(), colors = SurfaceDefaults.colors(
                    containerColor = MaterialTheme.colorScheme.background, contentColor = MaterialTheme.colorScheme.onBackground)) {
                    if (visible.value) {
                        DiscoverScreen(discover, { _, _ -> }, { id, _, _ -> played = id }, null, FocusRequester(), {}, sources = sources)
                    }
                }
            }
        }
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun movieThumbnailAutomaticallyFindsSourcesAndRemoteCanPlay() {
        show()
        compose.onNodeWithContentDescription("Movie 1").performClick()
        compose.onNodeWithTag("remote-source-play-0").assertIsFocused()
            .performKeyInput { pressKey(Key.DirectionCenter) }
        compose.runOnIdle {
            assertEquals(3, requests.size)
            assertTrue(requests.all { it.tmdbId == 11 && it.title == "Movie 1" && it.year == 1999 && it.type == SourceMediaType.MOVIE })
            assertNotNull(played)
        }
        compose.onAllNodes(hasSetTextAction()).assertCountEquals(0)
        val bitmap = compose.onNodeWithTag("discover-title-dialog").captureToImage().asAndroidBitmap()
        val target = java.io.File("build/remote-ui/discover-sources.png").apply { parentFile?.mkdirs() }
        target.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun showSourcesUseTheSelectedSeasonAndEpisodeWithoutTyping() {
        show()
        compose.onNodeWithText("Shows", useUnmergedTree = true).performClick()
        // Shows without a poster use the title fallback inside the clickable card.
        compose.onAllNodesWithText("A Show")[0].performClick()
        compose.runOnIdle { assertTrue(requests.isEmpty()) }
        compose.onNodeWithTag("discover-season-2").performScrollTo().performClick()
        compose.onNodeWithTag("discover-episode-2-2").performScrollTo().performClick()
        compose.onNodeWithTag("remote-source-play-0").assertExists()
        compose.runOnIdle {
            assertEquals(3, requests.size)
            assertTrue(requests.all { it.tmdbId == 5 && it.season == 2 && it.episode == 2 && it.type == SourceMediaType.SHOW })
        }
        compose.onAllNodes(hasSetTextAction()).assertCountEquals(0)
    }

    @Test fun returningFromPlaybackKeepsTheSourceAndResumesTheSameTmdbTitle() {
        show()
        compose.onNodeWithContentDescription("Movie 1").performClick()
        compose.onNodeWithTag("remote-source-play-0").performClick()
        compose.runOnIdle { visible.value = false }
        val id = requireNotNull(played)
        val token = remote.beginPlayback(id)
        remote.record(id, token, StreamPlaybackSnapshot(20_000, 100_000, hasStarted = true))
        runBlocking { remote.awaitWrites() }
        assertEquals(20_000L, runBlocking { remote.get(id)?.positionMs })
        compose.runOnIdle { visible.value = true }
        compose.waitUntil(5_000) {
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
            sources.state.value.media?.positionMs == 20_000L
        }
        compose.onNodeWithTag("remote-source-play-0").assertIsFocused()
        compose.onNodeWithTag("remote-source-play-0").assertTextContains("Resume at 0:20")
        compose.runOnIdle { assertEquals(3, requests.size) }
        compose.onNodeWithText("Close").performScrollTo().performClick()
        compose.onNodeWithText("Recently watched").assertExists()
    }
}
