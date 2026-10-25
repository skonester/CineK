package me.xdan.aperture.ui.screen.sources

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import me.xdan.aperture.ui.theme.ApertureTheme
import me.xdan.aperture.data.remoteplayback.*
import me.xdan.aperture.data.sources.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w800dp-h360dp-land")
class RemoteSourcesScreenTest {
    @get:Rule val compose = createComposeRule()
    private fun media() = RemoteMediaEntity("stable-id", "Archive Film", "MOVIE", 1968, 10331, null, null, null, null, null,
        positionMs = 20_000, durationMs = 100_000)

    @Test fun touchCanChooseResumeOrStartOverWithAProviderStillLoading() {
        val option = RemoteSourceOption(SourceSite.STREAMING_UNITY,
            ResolvedStream(SourceSite.FILEMOON, "https://cdn.example/film.m3u8?token=private", "1080p", quality = 1080))
        val actions = mutableListOf<Boolean>()
        compose.setContent {
            RemoteSourcesContent(RemoteSourcesUiState(request = media().request(), media = media(),
                options = listOf(option), loading = setOf(SourceSite.RIDOMOVIES)), emptyList(), {},
                { selected, restart -> assertEquals(option, selected); actions += restart }, {}, {})
        }
        compose.onNodeWithTag("remote-source-play-0").performScrollTo()
        compose.onNodeWithTag("remote-source-play-0").performTouchInput { click() }
        compose.onNodeWithText("Start over").performScrollTo().performTouchInput { click() }
        compose.runOnIdle { assertEquals(listOf(false, true), actions) }
        compose.onNodeWithText("https://cdn.example/film.m3u8?token=private").assertDoesNotExist()
    }

    @Test fun savedTitlePickerUsesMetadataAndOffersNoManualLookupFields() {
        compose.setContent {
            RemoteSourcesContent(RemoteSourcesUiState(request = media().request(), media = media()),
                emptyList(), {}, { _, _ -> }, {}, {})
        }
        compose.onNodeWithText("Archive Film").assertExists()
        compose.onAllNodes(hasSetTextAction()).assertCountEquals(0)
        compose.onNodeWithText("Open embed link").assertDoesNotExist()
        compose.onNodeWithText("TMDB ID (optional)").assertDoesNotExist()
        compose.onNodeWithText("Refresh sources").assertExists()
    }

    @Test fun remoteContinueWatchingCardOpensStableIdentity() {
        var opened: String? = null
        compose.setContent { RemoteContinueRow(listOf(media()), { opened = it }) }
        compose.onNodeWithText("Resume at 0:20").performTouchInput { click() }
        compose.runOnIdle { assertEquals("stable-id", opened) }
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun tvRemoteCanPlayFocusedSourceAndPickerRendersInLandscape() {
        val option = RemoteSourceOption(SourceSite.STREAMING_UNITY, ResolvedStream(SourceSite.STREAMING_UNITY,
            "https://cdn.example/film.m3u8", "1080p", quality = 1080))
        var plays = 0
        compose.setContent {
            ApertureTheme {
                Surface(modifier = Modifier.fillMaxSize(), colors = SurfaceDefaults.colors(
                    containerColor = MaterialTheme.colorScheme.background, contentColor = MaterialTheme.colorScheme.onBackground)) {
                    RemoteSourcesContent(RemoteSourcesUiState(request = media().request(), media = media(),
                        options = listOf(option)), emptyList(), {}, { _, _ -> plays++ }, {}, {})
                }
            }
        }
        compose.onNodeWithTag("remote-source-play-0").performScrollTo()
        compose.onNodeWithTag("remote-source-play-0").assertIsFocused().performKeyInput { pressKey(Key.DirectionCenter) }
        compose.runOnIdle { assertEquals(1, plays) }
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        val target = java.io.File("build/remote-ui/source-picker.png").apply { parentFile?.mkdirs() }
        target.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
    }

}
