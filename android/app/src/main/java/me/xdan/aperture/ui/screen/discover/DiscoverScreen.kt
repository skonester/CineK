package me.xdan.aperture.ui.screen.discover

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.Person
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.text.font.FontStyle
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
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import me.xdan.aperture.data.discover.DiscoverDetails
import me.xdan.aperture.data.discover.DiscoverEpisode
import me.xdan.aperture.data.discover.sourceRequest
import me.xdan.aperture.data.remoteplayback.RemoteSourceOption
import me.xdan.aperture.ui.screen.sources.RemoteSourcesViewModel
import me.xdan.aperture.ui.screen.sources.RemoteSourcesUiState
import me.xdan.aperture.ui.screen.sources.RemoteSourceOptions
import me.xdan.aperture.ui.screen.sources.RemoteHistoryRow
import me.xdan.aperture.data.discover.DiscoverPeriod
import me.xdan.aperture.data.discover.DiscoverSort
import me.xdan.aperture.data.discover.DiscoverTitle
import me.xdan.aperture.data.discover.DiscoverType
import me.xdan.aperture.ui.artwork.LocalHdArtwork
import me.xdan.aperture.ui.artwork.TmdbImageSize
import me.xdan.aperture.ui.component.ArtworkFallback
import me.xdan.aperture.ui.component.expressive.ExpressiveLoadingIndicator
import me.xdan.aperture.ui.component.touch.Button
import me.xdan.aperture.ui.component.touch.OutlinedButton
import me.xdan.aperture.ui.component.touch.Surface

