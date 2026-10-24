package me.xdan.aperture.ui.screen.live

import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.FileOpen
import androidx.compose.material.icons.rounded.LiveTv
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Close
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.SurfaceDefaults
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import me.xdan.aperture.data.live.ChannelSource
import me.xdan.aperture.data.live.LiveChannel
import me.xdan.aperture.data.live.LiveChannelRepository
import me.xdan.aperture.ui.component.expressive.ExpressiveLoadingIndicator
import me.xdan.aperture.ui.component.touch.Button
import me.xdan.aperture.ui.component.touch.OutlinedButton
import me.xdan.aperture.ui.component.touch.Surface
import me.xdan.aperture.ui.screen.open.displayName

/** Browse M3U playlists and watch live TV, the Android version of the desktop Live Channels page. */
@Composable
fun LiveTvScreen(
    viewModel: LiveTvViewModel,
    onPlayChannel: (LiveChannel, category: String) -> Unit,
    drawerFocusRequester: FocusRequester?,
    contentEntryFocusRequester: FocusRequester,
    onContentFocused: (FocusRequester) -> Unit
) {
    val ui by viewModel.ui.collectAsState()
    var showFeatured by remember { mutableStateOf(false) }
    var showAddPlaylist by remember { mutableStateOf(false) }
    val leftEdge = Modifier.then(
        if (drawerFocusRequester != null) Modifier.focusProperties { left = drawerFocusRequester } else Modifier
    )

    Column(Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(Modifier.weight(1f)) {
                Text("Live TV", style = MaterialTheme.typography.headlineSmall)
                Text(
                    "Free channels and your own M3U playlists",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            OutlinedButton(
                onClick = { showFeatured = true },
                modifier = leftEdge
                    .focusRequester(contentEntryFocusRequester)
                    .onFocusChanged { if (it.isFocused) onContentFocused(contentEntryFocusRequester) }
            ) {
                Icon(Icons.Rounded.LiveTv, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Free channels")
            }
            Button(onClick = { showAddPlaylist = true }) {
                Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Add playlist")
            }
        }

        ui.error?.let { message ->
            Row(
                Modifier.fillMaxWidth().padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f))
                OutlinedButton(onClick = viewModel::clearError) { Text("Dismiss") }
            }
        }
        Spacer(Modifier.size(12.dp))

        when {
            !ui.loaded -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                ExpressiveLoadingIndicator(color = MaterialTheme.colorScheme.primary, size = 40.dp)
            }
            ui.sources.isEmpty() -> EmptyLiveTv(
                onChooseFree = { showFeatured = true },
                onAddPlaylist = { showAddPlaylist = true }
            )
            else -> Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                PlaylistColumn(ui = ui, viewModel = viewModel, leftEdge = leftEdge)
                ChannelColumn(ui = ui, viewModel = viewModel, onPlayChannel = onPlayChannel)
            }
        }
    }

    if (showFeatured) FeaturedSourcesDialog(
        added = ui.sources.mapTo(HashSet()) { it.location },
        onDismiss = { showFeatured = false },
        onChoose = { playlist ->
            showFeatured = false
            viewModel.addFeatured(playlist)
        }
    )
    if (showAddPlaylist) AddPlaylistDialog(
        onDismiss = { showAddPlaylist = false },
        onAdd = { name, location -> if (viewModel.add(name, location)) showAddPlaylist = false }
    )
}

@Composable
private fun EmptyLiveTv(onChooseFree: () -> Unit, onAddPlaylist: () -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Rounded.LiveTv, contentDescription = null, modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.size(12.dp))
        Text("Watch free live TV", style = MaterialTheme.typography.titleLarge)
        Text(
            "Pick a free service and region, such as Pluto TV, Plex, Samsung TV Plus or Roku, or add your own M3U playlist.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.widthIn(max = 520.dp).padding(top = 6.dp, bottom = 16.dp)
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = onChooseFree) { Text("Choose free channels") }
            OutlinedButton(onClick = onAddPlaylist) { Text("Add your own playlist") }
        }
    }
}

