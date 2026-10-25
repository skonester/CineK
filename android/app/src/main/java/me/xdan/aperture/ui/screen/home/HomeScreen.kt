package me.xdan.aperture.ui.screen.home

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowForward
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.tv.material3.*
import me.xdan.aperture.ui.component.touch.Surface
import me.xdan.aperture.ui.component.touch.Button
import me.xdan.aperture.ui.component.touch.OutlinedButton
import me.xdan.aperture.ui.component.LocalLibraryAccess
import me.xdan.aperture.ui.component.actionLabel
import me.xdan.aperture.ui.component.guidance
import me.xdan.aperture.ui.screen.open.LocalMediaOpener
import coil.compose.AsyncImage
import coil.request.ImageRequest
import kotlinx.coroutines.delay
import me.xdan.aperture.data.local.entity.MediaEntity
import me.xdan.aperture.data.remoteplayback.RemoteMediaEntity
import me.xdan.aperture.ui.screen.sources.RemoteContinueRow
import me.xdan.aperture.domain.repository.LibraryPreparationStage
import me.xdan.aperture.ui.component.MediaCard
import me.xdan.aperture.ui.component.ArtworkFallback
import me.xdan.aperture.ui.component.rememberFocusGlow
import me.xdan.aperture.ui.theme.ApertureTheme
import me.xdan.aperture.ui.theme.HeroGradientEnd
import me.xdan.aperture.ui.theme.HeroGradientStart
import me.xdan.aperture.ui.navigation.Destination
import me.xdan.aperture.util.BackdropImageUsage
import me.xdan.aperture.util.backdropImageSpec
import me.xdan.aperture.util.backdropImageUrl