/** TMDB thumbnails open details and automatically find sources for the selected movie/episode. */
@Composable
fun DiscoverScreen(
    viewModel: DiscoverViewModel,
    onOpenLibraryItem: (mediaId: Long, FocusRequester) -> Unit,
    onPlaySource: (String, String, Boolean) -> Unit,
    drawerFocusRequester: FocusRequester?,
    contentEntryFocusRequester: FocusRequester,
    onContentFocused: (FocusRequester) -> Unit,
    onOpenSaved: (String) -> Unit = {},
    sources: RemoteSourcesViewModel = viewModel()
) {
    val ui by viewModel.ui.collectAsState()
    val sourceState by sources.state.collectAsState()
    val history by sources.history.collectAsState()
    val request = remember(ui.selected, ui.selectedEpisode) {
        ui.selected?.let { title ->
            if (title.type == DiscoverType.MOVIES || ui.selectedEpisode != null) title.sourceRequest(ui.selectedEpisode) else null
        }
    }
    LaunchedEffect(request) {
        if (request == null) sources.clear() else sources.open(request)
    }
    DisposableEffect(sources) { onDispose { sources.cancelLookup() } }
    val library by viewModel.library.collectAsState()
    val grid = rememberLazyGridState()
    // Keep the originating thumbnail identity through a trip to the player.
    // FocusRequesters themselves belong to the current grid composition.
    var openedFromId by rememberSaveable { mutableStateOf<Int?>(null) }
    val cardRequesters = remember { mutableMapOf<Int, FocusRequester>() }
    val leftEdge = Modifier.then(
        if (drawerFocusRequester != null) Modifier.focusProperties { left = drawerFocusRequester } else Modifier
    )

    // Asks for the next page while a couple of rows are still left to scroll.
    LaunchedEffect(grid, ui.hasMore) {
        snapshotFlow { grid.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }
            .distinctUntilChanged()
            .filter { last -> ui.hasMore && last >= grid.layoutInfo.totalItemsCount - 12 }
            .collect { viewModel.loadMore() }
    }
    // A new filter starts the list again from the top. Coming back to the page keeps
    // the filter, so the scroll position survives a trip to the library or player.
    var shownFilter by remember { mutableStateOf(ui.filter) }
    LaunchedEffect(ui.filter) {
        if (ui.filter != shownFilter) {
            shownFilter = ui.filter
            grid.scrollToItem(0)
        }
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 32.dp, vertical = 20.dp)) {
        Text("Discover", style = MaterialTheme.typography.headlineLarge)
        Text(
            "Choose a movie or show to find sources. Select an episode for shows.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (!ui.available) {
            DiscoverMessage(
                "Discover needs TMDB",
                "TMDB is unavailable in this build.",
                modifier = leftEdge.focusRequester(contentEntryFocusRequester)
                    .onFocusChanged { if (it.isFocused) onContentFocused(contentEntryFocusRequester) }
            )
            return
        }

        Spacer(Modifier.height(12.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(vertical = 4.dp)) {
            items(DiscoverType.entries, key = { "type-$it" }) { type ->
                FilterChip(
                    label = type.label,
                    selected = ui.filter.type == type,
                    onClick = { viewModel.setType(type) },
                    modifier = if (type == DiscoverType.entries.first()) {
                        leftEdge.focusRequester(contentEntryFocusRequester)
                            .onFocusChanged { if (it.isFocused) onContentFocused(contentEntryFocusRequester) }
                    } else Modifier
                )
            }
            item(key = "divider") { Spacer(Modifier.width(12.dp)) }
            items(DiscoverSort.entries, key = { "sort-$it" }) { sort ->
                FilterChip(sort.label, ui.filter.sort == sort, { viewModel.setSort(sort) })
            }
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(vertical = 4.dp)) {
            items(DiscoverPeriod.entries, key = { it.name }) { period ->
                FilterChip(period.label, ui.filter.period == period, { viewModel.setPeriod(period) },
                    modifier = if (period == DiscoverPeriod.entries.first()) leftEdge else Modifier)
            }
        }
        if (ui.genres.isNotEmpty()) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(vertical = 4.dp)) {
                item(key = "any") { FilterChip("Any genre", ui.filter.genreId == null, { viewModel.setGenre(null) }, leftEdge) }
                items(ui.genres, key = { it.id }) { genre ->
                    FilterChip(genre.name, ui.filter.genreId == genre.id, { viewModel.setGenre(genre.id) })
                }
            }
        }
        Spacer(Modifier.height(8.dp))

        when {
            ui.titles.isEmpty() && ui.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                ExpressiveLoadingIndicator(color = MaterialTheme.colorScheme.primary, size = 40.dp)
            }
            ui.titles.isEmpty() && ui.error != null -> DiscoverMessage("Couldn't load Discover", ui.error.orEmpty(),
                action = "Try again", onAction = viewModel::retry)
            ui.titles.isEmpty() -> DiscoverMessage("Nothing here", "No titles match these filters. Try another genre or decade.")
            else -> LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 132.dp),
                state = grid,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(top = 12.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                val recent = history.filter { it.lastUpdated > 0 }.take(20)
                if (recent.isNotEmpty()) item(key = "recent", span = { GridItemSpan(maxLineSpan) }) {
                    RemoteHistoryRow(recent, onOpenSaved)
                }
                items(ui.titles, key = { "${it.type}-${it.tmdbId}" }) { title ->
                    val requester = cardRequesters.getOrPut(title.tmdbId) { FocusRequester() }
                    DiscoverCard(
                        title = title,
                        inLibrary = (title.type to title.tmdbId) in library,
                        onClick = { openedFromId = title.tmdbId; viewModel.open(title) },
                        modifier = Modifier.focusRequester(requester)
                            .onFocusChanged { if (it.isFocused) onContentFocused(requester) }
                    )
                }
                item(key = "footer", span = { GridItemSpan(maxLineSpan) }) {
                    Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                        when {
                            ui.loading -> ExpressiveLoadingIndicator(color = MaterialTheme.colorScheme.primary, size = 32.dp)
                            ui.error != null -> Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(ui.error.orEmpty(), color = MaterialTheme.colorScheme.error)
                                Spacer(Modifier.width(12.dp))
                                OutlinedButton(onClick = viewModel::retry) { Text("Try again") }
                            }
                            !ui.hasMore -> Text("That's everything for these filters.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }

    ui.selected?.let { selected ->
        val mediaId = library[selected.type to selected.tmdbId]
        DiscoverDetailsDialog(
            ui = ui,
            sourceState = if (sourceState.request == request) sourceState else RemoteSourcesUiState(),
            onSelectSeason = viewModel::selectSeason,
            onSelectEpisode = viewModel::selectEpisode,
            onRetrySources = sources::retry,
            onSelectSource = { option, restart -> sources.select(option, restart, onPlaySource) },
            libraryMediaId = mediaId,
            library = library,
            onOpenLibrary = { id ->
                viewModel.closeDetails()
                onOpenLibraryItem(id, cardRequesters[openedFromId] ?: contentEntryFocusRequester)
            },
            onOpenRelated = viewModel::open,
            onRetry = { viewModel.open(selected) },
            onDismiss = {
                viewModel.closeDetails()
                runCatching { (cardRequesters[openedFromId] ?: contentEntryFocusRequester).requestFocus() }
            }
        )
    }
}

@Composable
private fun FilterChip(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val primary = MaterialTheme.colorScheme.primary
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = ClickableSurfaceDefaults.shape(CircleShape),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.04f),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (selected) primary.copy(alpha = 0.22f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
            contentColor = if (selected) primary else MaterialTheme.colorScheme.onSurface,
            focusedContainerColor = MaterialTheme.colorScheme.primaryContainer,
            focusedContentColor = MaterialTheme.colorScheme.onPrimaryContainer
        ),
        border = ClickableSurfaceDefaults.border(focusedBorder = Border(BorderStroke(2.dp, primary), shape = CircleShape))
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp))
    }
}

