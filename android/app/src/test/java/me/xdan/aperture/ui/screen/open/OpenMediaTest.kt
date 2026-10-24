package me.xdan.aperture.ui.screen.open

import android.Manifest
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.media3.common.MimeTypes
import androidx.test.core.app.ApplicationProvider
import me.xdan.aperture.ui.component.LibraryAccess
import me.xdan.aperture.ui.component.LibraryAccessController
import me.xdan.aperture.ui.component.LibraryAccessPrompt
import me.xdan.aperture.ui.component.LibraryPermissions
import me.xdan.aperture.ui.component.LocalLibraryAccess
import me.xdan.aperture.ui.screen.player.externalMediaItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class OpenMediaTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun accessReflectsGrantedVideoAndMusicPermissions() {
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        assertEquals(LibraryAccess.None, LibraryPermissions.current(app))

        shadowOf(app).grantPermissions(Manifest.permission.READ_MEDIA_VIDEO)
        val videosOnly = LibraryPermissions.current(app)
        assertTrue(videosOnly.videos)
        assertFalse(videosOnly.music)
        assertFalse(videosOnly.isComplete)

        shadowOf(app).grantPermissions(Manifest.permission.READ_MEDIA_AUDIO)
        assertTrue(LibraryPermissions.current(app).isComplete)
    }

    @Test
    fun limitedVideoAccessIsReportedSeparately() {
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        shadowOf(app).grantPermissions(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
        val access = LibraryPermissions.current(app)
        assertFalse(access.videos)
        assertTrue(access.selectedVideosOnly)
        assertTrue(access.hasAny)
    }

    @Test
    fun promptShowsStatusAndRoutesButtons() {
        val controller = LibraryAccessController().apply {
            access = LibraryAccess(videos = true, music = false)
        }
        var requests = 0
        controller.launchRequest = { requests++ }
        var dismissed = false
        compose.setContent { LibraryAccessPrompt(controller, onDismiss = { dismissed = true }) }

        compose.onNodeWithText("Allowed").assertExists()
        compose.onNodeWithText("Not allowed").assertExists()
        compose.onNodeWithText("Allow access").performTouchInput { click() }
        compose.runOnIdle { assertEquals(1, requests) }
        compose.onNodeWithText("Not now").performTouchInput { click() }
        compose.runOnIdle { assertTrue(dismissed) }
    }

    @Test
    fun promptPointsToSettingsOnceAndroidStopsAsking() {
        val controller = LibraryAccessController().apply {
            access = LibraryAccess.None
            mustUseSettings = true
        }
        compose.setContent { LibraryAccessPrompt(controller, onDismiss = {}) }
        compose.onNodeWithText("Open Android settings").assertExists()
    }

    @Test
    fun openScreenActionsReachTheOpener() {
        var files = 0
        var streams = 0
        val opener = MediaOpener().apply {
            pickFile = { files++ }
            askForStream = { streams++ }
        }
        compose.setContent {
            CompositionLocalProvider(
                LocalMediaOpener provides opener,
                LocalLibraryAccess provides LibraryAccessController()
            ) {
                OpenMediaScreen(
                    isScanning = false,
                    onRescan = {},
                    onOpenLibrarySettings = {},
                    drawerFocusRequester = null,
                    contentEntryFocusRequester = FocusRequester(),
                    onContentFocused = {}
                )
            }
        }
        compose.onNodeWithText("Open a file").performTouchInput { click() }
        compose.onNodeWithText("Play a stream").performTouchInput { click() }
        compose.runOnIdle {
            assertEquals(1, files)
            assertEquals(1, streams)
        }
    }

    @Test
    fun streamAddressesAreValidated() {
        assertNotNull(streamAddressError(""))
        assertNotNull(streamAddressError("ftp://example.com/video.mp4"))
        assertNotNull(streamAddressError("example.com/video.mp4"))
        assertNull(streamAddressError("https://example.com/live/index.m3u8"))
        assertNull(streamAddressError("  http://192.168.1.20:8080/movie.mkv "))
        assertNull(streamAddressError("rtsp://camera.local/stream"))
    }

    @Test
    fun hlsAddressesGetTheHlsMimeType() {
        val hls = externalMediaItem("https://example.com/live/master.m3u8?token=abc", "Live")
        assertEquals(MimeTypes.APPLICATION_M3U8, hls.localConfiguration?.mimeType)
        val progressive = externalMediaItem("content://media/external/video/media/42", "Clip")
        assertNull(progressive.localConfiguration?.mimeType)
        assertEquals("Clip", progressive.mediaMetadata.title)
    }
}