@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onMediaClick: (Long, FocusRequester, Boolean) -> Unit,
    onMediaLongClick: (MediaEntity, FocusRequester, Boolean, Boolean) -> Unit,
    drawerFocusRequester: FocusRequester?,
    contentEntryFocusRequester: FocusRequester,
    restoreFocusKey: String?,
    onFocusKeyChanged: (String) -> Unit,
    onContentFocused: (FocusRequester) -> Unit,
    onActiveMediaChanged: (Long) -> Unit = {},
    onOpenLibrary: (Destination) -> Unit = {},
    onRemoteMediaClick: (String) -> Unit = {}
) {
    val state by viewModel.homeState.collectAsState()
    val roundedSpotlight by viewModel.roundedSpotlight.collectAsState()
    val remoteContinue by viewModel.remoteContinue.collectAsState()
    val preparation by viewModel.libraryPreparation.collectAsState()
    val isScanning = preparation.stage == LibraryPreparationStage.DISCOVERING ||
        preparation.stage == LibraryPreparationStage.MATCHING

    LaunchedEffect(state) {
        if (state == HomeState.Empty) {
            delay(150)
            runCatching { contentEntryFocusRequester.requestFocus() }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        when (val s = state) {
            HomeState.Loading -> {
                Text("Scanning library...", modifier = Modifier.align(Alignment.Center))
            }
            HomeState.Empty -> {
                Column(
                    modifier = Modifier.align(Alignment.Center).verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    val libraryAccess = LocalLibraryAccess.current
                    val access = libraryAccess.access
                    val opener = LocalMediaOpener.current
                    RemoteContinueRow(remoteContinue, onRemoteMediaClick, drawerFocusRequester)
                    Text(
                        when {
                            isScanning -> "Scanning your library..."
                            !access.hasAny -> "CineK can't see your videos and music yet."
                            else -> "No videos in your CineK library yet."
                        },
                        style = MaterialTheme.typography.headlineSmall
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        libraryAccess.guidance() ?: if (!access.hasAny) {
                            "Allow access so CineK can find them, or open a file to play it now."
                        } else {
                            "Add a media folder, scan again, or open a file to play it now."
                        },
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(
                            onClick = {
                                if (access.isComplete) onOpenLibrary(Destination.Settings) else libraryAccess.request()
                            },
                            modifier = Modifier
                                .focusRequester(contentEntryFocusRequester)
                                .onFocusChanged { if (it.isFocused) onContentFocused(contentEntryFocusRequester) },
                            colors = if (ApertureTheme.isMaterialTv) {
                                ButtonDefaults.colors()
                            } else {
                                ButtonDefaults.colors(
                                    containerColor = ApertureTheme.colorScheme.primary.copy(alpha = 0.72f),
                                    contentColor = ApertureTheme.colorScheme.onPrimary,
                                    focusedContainerColor = ApertureTheme.colorScheme.primary,
                                    focusedContentColor = ApertureTheme.colorScheme.onPrimary,
                                    pressedContainerColor = ApertureTheme.colorScheme.primary,
                                    pressedContentColor = ApertureTheme.colorScheme.onPrimary
                                )
                            }
                        ) { Text(if (access.isComplete) "Library settings" else libraryAccess.actionLabel()) }
                        OutlinedButton(onClick = opener::openFile) { Text("Open a file") }
                        OutlinedButton(onClick = { onOpenLibrary(Destination.Discover) }) { Text("Discover movies and shows") }
                        OutlinedButton(
                            onClick = viewModel::rescan,
                            enabled = !isScanning
                        ) { Text("Scan again") }
                    }
                }
            }
            is HomeState.Success -> {
                HomeContent(
                    state = s,
                    onMediaClick = onMediaClick,
                    onMediaLongClick = onMediaLongClick,
                    drawerFocusRequester = drawerFocusRequester,
                    contentEntryFocusRequester = contentEntryFocusRequester,
                    restoreFocusKey = restoreFocusKey,
                    onFocusKeyChanged = onFocusKeyChanged,
                    onContentFocused = onContentFocused,
                    onActiveMediaChanged = onActiveMediaChanged,
                    onOpenLibrary = onOpenLibrary,
                    roundedSpotlight = roundedSpotlight,
                    remoteContinue = remoteContinue,
                    onRemoteMediaClick = onRemoteMediaClick
                )
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun HomeContent(
    state: HomeState.Success,
    onMediaClick: (Long, FocusRequester, Boolean) -> Unit,
    onMediaLongClick: (MediaEntity, FocusRequester, Boolean, Boolean) -> Unit,
    drawerFocusRequester: FocusRequester?,
    contentEntryFocusRequester: FocusRequester,
    restoreFocusKey: String?,
    onFocusKeyChanged: (String) -> Unit,
    onContentFocused: (FocusRequester) -> Unit,
    onActiveMediaChanged: (Long) -> Unit,
    onOpenLibrary: (Destination) -> Unit,
    roundedSpotlight: Boolean,
    remoteContinue: List<RemoteMediaEntity>,
    onRemoteMediaClick: (String) -> Unit
) {
    val listState = rememberLazyListState()
    val refreshAlpha = remember { Animatable(1f) }
    val resolvedRestoreFocusKey = restoreFocusKey.takeIf { key ->
        key == HOME_SPOTLIGHT_FOCUS_KEY || state.rows.any { row ->
            row.items.any { media -> key == "row:${row.title}:${media.id}" }
        }
    }
    // The entry requester must stay attached to the item Home was entered on.
    // Moving it to every newly focused card mutates the focus tree mid-scroll and
    // makes the containing LazyColumn perform small vertical corrections.
    val entryFocusKey = remember(state.suggestionGeneration) { resolvedRestoreFocusKey }

    LaunchedEffect(Unit) {
        val restoredRowTitle = entryFocusKey
            ?.takeIf { it.startsWith("row:") }
            ?.removePrefix("row:")
            ?.substringBeforeLast(":")
        val restoredRowIndex = state.rows.indexOfFirst { it.title == restoredRowTitle }
        if (restoredRowIndex >= 0) {
            listState.scrollToItem(restoredRowIndex + 1 + if (remoteContinue.isEmpty()) 0 else 1)
        } else {
            listState.scrollToItem(0, 0)
            delay(100)
            repeat(3) {
                if (runCatching { contentEntryFocusRequester.requestFocus() }.getOrDefault(false)) {
                    return@LaunchedEffect
                }
                delay(80)
            }
        }
    }

    val isAtSpotlight by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset < 24
        }
    }
    val allowUnfocusedSpotlightUpdates = isAtSpotlight &&
        (restoreFocusKey == null || restoreFocusKey == HOME_SPOTLIGHT_FOCUS_KEY)

    LaunchedEffect(restoreFocusKey) {
        if (restoreFocusKey != null && restoreFocusKey != HOME_SPOTLIGHT_FOCUS_KEY) {
            // If we've restored focus to a row, ensure the theme color matches it immediately
            val mediaId = restoreFocusKey.substringAfterLast(":").toLongOrNull()
            mediaId?.let { onActiveMediaChanged(it) }
        }
    }

    LaunchedEffect(state.suggestionGeneration) {
        if (state.suggestionGeneration > 0) {
            refreshAlpha.snapTo(0.42f)
            listState.animateScrollToItem(0)
            onFocusKeyChanged(HOME_SPOTLIGHT_FOCUS_KEY)
            delay(80)
            runCatching { contentEntryFocusRequester.requestFocus() }
            refreshAlpha.animateTo(1f, tween(320))
        }
    }
    
    LazyColumn(
        modifier = Modifier.fillMaxSize().graphicsLayer {
            alpha = refreshAlpha.value
            clip = false
        },
        state = listState,
        contentPadding = PaddingValues(bottom = 32.dp)
    ) {
        item {
            FeaturedCarousel(
                featured = state.featured,
                progressMap = state.progressMap,
                completedMediaIds = state.completedMediaIds,
                continueMediaIds = state.continueMediaIds,
                onMediaClick = onMediaClick,
                drawerFocusRequester = drawerFocusRequester,
                contentEntryFocusRequester = contentEntryFocusRequester,
                isContentEntry = entryFocusKey == null || entryFocusKey == HOME_SPOTLIGHT_FOCUS_KEY,
                onFocusKeyChanged = onFocusKeyChanged,
                onContentFocused = onContentFocused,
                onActiveMediaChanged = onActiveMediaChanged,
                allowUnfocusedArtworkUpdates = allowUnfocusedSpotlightUpdates,
                roundedSpotlight = roundedSpotlight
            )
        }
        if (remoteContinue.isNotEmpty()) item(key = "remote-continue") {
            RemoteContinueRow(remoteContinue, onRemoteMediaClick, drawerFocusRequester)
        }
        items(state.rows, key = { it.title }) { row ->
            HomeMediaRow(
                row = row,
                onMediaClick = onMediaClick,
                onMediaLongClick = onMediaLongClick,
                progressMap = state.progressMap,
                drawerFocusRequester = drawerFocusRequester,
                contentEntryFocusRequester = contentEntryFocusRequester,
                restoreFocusKey = entryFocusKey,
                onFocusKeyChanged = onFocusKeyChanged,
                onContentFocused = onContentFocused,
                onActiveMediaChanged = onActiveMediaChanged,
                onOpenLibrary = onOpenLibrary
            )
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun FeaturedCarousel(
    featured: List<MediaEntity>,
    progressMap: Map<Long, Float>,
    completedMediaIds: Set<Long>,
    continueMediaIds: Set<Long>,
    onMediaClick: (Long, FocusRequester, Boolean) -> Unit,
    drawerFocusRequester: FocusRequester?,
    contentEntryFocusRequester: FocusRequester,
    isContentEntry: Boolean,
    onFocusKeyChanged: (String) -> Unit,
    onContentFocused: (FocusRequester) -> Unit,
    onActiveMediaChanged: (Long) -> Unit,
    allowUnfocusedArtworkUpdates: Boolean,
    roundedSpotlight: Boolean
) {
    if (featured.isEmpty()) return

    val carouselState = rememberCarouselState()
    val spotlightRequesters = remember(featured.map { it.id }) {
        List(featured.size) { FocusRequester() }
    }
    var focusActiveSpotlight by remember { mutableStateOf(false) }
    val spotlightShape = RoundedCornerShape(if (roundedSpotlight) 32.dp else 0.dp)
    val spotlightGlow = rememberFocusGlow(focusActiveSpotlight)
    val context = LocalContext.current
    val backdropSpec = remember(context) {
        backdropImageSpec(context, BackdropImageUsage.HOME)
    }

    LaunchedEffect(
        carouselState.activeItemIndex,
        featured,
        focusActiveSpotlight,
        allowUnfocusedArtworkUpdates
    ) {
        if (focusActiveSpotlight) {
            featured.getOrNull(carouselState.activeItemIndex)?.let { onActiveMediaChanged(it.id) }
        } else if (allowUnfocusedArtworkUpdates) {
            // Debounce unfocused updates to allow card focus transitions to "win" the color lock first.
            delay(180)
            if (allowUnfocusedArtworkUpdates) {
                featured.getOrNull(carouselState.activeItemIndex)?.let { onActiveMediaChanged(it.id) }
            }
        }
    }

    LaunchedEffect(focusActiveSpotlight, carouselState.activeItemIndex) {
        if (focusActiveSpotlight) {
            delay(16)
            runCatching {
                if (isContentEntry) {
                    contentEntryFocusRequester.requestFocus()
                } else {
                    spotlightRequesters[carouselState.activeItemIndex].requestFocus()
                }
            }
        }
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(400.dp)
            .padding(16.dp),
        shape = spotlightShape,
        colors = SurfaceDefaults.colors(containerColor = Color.Transparent),
        border = if (roundedSpotlight) {
            Border(
                border = androidx.compose.foundation.BorderStroke(
                    width = if (focusActiveSpotlight) 3.dp else 1.dp,
                    color = if (focusActiveSpotlight) {
                        ApertureTheme.colorScheme.primary
                    } else {
                        ApertureTheme.colorScheme.primary.copy(alpha = 0.28f)
                    }
                ),
                shape = spotlightShape
            )
        } else {
            Border.None
        },
        glow = spotlightGlow
    ) {
        Carousel(
            itemCount = featured.size,
            carouselState = carouselState,
            modifier = Modifier
                .fillMaxSize()
                .onFocusChanged { focusState ->
                    focusActiveSpotlight = focusState.hasFocus
                }
        ) { index ->
            val media = featured[index]
            var isWatchNowFocused by remember(media.id) { mutableStateOf(false) }
            val watchNowFocusRequester = if (
                isContentEntry && index == carouselState.activeItemIndex
            ) {
                contentEntryFocusRequester
            } else {
                spotlightRequesters[index]
            }
            Box(modifier = Modifier.fillMaxSize()) {
                if (media.backdropPath.isNullOrBlank()) {
                    ArtworkFallback(
                        title = media.title,
                        isFocused = isWatchNowFocused,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(backdropImageUrl(media.backdropPath!!, backdropSpec))
                            .size(backdropSpec.widthPx, backdropSpec.heightPx)
                            .crossfade(false)
                            .build(),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            if (roundedSpotlight) {
                                Brush.linearGradient(
                                    colors = listOf(
                                        Color.Black.copy(alpha = 0.22f),
                                        Color.Black.copy(alpha = 0.22f)
                                    )
                                )
                            } else {
                                Brush.verticalGradient(
                                    colors = listOf(HeroGradientStart, HeroGradientEnd),
                                    startY = 0f,
                                    endY = Float.POSITIVE_INFINITY
                                )
                            }
                        )
                )
                Column(
                    modifier = Modifier
                        .padding(32.dp)
                        .align(Alignment.BottomStart)
                ) {
                    Text(
                        text = media.title,
                        style = MaterialTheme.typography.displayMedium,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = {
                            onMediaClick(
                                media.id,
                                watchNowFocusRequester,
                                media.type == "EPISODE" && media.id in continueMediaIds
                            )
                        },
                        modifier = Modifier
                            .then(
                                if (drawerFocusRequester != null) {
                                    Modifier.focusProperties { left = drawerFocusRequester }
                                } else Modifier
                            )
                            .focusRequester(watchNowFocusRequester)
                            .onFocusChanged {
                                isWatchNowFocused = it.isFocused
                                if (it.isFocused) {
                                    onFocusKeyChanged(HOME_SPOTLIGHT_FOCUS_KEY)
                                    onContentFocused(watchNowFocusRequester)
                                }
                            },
                        colors = if (ApertureTheme.isMaterialTv) {
                            ButtonDefaults.colors()
                        } else {
                            ButtonDefaults.colors(
                                containerColor = ApertureTheme.colorScheme.primary.copy(alpha = 0.72f),
                                contentColor = ApertureTheme.colorScheme.onPrimary,
                                focusedContainerColor = ApertureTheme.colorScheme.primary,
                                focusedContentColor = ApertureTheme.colorScheme.onPrimary,
                                pressedContainerColor = ApertureTheme.colorScheme.primary,
                                pressedContentColor = ApertureTheme.colorScheme.onPrimary
                            )
                        }
                    ) {
                        val progress = progressMap[media.id] ?: 0f
                        Text(
                            when {
                                media.id in continueMediaIds -> "Continue"
                                progress >= 0.05f && progress < 0.95f -> "Continue"
                                media.id in completedMediaIds -> "Rewatch"
                                else -> "Watch Now"
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeMediaRow(
    row: HomeRow,
    onMediaClick: (Long, FocusRequester, Boolean) -> Unit,
    onMediaLongClick: (MediaEntity, FocusRequester, Boolean, Boolean) -> Unit,
    progressMap: Map<Long, Float> = emptyMap(),
    drawerFocusRequester: FocusRequester?,
    contentEntryFocusRequester: FocusRequester,
    restoreFocusKey: String?,
    onFocusKeyChanged: (String) -> Unit,
    onContentFocused: (FocusRequester) -> Unit,
    onActiveMediaChanged: (Long) -> Unit,
    onOpenLibrary: (Destination) -> Unit
) {
    if (row.items.isEmpty()) return

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        Text(
            text = row.title,
            style = MaterialTheme.typography.titleLarge,
            color = ApertureTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 16.dp, bottom = 8.dp)
        )
        LazyRow(
            modifier = Modifier.height(270.dp).graphicsLayer { clip = false },
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(ApertureTheme.spacing.large),
            verticalAlignment = Alignment.CenterVertically
        ) {
            itemsIndexed(row.items, key = { _, media -> media.id }) { index, media ->
                val focusKey = "row:${row.title}:${media.id}"
                MediaCard(
                    media = media,
                    onClick = { requester ->
                        onMediaClick(
                            media.id,
                            requester,
                            media.type == "EPISODE" && row.title == "Continue Watching"
                        )
                    },
                    modifier = Modifier.width(150.dp),
                    focusRequester = contentEntryFocusRequester.takeIf {
                        restoreFocusKey == focusKey
                    },
                    progress = progressMap[media.id] ?: 0f,
                    drawerFocusRequester = drawerFocusRequester.takeIf { index == 0 },
                    onFocused = { requester ->
                        onFocusKeyChanged(focusKey)
                        onContentFocused(requester)
                        onActiveMediaChanged(media.id)
                    },
                    onLongClick = { requester, opensToRight ->
                        onMediaLongClick(media, requester, row.title == "Continue Watching", opensToRight)
                    }
                )
            }

            if (row.hasMore) {
                val destination = when (row.title) {
                    "Movies" -> Destination.Movies
                    "TV Shows" -> Destination.Shows
                    else -> null
                }
                if (destination != null) {
                    item(key = "more:${row.title}") {
                        MoreCard(
                            destination = destination,
                            onOpenLibrary = onOpenLibrary,
                            onContentFocused = onContentFocused
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun MoreCard(
    destination: Destination,
    onOpenLibrary: (Destination) -> Unit,
    onContentFocused: (FocusRequester) -> Unit
) {
    val focusRequester = remember { FocusRequester() }
    var isFocused by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .width(150.dp)
            .aspectRatio(2f / 3f)
            .zIndex(if (isFocused) 1f else 0f)
            .graphicsLayer { clip = false }
    ) {
        Surface(
            onClick = { onOpenLibrary(destination) },
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.08f),
            modifier = Modifier
                .fillMaxSize()
                .focusRequester(focusRequester)
                .onFocusChanged {
                    isFocused = it.isFocused
                    if (it.isFocused) onContentFocused(focusRequester)
                },
            shape = ClickableSurfaceDefaults.shape(ApertureTheme.shapes.poster),
            colors = ClickableSurfaceDefaults.colors(
                containerColor = ApertureTheme.colorScheme.mediaCardBackground,
                focusedContainerColor = ApertureTheme.colorScheme.focusedMediaCardBackground
            ),
            border = ClickableSurfaceDefaults.border(
                focusedBorder = Border(
                    border = androidx.compose.foundation.BorderStroke(
                        2.dp,
                        ApertureTheme.colorScheme.border
                    ),
                    shape = ApertureTheme.shapes.poster
                )
            )
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.ArrowForward,
                    contentDescription = null,
                    modifier = Modifier.size(42.dp),
                    tint = ApertureTheme.colorScheme.primary
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "More",
                    style = MaterialTheme.typography.titleMedium,
                    color = ApertureTheme.colorScheme.primary
                )
            }
        }
    }
}

private const val HOME_SPOTLIGHT_FOCUS_KEY = "spotlight"
