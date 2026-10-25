package me.xdan.aperture.ui.screen.player

import androidx.media3.common.Player
import me.xdan.aperture.data.remoteplayback.StreamPlaybackSnapshot

/** Captures the timeline before teardown and on seeks, pause and completion as well as periodic ticks. */
internal class StreamProgressReporter(private val player: Player, private val report: (StreamPlaybackSnapshot) -> Unit) : Player.Listener {
    private var started = false
    override fun onPlaybackStateChanged(playbackState: Int) {
        if (playbackState == Player.STATE_READY) started = true
        if (playbackState == Player.STATE_READY || playbackState == Player.STATE_ENDED) save()
    }
    override fun onIsPlayingChanged(isPlaying: Boolean) { if (!isPlaying) save() }
    override fun onPositionDiscontinuity(oldPosition: Player.PositionInfo, newPosition: Player.PositionInfo, reason: Int) { save() }
    fun save() {
        if (started) report(StreamPlaybackSnapshot(player.currentPosition.coerceAtLeast(0), player.duration.coerceAtLeast(0),
            ended = player.playbackState == Player.STATE_ENDED, hasStarted = true))
    }
}