@Composable
private fun PlaylistColumn(ui: LiveTvUiState, viewModel: LiveTvViewModel, leftEdge: Modifier) {
    Column(Modifier.width(210.dp).fillMaxHeight()) {
        SectionLabel("PLAYLISTS")
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(ui.sources, key = { it.id }) { source ->
                val selected = source.id == ui.source?.id
                SelectableRow(selected = selected, onClick = { viewModel.selectSource(source) }, modifier = leftEdge) {
                    Column(Modifier.padding(horizontal = 12.dp, vertical = 9.dp)) {
                        Text(source.name, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(
                            if (ui.loadingSourceId == source.id) "Loading…" else "${ui.channelCounts[source.id] ?: 0} channels",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.alpha(0.72f)
                        )
                    }
                }
            }
        }
        ui.source?.let { source ->
            Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SmallAction(Icons.Rounded.Refresh, "Refresh", enabled = ui.loadingSourceId == null) { viewModel.refresh(source) }
                SmallAction(Icons.Rounded.Delete, "Remove") { viewModel.remove(source) }
            }
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.ChannelColumn(
    ui: LiveTvUiState,
    viewModel: LiveTvViewModel,
    onPlayChannel: (LiveChannel, String) -> Unit
) {
    Column(Modifier.weight(1f).fillMaxHeight()) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                Modifier
                    .weight(1f)
                    .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Rounded.Search, contentDescription = null, modifier = Modifier.size(18.dp).alpha(0.72f))
                Spacer(Modifier.width(10.dp))
                BasicTextField(
                    value = ui.query,
                    onValueChange = viewModel::setQuery,
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    modifier = Modifier.weight(1f),
                    decorationBox = { field ->
                        if (ui.query.isEmpty()) Text("Search channels", color = Color.Gray, style = MaterialTheme.typography.bodyMedium)
                        field()
                    }
                )
                if (ui.query.isNotEmpty()) {
                    Icon(
                        Icons.Rounded.Close,
                        contentDescription = "Clear search",
                        modifier = Modifier.size(18.dp).pointerInput(Unit) { detectTapGestures { viewModel.setQuery("") } }
                    )
                }
            }
            Text("${ui.visibleChannels.size} channels", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        LazyRow(
            Modifier.padding(vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(horizontal = 2.dp)
        ) {
            items(ui.categories, key = { it.first }) { (name, count) ->
                SelectableRow(
                    selected = name == ui.category,
                    onClick = { viewModel.selectCategory(name) },
                    shape = CircleShape
                ) {
                    Text("$name · $count", style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp))
                }
            }
        }
        when {
            ui.loadingSourceId == ui.source?.id && ui.visibleChannels.isEmpty() ->
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    ExpressiveLoadingIndicator(color = MaterialTheme.colorScheme.primary, size = 36.dp)
                }
            ui.visibleChannels.isEmpty() -> Text(
                if (ui.query.isNotBlank()) "No channels match \"${ui.query.trim()}\"."
                else "No channels here. Refresh the playlist or choose another category.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(12.dp)
            )
            else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp), contentPadding = PaddingValues(bottom = 12.dp)) {
                items(ui.visibleChannels, key = { it.url }) { channel ->
                    ChannelRow(channel) { onPlayChannel(channel, ui.category) }
                }
            }
        }
    }
}

@Composable
private fun ChannelRow(channel: LiveChannel, onClick: () -> Unit) {
    SelectableRow(selected = false, onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(width = 56.dp, height = 36.dp).clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Rounded.LiveTv, contentDescription = null, modifier = Modifier.size(20.dp).alpha(0.6f))
                if (channel.logo.isNotBlank()) {
                    AsyncImage(
                        model = channel.logo,
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize().padding(4.dp)
                    )
                }
            }
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text(channel.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val detail = listOf(channel.number, channel.group).filter { it.isNotBlank() }.joinToString(" · ")
                if (detail.isNotEmpty()) {
                    Text(detail, style = MaterialTheme.typography.bodySmall, maxLines = 1,
                        overflow = TextOverflow.Ellipsis, modifier = Modifier.alpha(0.72f))
                }
            }
        }
    }
}