@Composable
private fun DiscoverCard(title: DiscoverTitle, inLibrary: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(12.dp)
    Column(modifier.onFocusChanged { focused = it.hasFocus }) {
        Surface(
            onClick = onClick,
            modifier = Modifier.fillMaxWidth().aspectRatio(2f / 3f),
            shape = ClickableSurfaceDefaults.shape(shape),
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.06f),
            border = ClickableSurfaceDefaults.border(
                focusedBorder = Border(BorderStroke(2.dp, MaterialTheme.colorScheme.primary), shape = shape)
            )
        ) {
            Box(Modifier.fillMaxSize()) {
                if (title.posterPath.isNullOrBlank()) {
                    ArtworkFallback(title = title.title, isFocused = focused)
                } else {
                    AsyncImage(
                        model = TmdbImageSize.POSTER_SMALL.url(title.posterPath, LocalHdArtwork.current),
                        contentDescription = title.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                if (inLibrary) LibraryBadge(Modifier.align(Alignment.TopStart).padding(6.dp))
            }
        }
        Text(title.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp))
        Text(listOfNotNull(title.year?.toString(), title.rating?.takeIf { it > 0 }?.let { "★ %.1f".format(it) }).joinToString(" · "),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
    }
}

@Composable
private fun LibraryBadge(modifier: Modifier = Modifier) {
    Row(
        modifier.background(Color.Black.copy(alpha = 0.72f), RoundedCornerShape(8.dp)).padding(horizontal = 7.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Rounded.CheckCircle, contentDescription = null, modifier = Modifier.size(12.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(4.dp))
        Text("In library", style = MaterialTheme.typography.labelSmall, color = Color.White)
    }
}

@Composable
private fun DiscoverMessage(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    action: String? = null,
    onAction: () -> Unit = {}
) {
    // Without an action the message itself takes focus, so the remote has somewhere to land.
    Column(
        Modifier.fillMaxSize().padding(top = 48.dp).then(if (action == null) modifier.focusable() else Modifier),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(Icons.Rounded.Explore, contentDescription = null, modifier = Modifier.size(44.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(10.dp))
        Text(title, style = MaterialTheme.typography.titleLarge)
        Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.widthIn(max = 520.dp).padding(top = 6.dp, bottom = 14.dp))
        if (action != null) Button(onClick = onAction, modifier = modifier) { Text(action) }
    }
}

/** Details, episode selection and source lookup all belong to the selected TMDB title. */
@Composable
internal fun DiscoverDetailsDialog(
    ui: DiscoverUiState,
    sourceState: RemoteSourcesUiState,
    onSelectSeason: (Int) -> Unit,
    onSelectEpisode: (DiscoverEpisode) -> Unit,
    onRetrySources: () -> Unit,
    onSelectSource: (RemoteSourceOption, Boolean) -> Unit,
    libraryMediaId: Long?,
    library: Map<Pair<DiscoverType, Int>, Long>,
    onOpenLibrary: (Long) -> Unit,
    onOpenRelated: (DiscoverTitle) -> Unit,
    onRetry: () -> Unit,
    onDismiss: () -> Unit
) {
    val title = ui.selected ?: return
    val details = ui.details
    val error = ui.detailsError
    val firstFocus = remember(title) { FocusRequester() }
    LaunchedEffect(title) { withFrameNanos { }; runCatching { firstFocus.requestFocus() } }
    val hd = LocalHdArtwork.current
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            Modifier.fillMaxSize()
                .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.7f))
                .pointerInput(Unit) { detectTapGestures(onTap = { onDismiss() }) },
            contentAlignment = Alignment.Center
        ) {
            androidx.tv.material3.Surface(
                modifier = Modifier.testTag("discover-title-dialog").widthIn(max = 920.dp).heightIn(max = 620.dp).padding(20.dp)
                    .pointerInput(Unit) { detectTapGestures { } },
                shape = RoundedCornerShape(28.dp),
                colors = SurfaceDefaults.colors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(Modifier.verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                        Box(Modifier.width(150.dp).aspectRatio(2f / 3f).clip(RoundedCornerShape(14.dp))) {
                            val poster = (details?.title ?: title).posterPath
                            if (poster.isNullOrBlank()) ArtworkFallback(title.title, isFocused = false)
                            else AsyncImage(model = TmdbImageSize.POSTER_SMALL.url(poster, hd), contentDescription = null,
                                contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                        }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(title.title, style = MaterialTheme.typography.headlineSmall)
                            val meta = listOfNotNull(
                                title.year?.toString(),
                                details?.runtimeMinutes?.let(::runtimeLabel),
                                details?.seasons?.let { if (it == 1) "1 season" else "$it seasons" },
                                title.rating?.takeIf { it > 0 }?.let { "★ %.1f".format(it) }
                            ) + details?.genres.orEmpty().take(3)
                            Text(meta.joinToString(" · "), style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            details?.tagline?.let {
                                Text(it, style = MaterialTheme.typography.bodyMedium.copy(fontStyle = FontStyle.Italic))
                            }
                            Text((details?.title?.overview ?: title.overview).orEmpty().ifBlank { "No description on TMDB." },
                                style = MaterialTheme.typography.bodyMedium, maxLines = 7, overflow = TextOverflow.Ellipsis)
                            Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically) {
                                if (libraryMediaId != null) {
                                    Button(onClick = { onOpenLibrary(libraryMediaId) }, modifier = Modifier.focusRequester(firstFocus)) {
                                        Text("Open in library")
                                    }
                                    OutlinedButton(onClick = onDismiss) { Text("Close") }
                                } else {
                                    OutlinedButton(onClick = onDismiss, modifier = Modifier.focusRequester(firstFocus)) { Text("Close") }
                                }
                            }
                        }
                    }

                    if (title.type == DiscoverType.SHOWS) {
                        DiscoverEpisodeChoices(ui, onSelectSeason, onSelectEpisode)
                    }
                    if (title.type == DiscoverType.MOVIES || ui.selectedEpisode != null) {
                        ui.selectedEpisode?.let { episode ->
                            Text("Season ${episode.season}, episode ${episode.number}: ${episode.name}",
                                style = MaterialTheme.typography.titleMedium)
                        }
                        RemoteSourceOptions(sourceState, onRetrySources, onSelectSource)
                    }

                    when {
                        details == null && error != null -> Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(error, color = MaterialTheme.colorScheme.error, modifier = Modifier.weight(1f))
                            OutlinedButton(onClick = onRetry) { Text("Try again") }
                        }
                        details == null -> Box(Modifier.fillMaxWidth().padding(12.dp), contentAlignment = Alignment.Center) {
                            ExpressiveLoadingIndicator(color = MaterialTheme.colorScheme.primary, size = 32.dp)
                        }
                        else -> {
                            if (details.cast.isNotEmpty()) {
                                SectionTitle("Cast")
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                                    items(details.cast, key = { it.name + it.character }) { member ->
                                        Column(Modifier.width(92.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                            Box(Modifier.size(72.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant),
                                                contentAlignment = Alignment.Center) {
                                                Icon(Icons.Rounded.Person, contentDescription = null, modifier = Modifier.alpha(0.5f))
                                                member.profilePath?.let {
                                                    AsyncImage(model = TmdbImageSize.PROFILE.url(it, hd), contentDescription = null,
                                                        contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                                                }
                                            }
                                            Text(member.name, style = MaterialTheme.typography.labelMedium, maxLines = 2,
                                                overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
                                            member.character?.takeIf { it.isNotBlank() }?.let {
                                                Text(it, style = MaterialTheme.typography.labelSmall, maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                        }
                                    }
                                }
                            }
                            if (details.related.isNotEmpty()) {
                                SectionTitle("More like this")
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                                    items(details.related, key = { it.tmdbId }) { related ->
                                        DiscoverCard(
                                            title = related,
                                            inLibrary = (related.type to related.tmdbId) in library,
                                            onClick = { onOpenRelated(related) },
                                            modifier = Modifier.width(112.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun DiscoverEpisodeChoices(
    ui: DiscoverUiState, onSelectSeason: (Int) -> Unit, onSelectEpisode: (DiscoverEpisode) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Choose an episode", style = MaterialTheme.typography.titleMedium)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(ui.details?.seasonList.orEmpty(), key = { it.number }) { season ->
                FilterChip("Season ${season.number}", ui.selectedSeason == season.number, { onSelectSeason(season.number) },
                    Modifier.testTag("discover-season-${season.number}"))
            }
        }
        when {
            ui.seasonLoading -> ExpressiveLoadingIndicator(color = MaterialTheme.colorScheme.primary, size = 24.dp)
            ui.seasonError != null -> {
                Text(ui.seasonError, color = MaterialTheme.colorScheme.error)
                OutlinedButton(onClick = { ui.selectedSeason?.let(onSelectSeason) }) { Text("Retry episodes") }
            }
            ui.episodes.isNotEmpty() -> LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(ui.episodes, key = { it.number }) { episode ->
                    FilterChip("${episode.number}. ${episode.name.ifBlank { "Episode ${episode.number}" }}",
                        ui.selectedEpisode == episode, { onSelectEpisode(episode) },
                        Modifier.widthIn(max = 320.dp).testTag("discover-episode-${episode.season}-${episode.number}"))
                }
            }
            ui.details != null -> Text("No episodes available for this season.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium)
}

internal fun runtimeLabel(minutes: Int): String = when {
    minutes < 60 -> "${minutes}m"
    minutes % 60 == 0 -> "${minutes / 60}h"
    else -> "${minutes / 60}h ${minutes % 60}m"
}
