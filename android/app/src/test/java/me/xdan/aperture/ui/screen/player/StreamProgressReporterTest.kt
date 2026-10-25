package me.xdan.aperture.ui.screen.player

import androidx.media3.common.Player
import me.xdan.aperture.data.remoteplayback.StreamPlaybackSnapshot
import org.junit.Assert.*
import org.junit.Test
import java.lang.reflect.Proxy

class StreamProgressReporterTest {
    @Test fun failedStartsDoNotReportButPausePeriodicAndCompletionCaptureTheCurrentTimeline() {
        var position = 0L
        var state = Player.STATE_BUFFERING
        val reports = mutableListOf<StreamPlaybackSnapshot>()
        val player = Proxy.newProxyInstance(Player::class.java.classLoader, arrayOf(Player::class.java)) { _, method, _ ->
            when (method.name) {
                "getCurrentPosition" -> position
                "getDuration" -> 100_000L
                "getPlaybackState" -> state
                else -> null
            }
        } as Player
        val reporter = StreamProgressReporter(player, reports::add)
        reporter.save(); reporter.onIsPlayingChanged(false)
        assertTrue(reports.isEmpty())
        position = 20_000; state = Player.STATE_READY; reporter.onPlaybackStateChanged(state)
        position = 25_000; reporter.save()
        position = 30_000; reporter.onIsPlayingChanged(false)
        position = 100_000; state = Player.STATE_ENDED; reporter.onPlaybackStateChanged(state)
        assertEquals(listOf(20_000L, 25_000L, 30_000L, 100_000L), reports.map { it.positionMs })
        assertTrue(reports.all { it.hasStarted })
        assertTrue(reports.last().ended)
    }
}