/** A focusable, tappable row or chip that is filled while [selected]. */
@Composable
private fun SelectableRow(
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(12.dp),
    content: @Composable () -> Unit
) {
    val primary = MaterialTheme.colorScheme.primary
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = ClickableSurfaceDefaults.shape(shape),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.02f, pressedScale = 0.98f),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (selected) primary.copy(alpha = 0.22f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
            contentColor = if (selected) primary else MaterialTheme.colorScheme.onSurface,
            focusedContainerColor = MaterialTheme.colorScheme.primaryContainer,
            focusedContentColor = MaterialTheme.colorScheme.onPrimaryContainer
        ),
        border = ClickableSurfaceDefaults.border(
            focusedBorder = Border(BorderStroke(2.dp, primary), shape = shape)
        )
    ) { content() }
}

@Composable
private fun SmallAction(icon: ImageVector, label: String, enabled: Boolean = true, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, enabled = enabled, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = 8.dp))
}

@Composable
private fun FeaturedSourcesDialog(
    added: Set<String>,
    onDismiss: () -> Unit,
    onChoose: (me.xdan.aperture.data.live.FeaturedPlaylist) -> Unit
) {
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { delay(120); runCatching { firstFocus.requestFocus() } }
    LiveDialog(onDismiss = onDismiss) {
        Text("Free channels", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Free ad-supported channels by service and region. They need an internet connection, and " +
                "some services only stream to their own region.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        LazyColumn(Modifier.heightIn(max = 320.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(LiveChannelRepository.featured, key = { it.file }) { playlist ->
                val isAdded = playlist.url in added
                SelectableRow(
                    selected = isAdded,
                    onClick = { if (!isAdded) onChoose(playlist) },
                    modifier = Modifier.fillMaxWidth().then(
                        if (playlist == LiveChannelRepository.featured.first()) Modifier.focusRequester(firstFocus) else Modifier
                    )
                ) {
                    Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(playlist.name, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                        if (isAdded) {
                            Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Added", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            OutlinedButton(onClick = onDismiss) { Text("Close") }
        }
    }
}

@Composable
private fun AddPlaylistDialog(onDismiss: () -> Unit, onAdd: (name: String, location: String) -> Unit) {
    val context = LocalContext.current
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    val nameFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { delay(120); runCatching { nameFocus.requestFocus() } }
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        // Keep access so Refresh can read the file again later.
        runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        onAdd(name.ifBlank { displayName(context, uri).substringBeforeLast('.') }, uri.toString())
    }

    LiveDialog(onDismiss = onDismiss) {
        Text("Add a playlist", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Enter the web address of an M3U playlist, or import an .m3u file from this device.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        DialogField(name, { name = it }, "Name (optional)", KeyboardType.Text, Modifier.focusRequester(nameFocus))
        DialogField(url, { url = it }, "https://…/playlist.m3u", KeyboardType.Uri)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End)) {
            OutlinedButton(onClick = {
                // M3U files arrive with many different types, so accept anything and let the parser decide.
                runCatching { filePicker.launch(arrayOf("*/*")) }.onFailure {
                    Toast.makeText(context, "No file picker is available on this device", Toast.LENGTH_LONG).show()
                }
            }) {
                Icon(Icons.Rounded.FileOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Import file")
            }
            OutlinedButton(onClick = onDismiss) { Text("Cancel") }
            Button(onClick = { onAdd(name, url) }, enabled = url.isNotBlank()) { Text("Add") }
        }
    }
}

@Composable
private fun DialogField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    keyboardType: KeyboardType,
    modifier: Modifier = Modifier
) {
    Box(
        Modifier.fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(16.dp))
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = ImeAction.Next),
            modifier = modifier.fillMaxWidth(),
            decorationBox = { field ->
                if (value.isEmpty()) Text(placeholder, color = Color.Gray)
                field()
            }
        )
    }
}

@Composable
private fun LiveDialog(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            Modifier.fillMaxSize()
                .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.62f))
                .pointerInput(Unit) { detectTapGestures(onTap = { onDismiss() }) },
            contentAlignment = Alignment.Center
        ) {
            Surface(
                modifier = Modifier.widthIn(max = 620.dp).padding(20.dp).pointerInput(Unit) { detectTapGestures { } },
                shape = RoundedCornerShape(28.dp),
                colors = SurfaceDefaults.colors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) { content() }
            }
        }
    }
}
