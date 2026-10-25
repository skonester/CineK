package me.xdan.aperture.ui.screen.sources

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import me.xdan.aperture.data.remoteplayback.RemoteMediaEntity
import me.xdan.aperture.data.remoteplayback.RemoteSourceOption
import me.xdan.aperture.data.sources.SourceFailureKind
import me.xdan.aperture.ui.component.touch.Button
import me.xdan.aperture.ui.component.touch.OutlinedButton

/** Reopens a saved Discover title; new lookups start from TMDB thumbnails in Discover. */
@Composable
fun RemoteSourcesScreen(
    contentId: String, onPlay: (String, String, Boolean) -> Unit,
    drawerFocusRequester: FocusRequester?, contentEntryFocusRequester: FocusRequester,
    onContentFocused: (FocusRequester) -> Unit,
    viewModel: RemoteSourcesViewModel = viewModel()
) {
    val state by viewModel.state.collectAsState()
    val history by viewModel.history.collectAsState()
    LaunchedEffect(contentId) { viewModel.openSaved(contentId) }
    DisposableEffect(viewModel) { onDispose { viewModel.cancelLookup() } }
    RemoteSourcesContent(state, history, viewModel::retry,
        { option, restart -> viewModel.select(option, restart, onPlay) }, viewModel::openSaved,
        viewModel::removeSaved, drawerFocusRequester, contentEntryFocusRequester, onContentFocused)
}

@Composable
internal fun RemoteSourcesContent(
    state: RemoteSourcesUiState, history: List<RemoteMediaEntity>, onRetry: () -> Unit,
    onSelect: (RemoteSourceOption, Boolean) -> Unit, onOpenSaved: (String) -> Unit,
    onRemoveSaved: (String) -> Unit, drawerFocusRequester: FocusRequester? = null,
    contentEntryFocusRequester: FocusRequester = remember { FocusRequester() },
    onContentFocused: (FocusRequester) -> Unit = {}
) {
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 28.dp).testTag("remote-source-list"),
        contentPadding = PaddingValues(vertical = 24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            val query = state.request?.query
            Text(state.media?.displayTitle ?: query?.title ?: "Sources", style = MaterialTheme.typography.headlineMedium)
            Text("Discover - Choose a source to watch", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item {
            RemoteSourceOptions(state, onRetry, onSelect, contentEntryFocusRequester, onContentFocused,
                Modifier.focusProperties { drawerFocusRequester?.let { left = it } })
        }
        if (history.isNotEmpty()) item { Text("Recently watched", style = MaterialTheme.typography.titleLarge) }
        items(history.filter { it.lastUpdated > 0 }.take(20), key = { "saved-${it.contentId}" }) { media ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = { onOpenSaved(media.contentId) }, modifier = Modifier.weight(1f)) {
                    Text(media.displayTitle + if (media.isCompleted) " - Watched" else "")
                }
                OutlinedButton(onClick = { onRemoveSaved(media.contentId) }) { Text("Remove") }
            }
        }
    }
}

/** Shared by the Discover title dialog and saved-title screen. No manual lookup fields. */
@Composable
internal fun RemoteSourceOptions(
    state: RemoteSourcesUiState, onRetry: () -> Unit,
    onSelect: (RemoteSourceOption, Boolean) -> Unit,
    entryFocusRequester: FocusRequester = remember { FocusRequester() },
    onContentFocused: (FocusRequester) -> Unit = {}, modifier: Modifier = Modifier
) {
    val selectedSource = remember { FocusRequester() }
    LaunchedEffect(state.request?.contentId, state.options.isNotEmpty(), state.selectedIndex) {
        if (state.options.isNotEmpty()) {
            withFrameNanos { }
            runCatching { selectedSource.requestFocus() }
        }
    }
    Column(modifier.fillMaxWidth().testTag("source-options"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Sources", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            OutlinedButton(onClick = onRetry, enabled = !state.selecting,
                modifier = Modifier.focusRequester(entryFocusRequester)) { Text("Refresh sources") }
        }
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        if (state.loading.isNotEmpty()) {
            Text("Looking for sources: ${state.loading.joinToString { it.displayName }}...")
        } else if (state.options.isEmpty() && state.request != null && state.error == null) {
            Text("No playable sources found. Try refreshing later.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        state.options.forEachIndexed { index, option ->
            val requester = if (index == state.selectedIndex) selectedSource else remember { FocusRequester() }
            Column(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("${option.provider.displayName} - ${option.stream.label}", style = MaterialTheme.typography.titleMedium)
                Text(listOfNotNull(option.stream.quality?.let { "${it}p" },
                    "${option.stream.subtitles.size} subtitle tracks".takeIf { option.stream.subtitles.isNotEmpty() }).joinToString(" - "),
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(onClick = { onSelect(option, false) }, enabled = !state.selecting,
                        modifier = Modifier.testTag("remote-source-play-$index").focusRequester(requester)
                            .onFocusChanged { if (it.isFocused) onContentFocused(requester) }) {
                        Text(state.media?.takeIf { it.canResume }?.let { "Resume at ${remoteTime(it.positionMs)}" } ?: "Play")
                    }
                    if (state.media?.canResume == true) OutlinedButton(onClick = { onSelect(option, true) }, enabled = !state.selecting) {
                        Text("Start over")
                    }
                }
            }
        }
        state.failures.forEach { failure ->
            val message = when (failure.kind) {
                SourceFailureKind.BLOCKED -> "This source is refusing requests right now."
                SourceFailureKind.UNSUPPORTED -> "This source's current player format is not supported."
                SourceFailureKind.TIMEOUT -> "This source took too long to respond."
                else -> failure.message
            }
            Text("${failure.site.displayName}: $message", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun RemoteHistoryRow(items: List<RemoteMediaEntity>, onOpen: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Recently watched", style = MaterialTheme.typography.titleLarge)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(vertical = 6.dp)) {
            items(items, key = { it.contentId }) { media ->
                OutlinedButton(onClick = { onOpen(media.contentId) }, modifier = Modifier.widthIn(max = 340.dp)) {
                    Column {
                        Text(media.displayTitle)
                        Text(when {
                            media.isCompleted -> "Watched"
                            media.canResume -> "Resume at ${remoteTime(media.positionMs)}"
                            else -> "Find sources"
                        }, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

@Composable
fun RemoteContinueRow(items: List<RemoteMediaEntity>, onOpen: (String) -> Unit, drawerFocusRequester: FocusRequester? = null) {
    if (items.isEmpty()) return
    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Continue Watching - Discover", style = MaterialTheme.typography.titleLarge)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(items, key = { it.contentId }) { media ->
                OutlinedButton(onClick = { onOpen(media.contentId) }, modifier = Modifier.widthIn(max = 340.dp)
                    .focusProperties { drawerFocusRequester?.let { left = it } }) {
                    Column { Text(media.displayTitle); Text("Resume at ${remoteTime(media.positionMs)}", style = MaterialTheme.typography.bodySmall) }
                }
            }
        }
    }
}

internal fun remoteTime(positionMs: Long): String {
    val seconds = positionMs.coerceAtLeast(0) / 1000
    return if (seconds >= 3600) "%d:%02d:%02d".format(seconds / 3600, (seconds / 60) % 60, seconds % 60)
    else "%d:%02d".format(seconds / 60, seconds % 60)
}
