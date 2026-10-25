package me.xdan.aperture.ui.screen.sources

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import me.xdan.aperture.ui.component.touch.Button
import me.xdan.aperture.ui.component.touch.OutlinedButton
import me.xdan.aperture.ui.screen.player.MediaQueuePlayer
import me.xdan.aperture.ui.screen.player.PlayerPlaceholder
import me.xdan.aperture.ui.screen.player.StreamPlayerViewModel
import me.xdan.aperture.ui.screen.player.toPlayableItem

@Composable
fun RemotePlayerScreen(
    contentId: String, sessionId: String?, fromBeginning: Boolean, onBack: () -> Unit,
    viewModel: RemotePlayerViewModel = viewModel(), settingsViewModel: StreamPlayerViewModel = viewModel()
) {
    val state by viewModel.state.collectAsState()
    val saveError by viewModel.saveError.collectAsState()
    val settings by settingsViewModel.settings.collectAsState()
    LaunchedEffect(contentId, sessionId, fromBeginning) { viewModel.load(contentId, sessionId, fromBeginning) }
    val option = state.option
    val media = state.media
    if (!viewModel.isLoadedFor(contentId, sessionId, fromBeginning) || state.loading || settings == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Preparing source…") }; return
    }
    if (option == null || media == null) {
        Column(Modifier.fillMaxSize().padding(32.dp), verticalArrangement = Arrangement.Center) {
            Text(state.error ?: "This source is unavailable.")
            Button(onClick = onBack) { Text("Choose source") }
            OutlinedButton(onClick = viewModel::refresh) { Text("Try again") }
        }
        return
    }
    val token = state.playbackToken
    val items = remember(option, state.revision) { listOf(option.stream.toPlayableItem(media.displayTitle)) }
    Box(Modifier.fillMaxSize()) {
        key(token) {
            MediaQueuePlayer(items, 0, onBack, settings = requireNotNull(settings),
                onCyclePictureSize = settingsViewModel::cyclePictureSize,
                initialPositionMs = state.startPositionMs,
                onProgress = { snapshot -> viewModel.record(contentId, token, snapshot) },
                onChooseSource = onBack, onRefreshSource = viewModel::refresh)
        }
        saveError?.let { Text(it, modifier = Modifier.align(Alignment.BottomCenter).padding(20.dp), color = MaterialTheme.colorScheme.error) }
    }
}
