package me.xdan.aperture.ui.screen.player

import android.app.Application
import android.content.Intent
import android.content.pm.PackageInfo
import android.view.MotionEvent
import androidx.test.core.app.ApplicationProvider
import me.xdan.aperture.domain.model.LivePlayerApp
import me.xdan.aperture.domain.model.StreamPictureSize
import org.robolectric.Shadows.shadowOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class StreamPlayerControlsTest {
    private val width = 1000
    private val height = 1000
    private val values = mutableMapOf(SwipeTarget.BRIGHTNESS to 0.5f, SwipeTarget.VOLUME to 0.2f)
    private val adjustments = mutableListOf<Pair<SwipeTarget, Float>>()
    private val swipe = SwipeAdjuster(touchSlop = 10, currentValue = { values.getValue(it) }) { target, value ->
        adjustments += target to value
    }

    private fun touch(action: Int, x: Float, y: Float): Boolean {
        val event = MotionEvent.obtain(0, 0, action, x, y, 0)
        return swipe.onTouch(event, width, height).also { event.recycle() }
    }

    @Test
    fun verticalSwipesAdjustBrightnessOnTheLeftAndVolumeOnTheRight() {
        assertFalse(touch(MotionEvent.ACTION_DOWN, 200f, 500f))
        // Under the slop it is still a possible tap, so the controller keeps the gesture.
        assertFalse(touch(MotionEvent.ACTION_MOVE, 200f, 490f))
        assertTrue(touch(MotionEvent.ACTION_MOVE, 200f, 450f))
        assertEquals(SwipeTarget.BRIGHTNESS, swipe.target)
        // 70% of the height covers the whole range, so 140px up adds 0.2.
        assertTrue(touch(MotionEvent.ACTION_MOVE, 200f, 310f))
        assertEquals(SwipeTarget.BRIGHTNESS, adjustments.last().first)
        assertEquals(0.7f, adjustments.last().second, 0.001f)
        assertTrue(touch(MotionEvent.ACTION_UP, 200f, 310f))
        assertNull(swipe.target)

        touch(MotionEvent.ACTION_DOWN, 800f, 400f)
        touch(MotionEvent.ACTION_MOVE, 800f, 450f)
        touch(MotionEvent.ACTION_MOVE, 800f, 1000f)
        assertEquals(SwipeTarget.VOLUME to 0f, adjustments.last())
    }

    @Test
    fun tapsSideSwipesAndEdgeSwipesAreLeftToTheController() {
        // A horizontal drag is a seek or nothing, not an adjustment.
        touch(MotionEvent.ACTION_DOWN, 500f, 500f)
        assertFalse(touch(MotionEvent.ACTION_MOVE, 700f, 520f))
        assertFalse(touch(MotionEvent.ACTION_UP, 700f, 520f))
        // The bottom quarter holds the seek bar; the top edge is the system's.
        touch(MotionEvent.ACTION_DOWN, 200f, 900f)
        assertFalse(touch(MotionEvent.ACTION_MOVE, 200f, 600f))
        touch(MotionEvent.ACTION_DOWN, 200f, 50f)
        assertFalse(touch(MotionEvent.ACTION_MOVE, 200f, 400f))
        // Turned off in Settings.
        swipe.enabled = false
        touch(MotionEvent.ACTION_DOWN, 200f, 500f)
        assertFalse(touch(MotionEvent.ACTION_MOVE, 200f, 300f))
        assertTrue(adjustments.isEmpty())
    }

    @Test
    fun handedOffStreamsCarryTitleTypeAndHeaders() {
        val channel = PlayableItem(
            "https://tv.example.com/live/news", "News", live = true,
            headers = linkedMapOf("User-Agent" to "VLC/3.0", "Referer" to "https://r.example.com/")
        )
        val intent = streamViewIntent(channel, "com.mxtech.videoplayer.ad")
        assertEquals(Intent.ACTION_VIEW, intent.action)
        assertEquals("com.mxtech.videoplayer.ad", intent.`package`)
        assertEquals("application/x-mpegURL", intent.type)
        assertEquals("News", intent.getStringExtra("title"))
        // MX Player's headers extra is a flat array of name, value pairs.
        assertEquals(
            listOf("User-Agent", "VLC/3.0", "Referer", "https://r.example.com/"),
            intent.getStringArrayExtra("headers")?.toList()
        )

        val file = streamViewIntent(PlayableItem("https://example.com/movie.mp4", "Movie"))
        assertEquals("video/*", file.type)
        assertNull(file.`package`)
        assertFalse(file.hasExtra("headers"))
    }

    @Test
    fun findsWhichPlayerAppIsInstalled() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        assertNull(installedPackage(context, LivePlayerApp.MX_PLAYER))
        shadowOf(context.packageManager).installPackage(PackageInfo().apply { packageName = "com.mxtech.videoplayer.ad" })
        assertEquals("com.mxtech.videoplayer.ad", installedPackage(context, LivePlayerApp.MX_PLAYER))
        assertNull(installedPackage(context, LivePlayerApp.VLC))
        assertFalse(openInApp(context, PlayableItem("https://tv.example.com/a.m3u8", "A"), LivePlayerApp.VLC))
        assertEquals(LivePlayerApp.CINEK, LivePlayerApp.fromPreference(null))
        assertEquals(LivePlayerApp.VLC, LivePlayerApp.fromPreference("vlc"))
        assertEquals(LivePlayerApp.CINEK, LivePlayerApp.VLC.next)
    }

    @Test
    fun addedSubtitleFilesAreCopiedAsUtf8() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val picked = java.io.File(context.filesDir, "Film.fr.srt").apply {
            writeBytes("1\n00:00:01,000 --> 00:00:02,000\nDéjà vu\n".toByteArray(java.nio.charset.Charset.forName("windows-1252")))
        }
        val subtitle = copyAddedSubtitle(context, android.net.Uri.fromFile(picked), "auto")!!
        assertEquals("application/x-subrip", subtitle.mimeType)
        // On a Windows test host the file Uri keeps backslashes, so only check the end.
        assertTrue(subtitle.label.toString().endsWith("Film.fr"))
        assertEquals(androidx.media3.common.C.SELECTION_FLAG_DEFAULT, subtitle.selectionFlags)
        val copy = java.io.File(subtitle.uri.path!!)
        assertTrue(copy.path.startsWith(context.cacheDir.path))
        assertTrue(copy.readText(Charsets.UTF_8).contains("Déjà vu"))

        val notSubtitle = java.io.File(context.filesDir, "notes.txt").apply { writeText("hello") }
        assertNull(copyAddedSubtitle(context, android.net.Uri.fromFile(notSubtitle), "auto"))
        assertEquals("text/x-ssa", subtitleMimeFor("Show.S01E01.ASS"))
        assertNull(subtitleMimeFor("movie.mkv"))
    }

    @Test
    fun liveReconnectsAreLimitedPerWindow() {
        val budget = ReconnectBudget(limit = 3, windowMs = 120_000)
        assertTrue(budget.tryAcquire(0))
        assertTrue(budget.tryAcquire(10_000))
        assertTrue(budget.tryAcquire(20_000))
        // A fourth dropout inside two minutes means the channel is really down.
        assertFalse(budget.tryAcquire(30_000))
        // Occasional dropouts over a long watch keep reconnecting.
        assertTrue(budget.tryAcquire(125_000))
        budget.reset()
        assertTrue(budget.tryAcquire(126_000))
    }

    @Test
    fun pictureSizeCyclesAndReadsSavedValues() {
        assertEquals(StreamPictureSize.STRETCH, StreamPictureSize.FIT.next)
        assertEquals(StreamPictureSize.FIT, StreamPictureSize.ZOOM.next)
        assertEquals(StreamPictureSize.ZOOM, StreamPictureSize.fromPreference("zoom"))
        assertEquals(StreamPictureSize.FIT, StreamPictureSize.fromPreference(null))
        assertEquals(StreamPictureSize.FIT, StreamPictureSize.fromPreference("unknown"))
    }
}
