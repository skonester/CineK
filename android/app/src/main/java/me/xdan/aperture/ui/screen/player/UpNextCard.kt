package me.xdan.aperture.ui.screen.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import me.xdan.aperture.ui.component.touch.Button
import me.xdan.aperture.ui.component.touch.OutlinedButton

/**
 * Names the next episode near the end of this one. On a remote, OK plays it now and
 * Back dismisses the card; the player handles those keys, so the card never takes focus
 * away from the video.
 */
@Composable
fun UpNextCard(upNext: UpNext, onPlayNow: () -> Unit, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    val episode = upNext.episode
    val seconds = (upNext.remainingMs + 999) / 1_000
    val countdown = "%d:%02d".format(seconds / 60, seconds % 60)
    Column(
        modifier
            .widthIn(max = 380.dp)
            .background(Color.Black.copy(alpha = 0.78f), RoundedCornerShape(16.dp))
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            if (upNext.autoPlay) "Up next in $countdown" else "Up next",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary
        )
        Text(episodeLabel(episode.seasonNumber, episode.episodeNumber, episode.episodeTitle),
            style = MaterialTheme.typography.titleMedium, color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text("OK to play now · Back to dismiss", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.7f))
        Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(onClick = onPlayNow) { Text("Play now") }
            OutlinedButton(onClick = onDismiss) { Text("Dismiss") }
        }
    }
}

/** "S1 · E2 · Title", leaving out whatever is unknown. */
internal fun episodeLabel(season: Int?, episode: Int?, title: String?): String =
    listOfNotNull(season?.let { "S$it" }, episode?.let { "E$it" }, title?.takeIf { it.isNotBlank() })
        .joinToString(" · ")
        .ifEmpty { "Next episode" }
