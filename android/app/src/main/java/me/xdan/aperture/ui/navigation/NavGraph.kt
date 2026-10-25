package me.xdan.aperture.ui.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.ui.NavDisplay
import androidx.tv.material3.*
import me.xdan.aperture.ui.component.touch.Surface
import me.xdan.aperture.ui.component.touch.NavigationDrawerItem
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.runtime.saveable.rememberSaveable
import me.xdan.aperture.domain.repository.LibraryPreparationStage
import me.xdan.aperture.ui.component.LibraryAccessPrompt
import me.xdan.aperture.ui.component.LocalLibraryAccess
import me.xdan.aperture.ui.component.rememberLibraryAccess
import me.xdan.aperture.ui.screen.open.OpenMediaScreen
import me.xdan.aperture.ui.screen.sources.RemoteSourcesScreen
import me.xdan.aperture.ui.screen.sources.RemotePlayerScreen
import me.xdan.aperture.ui.screen.live.LiveChannelPlayer
import me.xdan.aperture.ui.screen.live.LiveTvScreen
import me.xdan.aperture.ui.screen.discover.DiscoverScreen
import me.xdan.aperture.ui.screen.open.ProvideMediaOpener
import me.xdan.aperture.ui.screen.player.ExternalPlayerScreen
import me.xdan.aperture.ui.screen.home.HomeScreen
import me.xdan.aperture.ui.screen.search.SearchScreen
import me.xdan.aperture.ui.screen.player.PlayerScreen
import me.xdan.aperture.ui.screen.home.HomeViewModel
import me.xdan.aperture.ui.screen.details.MediaDetailsModal
import me.xdan.aperture.ui.screen.settings.SettingsScreen
import me.xdan.aperture.ui.screen.settings.SETTINGS_DEVICE_VIDEOS_FOCUS_KEY
import me.xdan.aperture.ui.screen.mylist.MyListScreen
import me.xdan.aperture.ui.screen.library.LibraryViewModel
import me.xdan.aperture.ui.screen.library.MoviesScreen
import me.xdan.aperture.ui.screen.library.ShowsScreen
import me.xdan.aperture.ui.component.ProvideFocusMemory
import me.xdan.aperture.ui.component.MediaContextMenu
import me.xdan.aperture.ui.component.CineKBrandMark
import me.xdan.aperture.ui.screen.actions.MediaActionsViewModel
import me.xdan.aperture.ui.theme.ApertureBrandFontFamily
import me.xdan.aperture.ui.theme.ApertureTheme

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun NavGraph(
    backstack: NavBackStack<Destination>,
    onNavigate: (Destination) -> Unit,
    mainViewModel: me.xdan.aperture.ui.MainViewModel = viewModel(),
    onPlayerStateChanged: (Boolean) -> Unit = {},
    onPreviewAmbientMode: () -> Unit = {}
) {
    val homeViewModel: HomeViewModel = viewModel()
    val mediaActionsViewModel: MediaActionsViewModel = viewModel()
    val mediaActionState by mediaActionsViewModel.state.collectAsState()
    val currentDestination = backstack.last()
    val isPlayback = currentDestination.isPlayback
    val showDrawer = !isPlayback
    val currentFocusKey = currentDestination.focusKey()

    LaunchedEffect(isPlayback) {
        onPlayerStateChanged(isPlayback)
    }

    // One access tracker for the whole app, so a grant triggers exactly one rescan.
    val libraryAccess = rememberLibraryAccess(
        onAccessGained = { mainViewModel.startLibraryPreparation(force = true) }
    )
    var accessPromptDismissed by rememberSaveable { mutableStateOf(false) }
    val libraryPreparation by mainViewModel.libraryPreparation.collectAsState()
    val isScanning = libraryPreparation.stage == LibraryPreparationStage.DISCOVERING ||
        libraryPreparation.stage == LibraryPreparationStage.MATCHING
    
    var selectedMediaId by remember { mutableStateOf<Long?>(null) }
    var selectedEpisodeOnly by remember { mutableStateOf(false) }
    var contextMediaId by remember { mutableStateOf<Long?>(null) }
    var contextFromContinue by remember { mutableStateOf(false) }
    var contextOpensToRight by remember { mutableStateOf(true) }
    var contextFocusRequester by remember { mutableStateOf<FocusRequester?>(null) }
    val lastFocusedRequesters = remember { mutableStateMapOf<String, FocusRequester>() }
    var homeRestoreFocusKey by remember { mutableStateOf<String?>(null) }
    var settingsRestoreFocusKey by remember { mutableStateOf<String?>(null) }
    var playerOriginFocusKey by remember { mutableStateOf<String?>(null) }
    var pendingPlayerFocusRestore by remember { mutableStateOf<String?>(null) }
    val homeDrawerRequester = remember { FocusRequester() }
    val searchDrawerRequester = remember { FocusRequester() }
    val moviesDrawerRequester = remember { FocusRequester() }
    val showsDrawerRequester = remember { FocusRequester() }
    val myListDrawerRequester = remember { FocusRequester() }
    val settingsDrawerRequester = remember { FocusRequester() }
    val openDrawerRequester = remember { FocusRequester() }
    val openContentEntryRequester = remember { FocusRequester() }
    val liveTvDrawerRequester = remember { FocusRequester() }
    val discoverDrawerRequester = remember { FocusRequester() }
    val discoverContentEntryRequester = remember { FocusRequester() }
    val sourcesContentEntryRequester = remember { FocusRequester() }
    val liveTvContentEntryRequester = remember { FocusRequester() }
    val homeContentEntryRequester = remember { FocusRequester() }
    val searchContentEntryRequester = remember { FocusRequester() }
    val settingsContentEntryRequester = remember { FocusRequester() }
    val myListContentEntryRequester = remember { FocusRequester() }
    val moviesContentEntryRequester = remember { FocusRequester() }
    val showsContentEntryRequester = remember { FocusRequester() }
    val drawerRequesters = remember {
        mapOf(
            "home" to homeDrawerRequester,
            "search" to searchDrawerRequester,
            "movies" to moviesDrawerRequester,
            "shows" to showsDrawerRequester,
            "my_list" to myListDrawerRequester,
            "settings" to settingsDrawerRequester,
            "open" to openDrawerRequester,
            "live_tv" to liveTvDrawerRequester,
            "sources" to discoverDrawerRequester,
            "discover" to discoverDrawerRequester
        )
    }
    val contentEntryRequesters = remember {
        mapOf(
            "home" to homeContentEntryRequester,
            "search" to searchContentEntryRequester,
            "movies" to moviesContentEntryRequester,
            "shows" to showsContentEntryRequester,
            "my_list" to myListContentEntryRequester,
            "settings" to settingsContentEntryRequester,
            "open" to openContentEntryRequester,
            "live_tv" to liveTvContentEntryRequester,
            "sources" to sourcesContentEntryRequester,
            "discover" to discoverContentEntryRequester
        )
    }
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val focusScope = rememberCoroutineScope()
    val pendingFocusJob = remember { arrayOfNulls<Job>(1) }

    fun requestFocusWhenReady(requester: FocusRequester?) {
        pendingFocusJob[0]?.cancel()
        if (requester == null) return
        pendingFocusJob[0] = focusScope.launch {
            delay(150)
            repeat(10) {
                if (runCatching { requester.requestFocus() }.getOrDefault(false)) {
                    return@launch
                }
                delay(100)
            }
        }
    }

    // Every drawer item returns to the content page that opened the drawer.
    // Its label is a destination, but the drawer itself is an overlay on the
    // current destination, so Right must not jump into the hovered label.
    val drawerReturnFocusRequester = currentFocusKey?.let { key ->
        lastFocusedRequesters[key] ?: contentEntryRequesters[key]
    }
    val navigateFromDrawer: (Destination) -> Unit = drawerNavigate@ { destination ->
        if (destination.focusKey() == currentFocusKey) return@drawerNavigate
        if (
            currentDestination is Destination.Home &&
            destination !is Destination.Home
        ) {
            lastFocusedRequesters.remove("home")
            homeRestoreFocusKey = HOME_DEFAULT_FOCUS_KEY
        }
        if (
            currentDestination is Destination.Settings &&
            destination !is Destination.Settings
        ) {
            lastFocusedRequesters.remove("settings")
            settingsRestoreFocusKey = null
        }
        if (destination is Destination.Home) {
            lastFocusedRequesters.remove("home")
            homeRestoreFocusKey = HOME_DEFAULT_FOCUS_KEY
        }
        if (destination is Destination.Movies) {
            // A requester remembered from a previous Movies composition may
            // point to an off-screen or disposed grid card after visiting a
            // different tab. A genuine tab switch re-enters at the first card;
            // simply opening and closing the drawer still preserves position.
            lastFocusedRequesters.remove("movies")
        }
        if (destination is Destination.Search) {
            lastFocusedRequesters.remove("search")
        }
        if (destination is Destination.Shows) {
            lastFocusedRequesters.remove("shows")
        }
        if (destination is Destination.MyList) {
            lastFocusedRequesters.remove("my_list")
        }
        if (destination is Destination.Open) {
            lastFocusedRequesters.remove("open")
        }
        if (destination is Destination.LiveTv) {
            lastFocusedRequesters.remove("live_tv")
        }
        if (destination is Destination.Sources) lastFocusedRequesters.remove("sources")
        if (destination is Destination.Discover) lastFocusedRequesters.remove("discover")
        if (destination is Destination.Settings) {
            lastFocusedRequesters.remove("settings")
            settingsRestoreFocusKey = null
        }
        while (backstack.size > 1) backstack.removeAt(backstack.lastIndex)
        if (destination !is Destination.Home) backstack.add(destination)
    }
    val openDrawer: () -> Unit = {
        drawerState.setValue(DrawerValue.Open)
        requestFocusWhenReady(currentFocusKey?.let(drawerRequesters::get))
    }
    val closeDrawerAndRestoreFocus: () -> Unit = {
        drawerState.setValue(DrawerValue.Closed)
        requestFocusWhenReady(drawerReturnFocusRequester)
    }
    val selectDrawerDestination: (Destination) -> Unit = selectDestination@ { destination ->
        val destinationFocusKey = destination.focusKey()
        if (destinationFocusKey == currentFocusKey) {
            closeDrawerAndRestoreFocus()
            return@selectDestination
        }

        // Clear focus from current drawer item to prevent auto-reopening
        requestFocusWhenReady(null)
        drawerState.setValue(DrawerValue.Closed)
        navigateFromDrawer(destination)
        requestFocusWhenReady(destinationFocusKey?.let(contentEntryRequesters::get))
    }
    val returnFromPlayer: () -> Unit = {
        val originFocusKey = playerOriginFocusKey ?: "home"
        if (originFocusKey == "home") {
            lastFocusedRequesters.remove(originFocusKey)
            homeRestoreFocusKey = HOME_DEFAULT_FOCUS_KEY
        }
        // Remote source lists restore their own focus, including the Discover dialog.
        pendingPlayerFocusRestore = originFocusKey.takeUnless {
            currentDestination is Destination.RemotePlayer && (it == "sources" || it == "discover")
        }
        if (backstack.size > 1) backstack.removeAt(backstack.lastIndex)
    }
    LaunchedEffect(contextMediaId) {
        if (contextMediaId != null) return@LaunchedEffect
        val requester = contextFocusRequester ?: return@LaunchedEffect
        delay(140)
        val restored = runCatching { requester.requestFocus() }.getOrDefault(false)
        if (!restored) {
            val fallback = when (currentFocusKey) {
                "home" -> homeContentEntryRequester
                "search" -> searchContentEntryRequester
                "movies" -> moviesContentEntryRequester
                "shows" -> showsContentEntryRequester
                "my_list" -> myListContentEntryRequester
                "settings" -> settingsContentEntryRequester
                "open" -> openContentEntryRequester
                "live_tv" -> liveTvContentEntryRequester
                "sources" -> sourcesContentEntryRequester
                "discover" -> discoverContentEntryRequester
                else -> null
            }
            fallback?.let { runCatching { it.requestFocus() } }
        }
        contextFocusRequester = null
    }

    LaunchedEffect(currentDestination, pendingPlayerFocusRestore) {
        val focusKey = pendingPlayerFocusRestore ?: return@LaunchedEffect
        if (currentFocusKey != focusKey || currentDestination is Destination.Player) {
            return@LaunchedEffect
        }

        delay(160)
        val rememberedRequester = lastFocusedRequesters[focusKey]
        val fallbackRequester = when (focusKey) {
            "home" -> homeContentEntryRequester
            "search" -> searchContentEntryRequester
            "my_list" -> myListContentEntryRequester
            "movies" -> moviesContentEntryRequester
            "shows" -> showsContentEntryRequester
            "settings" -> settingsContentEntryRequester
            "open" -> openContentEntryRequester
            "live_tv" -> liveTvContentEntryRequester
            "sources" -> sourcesContentEntryRequester
            "discover" -> discoverContentEntryRequester
            else -> null
        }
        val restoredRememberedFocus = rememberedRequester?.let { requester ->
            runCatching { requester.requestFocus() }.getOrDefault(false)
        } == true
        if (!restoredRememberedFocus) {
            fallbackRequester?.let { requester ->
                runCatching { requester.requestFocus() }
            }
        }
        pendingPlayerFocusRestore = null
        playerOriginFocusKey = null
    }

    CompositionLocalProvider(LocalLibraryAccess provides libraryAccess) {
    ProvideMediaOpener(onPlay = { destination ->
        playerOriginFocusKey = currentFocusKey
        onNavigate(destination)
    }) {
    ProvideFocusMemory {
            if (showDrawer) {
                NavigationDrawer(
                    drawerState = drawerState,
                    drawerContent = { drawerValue ->
                        Surface(
                            modifier = Modifier.fillMaxHeight(),
                            colors = SurfaceDefaults.colors(
                                containerColor = if (ApertureTheme.isMaterialTv) {
                                    Color.White
                                } else {
                                    MaterialTheme.colorScheme.surface
                                },
                                contentColor = if (ApertureTheme.isMaterialTv) {
                                    Color(0xFF1A191C)
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                }
                            )
                        ) {
                            Column(
                                modifier = Modifier
                                    .padding(16.dp)
                                    .fillMaxHeight()
                                    .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Logo and Title
                            Row(
                                modifier = Modifier.padding(bottom = 16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CineKBrandMark(
                                    modifier = Modifier.size(40.dp),
                                    spinBlades = drawerValue == DrawerValue.Open
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    "CineK",
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontFamily = ApertureBrandFontFamily,
                                    fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }

                            NavigationDrawerItem(
                                selected = currentDestination is Destination.Home,
                                onClick = {
                                    if (currentDestination is Destination.Home) {
                                        // Spotlight is not composed while Home is scrolled far
                                        // enough down. Return focus to the visible origin first so
                                        // NavigationDrawer can release its scrim; Home's refresh
                                        // effect then scrolls to and focuses Spotlight.
                                        val visibleHomeRequester = drawerReturnFocusRequester
                                        requestFocusWhenReady(null)
                                        val restoredVisibleFocus = visibleHomeRequester?.let { requester ->
                                            runCatching { requester.requestFocus() }.getOrDefault(false)
                                        } == true
                                        drawerState.setValue(DrawerValue.Closed)
                                        homeRestoreFocusKey = HOME_DEFAULT_FOCUS_KEY
                                        if (!restoredVisibleFocus) {
                                            requestFocusWhenReady(visibleHomeRequester)
                                        }
                                        homeViewModel.regenerateSuggestions()
                                    } else {
                                        // Home remains composed underneath the other top-level
                                        // destinations, including its previous scroll position.
                                        // Release drawer focus through the visible page before
                                        // revealing Home, then let Home's refresh effect return to
                                        // Spotlight and establish its new entry focus.
                                        val visibleOriginRequester = drawerReturnFocusRequester
                                        requestFocusWhenReady(null)
                                        val restoredVisibleFocus = visibleOriginRequester?.let { requester ->
                                            runCatching { requester.requestFocus() }.getOrDefault(false)
                                        } == true
                                        drawerState.setValue(DrawerValue.Closed)
                                        navigateFromDrawer(Destination.Home)
                                        if (!restoredVisibleFocus) {
                                            requestFocusWhenReady(homeContentEntryRequester)
                                        }
                                        homeViewModel.regenerateSuggestions()
                                    }
                                },
                                modifier = Modifier
                                    .focusRequester(homeDrawerRequester)
                                    .focusProperties {
                                        right = drawerReturnFocusRequester ?: homeContentEntryRequester
                                    },
                                leadingContent = { Icon(Icons.Rounded.Home, contentDescription = null) }
                            ) {
                                Text("Home")
                            }
                            NavigationDrawerItem(
                                selected = currentDestination is Destination.Open,
                                onClick = { selectDrawerDestination(Destination.Open) },
                                modifier = Modifier
                                    .focusRequester(openDrawerRequester)
                                    .focusProperties {
                                        right = drawerReturnFocusRequester ?: openContentEntryRequester
                                    },
                                leadingContent = { Icon(Icons.Rounded.FileOpen, contentDescription = null) }
                            ) {
                                Text("Open")
                            }
                            NavigationDrawerItem(
                                selected = currentDestination is Destination.LiveTv,
                                onClick = { selectDrawerDestination(Destination.LiveTv) },
                                modifier = Modifier
                                    .focusRequester(liveTvDrawerRequester)
                                    .focusProperties {
                                        right = drawerReturnFocusRequester ?: liveTvContentEntryRequester
                                    },
                                leadingContent = { Icon(Icons.Rounded.LiveTv, contentDescription = null) }
                            ) {
                                Text("Live TV")
                            }
                            NavigationDrawerItem(
                                selected = currentDestination is Destination.Search,
                                onClick = { selectDrawerDestination(Destination.Search) },
                                modifier = Modifier
                                    .focusRequester(searchDrawerRequester)
                                    .focusProperties {
                                        right = drawerReturnFocusRequester ?: searchContentEntryRequester
                                    },
                                leadingContent = { Icon(Icons.Rounded.Search, contentDescription = null) }
                            ) {
                                Text("Search")
                            }
                            NavigationDrawerItem(
                                selected = currentDestination is Destination.Movies,
                                onClick = { selectDrawerDestination(Destination.Movies) },
                                modifier = Modifier
                                    .focusRequester(moviesDrawerRequester)
                                    .focusProperties {
                                        right = drawerReturnFocusRequester ?: moviesContentEntryRequester
                                    },
                                leadingContent = { Icon(Icons.Rounded.Movie, contentDescription = null) }
                            ) {
                                Text("Movies")
                            }
                            NavigationDrawerItem(
                                selected = currentDestination is Destination.Shows,
                                onClick = { selectDrawerDestination(Destination.Shows) },
                                modifier = Modifier
                                    .focusRequester(showsDrawerRequester)
                                    .focusProperties {
                                        right = drawerReturnFocusRequester ?: showsContentEntryRequester
                                    },
                                leadingContent = { Icon(Icons.Rounded.Tv, contentDescription = null) }
                            ) {
                                Text("Shows")
                            }
                            NavigationDrawerItem(
                                selected = currentDestination is Destination.Discover || currentDestination is Destination.Sources,
                                onClick = { selectDrawerDestination(Destination.Discover) },
                                modifier = Modifier.focusRequester(discoverDrawerRequester)
                                    .focusProperties { right = drawerReturnFocusRequester ?: discoverContentEntryRequester },
                                leadingContent = { Icon(Icons.Rounded.Explore, contentDescription = null) }
                            ) { Text("Discover") }
                            NavigationDrawerItem(
                                selected = currentDestination is Destination.MyList,
                                onClick = { selectDrawerDestination(Destination.MyList) },
                                modifier = Modifier
                                    .focusRequester(myListDrawerRequester)
                                    .focusProperties {
                                        right = drawerReturnFocusRequester ?: myListContentEntryRequester
                                    },
                                leadingContent = { Icon(Icons.Rounded.PlaylistAdd, contentDescription = null) }
                            ) {
                                Text("My List")
                            }
                            NavigationDrawerItem(
                                selected = currentDestination is Destination.Settings,
                                onClick = { selectDrawerDestination(Destination.Settings) },
                                modifier = Modifier
                                    .focusRequester(settingsDrawerRequester)
                                    .focusProperties {
                                        right = drawerReturnFocusRequester ?: settingsContentEntryRequester
                                    },
                                leadingContent = { Icon(Icons.Rounded.Settings, contentDescription = null) }
                            ) {
                                Text("Settings")
                            }
                            }
                        }
                    }
                ) {
                    NavContent(
                        homeViewModel = homeViewModel,
                        backstack = backstack,
                        drawerRequesters = drawerRequesters,
                        contentEntryRequesters = contentEntryRequesters,
                        homeRestoreFocusKey = homeRestoreFocusKey,
                        settingsRestoreFocusKey = settingsRestoreFocusKey,
                        onHomeFocusKeyChanged = { homeRestoreFocusKey = it },
                        onSettingsFocusKeyChanged = { settingsRestoreFocusKey = it },
                        onPlayerBack = returnFromPlayer,
                        onActiveMediaChanged = mainViewModel::setActiveMedia,
                        onPreviewAmbientMode = onPreviewAmbientMode,
                        onOpenLibrary = { destination ->
                            selectDrawerDestination(destination)
                            if (destination is Destination.Settings) {
                                settingsRestoreFocusKey = SETTINGS_DEVICE_VIDEOS_FOCUS_KEY
                            }
                        },
                        onContentFocused = { focusKey, requester ->
                            lastFocusedRequesters[focusKey] = requester
                        },
                        onPlayLive = { destination ->
                            playerOriginFocusKey = currentFocusKey
                            onNavigate(destination)
                        },
                        onRemoteNavigate = { destination ->
                            if (destination is Destination.RemotePlayer) playerOriginFocusKey = currentFocusKey
                            onNavigate(destination)
                        },
                        isScanning = isScanning,
                        onRescan = { mainViewModel.startLibraryPreparation(force = true) },
                        onMediaClick = { focusKey, mediaId, requester, episodeOnly ->
                            lastFocusedRequesters[focusKey] = requester
                            mainViewModel.setActiveMedia(mediaId)
                            selectedEpisodeOnly = episodeOnly
                            selectedMediaId = mediaId
                        },
                        onMediaLongClick = { focusKey, media, requester, fromContinue, opensToRight ->
                            lastFocusedRequesters[focusKey] = requester
                            contextMediaId = media.id
                            contextFromContinue = fromContinue
                            contextFocusRequester = requester
                            contextOpensToRight = opensToRight
                            mediaActionsViewModel.load(media.id)
                        }
                    )
                }
            } else {
                NavContent(
                    homeViewModel = homeViewModel,
                    backstack = backstack,
                    drawerRequesters = emptyMap(),
                    contentEntryRequesters = contentEntryRequesters,
                    homeRestoreFocusKey = homeRestoreFocusKey,
                    settingsRestoreFocusKey = settingsRestoreFocusKey,
                    onHomeFocusKeyChanged = {},
                    onSettingsFocusKeyChanged = { settingsRestoreFocusKey = it },
                    onPlayerBack = returnFromPlayer,
                    onActiveMediaChanged = mainViewModel::setActiveMedia,
                    onPreviewAmbientMode = onPreviewAmbientMode,
                    onOpenLibrary = { destination ->
                        selectDrawerDestination(destination)
                        if (destination is Destination.Settings) {
                            settingsRestoreFocusKey = SETTINGS_DEVICE_VIDEOS_FOCUS_KEY
                        }
                    },
                    onContentFocused = { focusKey, requester ->
                        lastFocusedRequesters[focusKey] = requester
                    },
                    onPlayLive = { destination ->
                        playerOriginFocusKey = currentFocusKey
                        onNavigate(destination)
                    },
                    onRemoteNavigate = { destination ->
                        if (destination is Destination.RemotePlayer) playerOriginFocusKey = currentFocusKey
                        onNavigate(destination)
                    },
                    isScanning = isScanning,
                    onRescan = { mainViewModel.startLibraryPreparation(force = true) },
                    onMediaClick = { focusKey, mediaId, requester, episodeOnly ->
                        lastFocusedRequesters[focusKey] = requester
                        mainViewModel.setActiveMedia(mediaId)
                        selectedEpisodeOnly = episodeOnly
                        selectedMediaId = mediaId
                    },
                    onMediaLongClick = { _, media, requester, fromContinue, opensToRight ->
                        contextMediaId = media.id
                        contextFromContinue = fromContinue
                        contextFocusRequester = requester
                        contextOpensToRight = opensToRight
                        mediaActionsViewModel.load(media.id)
                    }
                )
            }

            // Compose this after NavigationDrawer so Aperture owns the top-level
            // Back contract. Dialog windows and the overlays below still get
            // first refusal, while Player is outside this handler entirely.
            BackHandler(
                enabled = showDrawer &&
                    contextMediaId == null &&
                    selectedMediaId == null
            ) {
                if (drawerState.currentValue == DrawerValue.Open) {
                    closeDrawerAndRestoreFocus()
                } else {
                    openDrawer()
                }
            }

            contextMediaId?.takeIf { mediaActionState.media?.id == it }?.let { mediaId ->
                MediaContextMenu(
                    state = mediaActionState,
                    fromContinueWatching = contextFromContinue,
                    opensToRight = contextOpensToRight,
                    onDismiss = {
                        contextMediaId = null
                    },
                    onInfo = {
                        contextFocusRequester = null
                        contextMediaId = null
                        mainViewModel.setActiveMedia(mediaId)
                        selectedEpisodeOnly = contextFromContinue && mediaActionState.media?.type == "EPISODE"
                        selectedMediaId = mediaId
                    },
                    onPlayFromBeginning = {
                        contextFocusRequester = null
                        contextMediaId = null
                        playerOriginFocusKey = currentFocusKey
                        onNavigate(Destination.Player(mediaId, true))
                    },
                    onRemoveContinue = {
                        mediaActionsViewModel.clearProgress(mediaId)
                        contextMediaId = null
                    },
                    onToggleList = {
                        mediaActionsViewModel.toggleFavorite(mediaId)
                        contextMediaId = null
                    },
                    onToggleWatched = {
                        mediaActionsViewModel.toggleWatched(mediaId)
                        contextMediaId = null
                    },
                    onHide = {
                        mediaActionsViewModel.hide(mediaId)
                        contextMediaId = null
                    },
                    onRefreshAssets = {
                        mediaActionsViewModel.refreshAssets(mediaId)
                        contextMediaId = null
                    }
                )
            }

            MediaDetailsModal(
                mediaId = selectedMediaId,
                episodeOnly = selectedEpisodeOnly,
                viewModel = viewModel(),
                onPlay = { mediaId, startFromBeginning ->
                    selectedMediaId = null
                    playerOriginFocusKey = currentFocusKey
                    onNavigate(Destination.Player(mediaId, startFromBeginning))
                },
                onClose = { selectedMediaId = null },
                restoreFocus = {
                    val focusKey = currentFocusKey
                    val rememberedRequester = focusKey?.let { lastFocusedRequesters[it] }
                    val fallbackRequester = when (focusKey) {
                        "home" -> homeContentEntryRequester
                        "search" -> searchContentEntryRequester
                        "my_list" -> myListContentEntryRequester
                        "movies" -> moviesContentEntryRequester
                        "shows" -> showsContentEntryRequester
                        "settings" -> settingsContentEntryRequester
                        "open" -> openContentEntryRequester
                        "live_tv" -> liveTvContentEntryRequester
                        "sources" -> sourcesContentEntryRequester
                        "discover" -> discoverContentEntryRequester
                        else -> null
                    }
                    val restored = rememberedRequester?.let { requester ->
                        runCatching { requester.requestFocus() }.getOrDefault(false)
                    } == true
                    if (!restored) {
                        fallbackRequester?.let { requester ->
                            runCatching { requester.requestFocus() }
                        }
                    }
                }
            )

        }

        // Ask for library access on launch until it is granted or put off for this session.
        if (!accessPromptDismissed && !isPlayback && !libraryAccess.access.isComplete) {
            LibraryAccessPrompt(libraryAccess, onDismiss = { accessPromptDismissed = true })
        }
    }
    }
}

private const val HOME_DEFAULT_FOCUS_KEY = "spotlight"

private fun Destination.focusKey(): String? = when (this) {
    Destination.Home -> "home"
    Destination.Search -> "search"
    Destination.Movies -> "movies"
    Destination.Shows -> "shows"
    Destination.MyList -> "my_list"
    Destination.Settings -> "settings"
    Destination.Open -> "open"
    Destination.LiveTv -> "live_tv"
    is Destination.Sources -> "sources"
    Destination.Discover -> "discover"
    is Destination.Player -> null
    is Destination.ExternalPlayer -> null
    is Destination.LivePlayer -> null
    is Destination.RemotePlayer -> null
}

@Composable
private fun NavContent(
    homeViewModel: HomeViewModel,
    backstack: NavBackStack<Destination>,
    onMediaClick: (String, Long, FocusRequester, Boolean) -> Unit,
    onMediaLongClick: (String, me.xdan.aperture.data.local.entity.MediaEntity, FocusRequester, Boolean, Boolean) -> Unit,
    drawerRequesters: Map<String, FocusRequester>,
    contentEntryRequesters: Map<String, FocusRequester>,
    homeRestoreFocusKey: String?,
    settingsRestoreFocusKey: String?,
    onHomeFocusKeyChanged: (String) -> Unit,
    onSettingsFocusKeyChanged: (String) -> Unit,
    onPlayerBack: () -> Unit,
    onActiveMediaChanged: (Long) -> Unit,
    onPreviewAmbientMode: () -> Unit,
    onOpenLibrary: (Destination) -> Unit,
    onContentFocused: (String, FocusRequester) -> Unit,
    isScanning: Boolean = false,
    onRescan: () -> Unit = {},
    onPlayLive: (Destination.LivePlayer) -> Unit = {},
    onRemoteNavigate: (Destination) -> Unit = {}
) {
    NavDisplay(
        backStack = backstack
    ) { destination ->
        NavEntry<Destination>(destination) {
            val focusKey = destination.focusKey()
            val drawerFocusRequester = focusKey?.let(drawerRequesters::get)
            val contentEntryFocusRequester = focusKey?.let(contentEntryRequesters::get)
                ?: FocusRequester.Default
            val mediaClick: (Long, FocusRequester) -> Unit = { mediaId, requester ->
                focusKey?.let { onMediaClick(it, mediaId, requester, false) }
            }
            val episodeAwareMediaClick: (Long, FocusRequester, Boolean) -> Unit =
                { mediaId, requester, episodeOnly ->
                    focusKey?.let { onMediaClick(it, mediaId, requester, episodeOnly) }
                }
            val mediaLongClick: (me.xdan.aperture.data.local.entity.MediaEntity, FocusRequester, Boolean, Boolean) -> Unit =
                { media, requester, fromContinue, opensToRight ->
                    focusKey?.let { onMediaLongClick(it, media, requester, fromContinue, opensToRight) }
                }
            val contentFocused: (FocusRequester) -> Unit = { requester ->
                focusKey?.let { onContentFocused(it, requester) }
            }
            when (destination) {
                is Destination.Home -> HomeScreen(
                    viewModel = homeViewModel,
                    onMediaClick = episodeAwareMediaClick,
                    onMediaLongClick = mediaLongClick,
                    drawerFocusRequester = drawerFocusRequester,
                    contentEntryFocusRequester = contentEntryFocusRequester,
                    restoreFocusKey = homeRestoreFocusKey,
                    onFocusKeyChanged = onHomeFocusKeyChanged,
                    onContentFocused = contentFocused,
                    onActiveMediaChanged = onActiveMediaChanged,
                    onOpenLibrary = onOpenLibrary,
                    onRemoteMediaClick = { id -> onRemoteNavigate(Destination.Sources(id)) }
                )
                is Destination.Search -> SearchScreen(
                    viewModel = viewModel(),
                    onMediaClick = mediaClick,
                    onMediaLongClick = mediaLongClick,
                    drawerFocusRequester = drawerFocusRequester,
                    contentEntryFocusRequester = contentEntryFocusRequester,
                    onContentFocused = contentFocused
                )
                is Destination.MyList -> MyListScreen(
                    viewModel = viewModel(),
                    onMediaClick = mediaClick,
                    onMediaLongClick = mediaLongClick,
                    drawerFocusRequester = drawerFocusRequester,
                    contentEntryFocusRequester = contentEntryFocusRequester,
                    onContentFocused = contentFocused
                )
                is Destination.Movies -> MoviesScreen(
                    viewModel = viewModel(),
                    onMediaClick = mediaClick,
                    onMediaLongClick = mediaLongClick,
                    drawerFocusRequester = drawerFocusRequester,
                    contentEntryFocusRequester = contentEntryFocusRequester,
                    onContentFocused = contentFocused,
                    onActiveMediaChanged = onActiveMediaChanged
                )
                is Destination.Shows -> ShowsScreen(
                    viewModel = viewModel(),
                    onMediaClick = episodeAwareMediaClick,
                    onMediaLongClick = mediaLongClick,
                    drawerFocusRequester = drawerFocusRequester,
                    contentEntryFocusRequester = contentEntryFocusRequester,
                    onContentFocused = contentFocused,
                    onActiveMediaChanged = onActiveMediaChanged
                )
                is Destination.Settings -> SettingsScreen(
                    drawerFocusRequester = drawerFocusRequester,
                    contentEntryFocusRequester = contentEntryFocusRequester,
                    restoreFocusKey = settingsRestoreFocusKey,
                    onFocusKeyChanged = onSettingsFocusKeyChanged,
                    onContentFocused = contentFocused,
                    onPreviewAmbientMode = onPreviewAmbientMode
                )
                is Destination.Player -> PlayerScreen(
                    mediaId = destination.mediaId,
                    startFromBeginning = destination.startFromBeginning,
                    viewModel = viewModel(),
                    onBack = onPlayerBack,
                    onFinished = onPlayerBack,
                    onLeavePlayerToOpenSubtitles = {
                        onSettingsFocusKeyChanged("open_subtitles")

                        if (backstack.size > 1) {
                            backstack.removeAt(backstack.lastIndex)
                        }

                        backstack.add(Destination.Settings)
                    }
                )
                is Destination.Open -> OpenMediaScreen(
                    isScanning = isScanning,
                    onRescan = onRescan,
                    onOpenLibrarySettings = { onOpenLibrary(Destination.Settings) },
                    drawerFocusRequester = drawerFocusRequester,
                    contentEntryFocusRequester = contentEntryFocusRequester,
                    onContentFocused = contentFocused
                )
                is Destination.ExternalPlayer -> ExternalPlayerScreen(
                    uri = destination.uri,
                    title = destination.title,
                    onBack = onPlayerBack
                )
                is Destination.LiveTv -> LiveTvScreen(
                    viewModel = viewModel(),
                    onPlayChannel = { channel, category ->
                        onPlayLive(Destination.LivePlayer(channel.sourceId, category, channel.url, channel.name))
                    },
                    drawerFocusRequester = drawerFocusRequester,
                    contentEntryFocusRequester = contentEntryFocusRequester,
                    onContentFocused = contentFocused
                )
                is Destination.LivePlayer -> LiveChannelPlayer(
                    destination = destination,
                    viewModel = viewModel(),
                    onBack = onPlayerBack
                )
                is Destination.Discover -> DiscoverScreen(
                    viewModel = viewModel(),
                    onOpenLibraryItem = mediaClick,
                    onPlaySource = { id, session, restart -> onRemoteNavigate(Destination.RemotePlayer(id, session, restart)) },
                    onOpenSaved = { id -> onRemoteNavigate(Destination.Sources(id)) },
                    drawerFocusRequester = drawerFocusRequester,
                    contentEntryFocusRequester = contentEntryFocusRequester,
                    onContentFocused = contentFocused
                )
                is Destination.Sources -> RemoteSourcesScreen(
                    contentId = destination.contentId,
                    onPlay = { id, session, restart -> onRemoteNavigate(Destination.RemotePlayer(id, session, restart)) },
                    drawerFocusRequester = drawerFocusRequester,
                    contentEntryFocusRequester = contentEntryFocusRequester,
                    onContentFocused = contentFocused
                )
                is Destination.RemotePlayer -> RemotePlayerScreen(
                    contentId = destination.contentId, sessionId = destination.sessionId,
                    fromBeginning = destination.fromBeginning, onBack = onPlayerBack
                )
            }
        }
    }
}
