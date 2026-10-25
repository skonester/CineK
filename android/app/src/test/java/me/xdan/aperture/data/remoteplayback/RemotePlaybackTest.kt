package me.xdan.aperture.data.remoteplayback

import android.app.Application
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.SavedStateHandle
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import me.xdan.aperture.data.local.AppDatabase
import me.xdan.aperture.data.local.entity.MediaEntity
import me.xdan.aperture.data.local.entity.PlaybackProgressEntity
import me.xdan.aperture.data.sources.*
import me.xdan.aperture.di.DatabaseModule
import me.xdan.aperture.ui.navigation.Destination
import me.xdan.aperture.ui.navigation.isPlayback
import me.xdan.aperture.ui.screen.sources.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class RemotePlaybackTest {
    private lateinit var db: AppDatabase
    private lateinit var repository: RemoteMediaRepository
    private val stores = mutableListOf<ViewModelStore>()
    private val app get() = ApplicationProvider.getApplicationContext<Application>()
    private val request = RemoteMediaRequest(SourceQuery("Archive Film", year = 1968, tmdbId = 10331))
    private fun stream(site: SourceSite, token: String = "fresh") = ResolvedStream(site, "https://cdn.example/movie.m3u8?token=$token", "1080p",
        "application/x-mpegURL", 1080, mapOf("Referer" to "https://embed.example/", "User-Agent" to "fixture"),
        listOf(SourceSubtitle("https://cdn.example/en.vtt", "English", "en")))

    @Before fun setup() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        db = Room.inMemoryDatabaseBuilder(app, AppDatabase::class.java).allowMainThreadQueries().build()
        repository = RemoteMediaRepository(db.remoteMediaDao())
    }
    @After fun cleanup() {
        stores.forEach { it.clear() }; repository.close(); db.close(); Dispatchers.resetMain()
    }
    private fun <T : androidx.lifecycle.ViewModel> retain(viewModel: T): T = viewModel.also {
        stores += ViewModelStore().apply { put("test", it) }
    }
    private suspend fun waitFor(condition: () -> Boolean) = withTimeout(5_000) { while (!condition()) delay(10) }

    @Test fun stableKeysIgnoreStreamTokensAndSeparateEpisodesAndMediaTypes() {
        val direct = RemoteMediaRequest(SourceQuery("Video"), SourceSite.FILEMOON, "https://filemoon.example/e/42?token=old#part")
        val refreshed = direct.copy(embedUrl = "https://filemoon.example/e/42?token=new")
        assertEquals(direct.contentId, refreshed.contentId)
        assertEquals("https://filemoon.example/e/42", direct.savedEmbedUrl)
        assertEquals(request.contentId, request.copy(query = request.query.copy(title = "A localized title")).contentId)
        val episode = SourceQuery("Show", SourceMediaType.SHOW, tmdbId = 10331, season = 1, episode = 1)
        assertNotEquals(request.contentId, RemoteMediaRequest(episode).contentId)
        assertNotEquals(RemoteMediaRequest(episode).contentId, RemoteMediaRequest(episode.copy(episode = 2)).contentId)
    }

    @Test fun progressSurvivesSourceSwitchAndLateCallbacksFromOldPlayerAreIgnored() = runBlocking {
        val media = repository.remember(request)
        val old = repository.beginPlayback(media.contentId)
        repository.record(media.contentId, old, StreamPlaybackSnapshot(20_000, 100_000, hasStarted = true))
        repository.awaitWrites()
        val selected = repository.select(media.contentId, RemoteSourceOption(SourceSite.VIDSRC, stream(SourceSite.FILEMOON)))
        assertEquals(20_000L, repository.get(media.contentId)?.resumePositionMs)
        val current = repository.beginPlayback(media.contentId)
        repository.record(media.contentId, current, StreamPlaybackSnapshot(40_000, 100_000, hasStarted = true))
        repository.record(media.contentId, old, StreamPlaybackSnapshot(25_000, 100_000, hasStarted = true))
        repository.awaitWrites()
        val stored = requireNotNull(db.remoteMediaDao().get(media.contentId))
        assertEquals(40_000L, stored.positionMs)
        assertEquals(SourceSite.VIDSRC.name, stored.lastProvider)
        assertEquals(selected.option, repository.session(selected.id, media.contentId)?.option)
        assertNull(stored.embedUrl)
        assertFalse(stored.toString().contains("token="))
    }

    @Test fun fiveToNinetyFivePercentPolicyAndFailedStartsDoNotEraseResume() = runBlocking {
        val media = repository.remember(request)
        val token = repository.beginPlayback(media.contentId)
        repository.record(media.contentId, token, StreamPlaybackSnapshot(4_000, 100_000, hasStarted = true))
        repository.awaitWrites(); assertFalse(requireNotNull(repository.get(media.contentId)).canResume)
        repository.record(media.contentId, token, StreamPlaybackSnapshot(5_000, 100_000, hasStarted = true))
        repository.awaitWrites(); assertTrue(requireNotNull(repository.get(media.contentId)).canResume)
        repository.record(media.contentId, token, StreamPlaybackSnapshot(0, 0, hasStarted = false))
        repository.record(media.contentId, token, StreamPlaybackSnapshot(0, 0, hasStarted = true))
        repository.awaitWrites(); assertEquals(5_000L, repository.get(media.contentId)?.positionMs)
        repository.record(media.contentId, token, StreamPlaybackSnapshot(95_000, 100_000, hasStarted = true))
        repository.awaitWrites()
        assertTrue(requireNotNull(repository.get(media.contentId)).isCompleted)
        assertEquals(0L, repository.get(media.contentId)?.resumePositionMs)
        repository.record(media.contentId, token, StreamPlaybackSnapshot(0, 100_000, hasStarted = true))
        repository.awaitWrites(); assertFalse(requireNotNull(repository.get(media.contentId)).isCompleted)
    }

    @Test fun embedQueriesAndHeadersAreNotPersistedAndSourceCanBeResolvedAfterRepositoryRecreation() = runBlocking {
        val direct = RemoteMediaRequest(SourceQuery("Host video"), SourceSite.DOOD, "https://dood.example/e/42?token=private")
        val media = repository.remember(direct)
        repository.select(media.contentId, RemoteSourceOption(SourceSite.DOOD, stream(SourceSite.DOOD, "secret")))
        val token = repository.beginPlayback(media.contentId)
        repository.record(media.contentId, token, StreamPlaybackSnapshot(30_000, 100_000, hasStarted = true))
        repository.awaitWrites(); repository.close()
        repository = RemoteMediaRepository(db.remoteMediaDao())
        val calls = mutableListOf<String>()
        val lookup = object : RemoteSourceLookup {
            override suspend fun find(query: SourceQuery, site: SourceSite) = error("Expected direct host resolution")
            override suspend fun resolve(site: SourceSite, embedUrl: String): SourceResults {
                calls += embedUrl; return SourceResults(listOf(stream(site, "new-token")))
            }
        }
        val player = retain(RemotePlayerViewModel(repository, lookup))
        player.load(media.contentId, "lost-session", false)
        waitFor { !player.state.value.loading }
        assertEquals(listOf("https://dood.example/e/42"), calls)
        assertEquals(30_000L, player.state.value.startPositionMs)
        assertTrue(player.state.value.option?.stream?.url.orEmpty().contains("new-token"))
        assertFalse(requireNotNull(db.remoteMediaDao().get(media.contentId)).toString().contains("private"))
    }

    @Test fun sourcePickerStreamsFastResultsWhileAnotherProviderIsStillLoading() = runBlocking {
        val slow = CompletableDeferred<Unit>()
        val lookup = object : RemoteSourceLookup {
            override suspend fun find(query: SourceQuery, site: SourceSite): SourceResults {
                if (site == SourceSite.RIDOMOVIES) slow.await()
                return if (site == SourceSite.VIDSRC) SourceResults(emptyList(), listOf(SourceFailure(site, SourceFailureKind.UNSUPPORTED, "Unsupported")))
                else SourceResults(listOf(stream(site)))
            }
            override suspend fun resolve(site: SourceSite, embedUrl: String) = SourceResults(listOf(stream(site)))
        }
        val picker = retain(RemoteSourcesViewModel(repository, lookup))
        picker.open(request)
        waitFor { picker.state.value.options.isNotEmpty() }
        assertEquals(SourceSite.STREAMING_UNITY, picker.state.value.options.first().provider)
        assertTrue(SourceSite.RIDOMOVIES in picker.state.value.loading)
        picker.clear()
        slow.complete(Unit); delay(30)
        assertTrue(picker.state.value.options.isEmpty())
        picker.open(RemoteMediaRequest(SourceQuery("Different film")), setOf(SourceSite.STREAMING_UNITY))
        waitFor { picker.state.value.options.isNotEmpty() }
        assertEquals("Different film", picker.state.value.request?.query?.title)
        assertEquals(1, picker.state.value.options.size)
    }

    @Test fun eachRequestedSiteCanPassThroughSelectionPlaybackAndRemoteProgress() = runBlocking {
        val lookup = object : RemoteSourceLookup {
            override suspend fun find(query: SourceQuery, site: SourceSite) = SourceResults(listOf(stream(site)))
            override suspend fun resolve(site: SourceSite, embedUrl: String) = SourceResults(listOf(stream(site)))
        }
        for (site in SourceSite.entries) {
            val picker = retain(RemoteSourcesViewModel(repository, lookup))
            val direct = site.takeIf { it in setOf(SourceSite.FILEMOON, SourceSite.DOOD) }
            picker.open(RemoteMediaRequest(request.query, direct, if (direct != null) "https://host.example/e/42" else null), setOf(site))
            waitFor { picker.state.value.options.isNotEmpty() }
            val selected = CompletableDeferred<Pair<String, String>>()
            picker.select(picker.state.value.options.single(), false) { id, session, _ -> selected.complete(id to session) }
            val (id, session) = withTimeout(5_000) { selected.await() }
            val player = retain(RemotePlayerViewModel(repository, lookup))
            player.load(id, session, false); waitFor { !player.state.value.loading }
            assertEquals(site, player.state.value.option?.provider)
            player.record(id, player.state.value.playbackToken, StreamPlaybackSnapshot(40_000, 100_000, hasStarted = true))
            repository.awaitWrites()
            assertEquals(40_000L, repository.get(id)?.resumePositionMs)
        }
    }

    @Test fun playerRefreshesExpiredUrlsAndStartOverDoesNotDestroyProgressBeforePlayback() = runBlocking {
        val media = repository.remember(request)
        val token = repository.beginPlayback(media.contentId)
        repository.record(media.contentId, token, StreamPlaybackSnapshot(25_000, 100_000, hasStarted = true)); repository.awaitWrites()
        val session = repository.select(media.contentId, RemoteSourceOption(SourceSite.STREAMING_UNITY, stream(SourceSite.STREAMING_UNITY, "expired")))
        val lookup = object : RemoteSourceLookup {
            override suspend fun find(query: SourceQuery, site: SourceSite) = SourceResults(listOf(stream(site, "refreshed")))
            override suspend fun resolve(site: SourceSite, embedUrl: String) = error("Unexpected direct link")
        }
        val player = retain(RemotePlayerViewModel(repository, lookup))
        player.load(media.contentId, session.id, true); waitFor { !player.state.value.loading }
        assertEquals(0L, player.state.value.startPositionMs)
        assertEquals(25_000L, repository.get(media.contentId)?.positionMs)
        player.refresh(); waitFor { !player.state.value.loading }
        assertTrue(player.state.value.option?.stream?.url.orEmpty().contains("refreshed"))
        assertEquals(0L, player.state.value.startPositionMs)
        player.record(media.contentId, player.state.value.playbackToken, StreamPlaybackSnapshot(20_000, 100_000, hasStarted = true))
        repository.awaitWrites()
        player.refresh(); waitFor { !player.state.value.loading }
        assertEquals(20_000L, player.state.value.startPositionMs)
    }

    @Test fun consumedStartOverIntentDoesNotRestartAgainAfterSavedStateRestoration() = runBlocking {
        val media = repository.remember(request)
        val option = RemoteSourceOption(SourceSite.STREAMING_UNITY, stream(SourceSite.STREAMING_UNITY))
        val session = repository.select(media.contentId, option)
        val lookup = object : RemoteSourceLookup {
            override suspend fun find(query: SourceQuery, site: SourceSite) = SourceResults(listOf(stream(site)))
            override suspend fun resolve(site: SourceSite, embedUrl: String) = error("Unexpected direct link")
        }
        val saved = SavedStateHandle()
        val first = retain(RemotePlayerViewModel(repository, lookup, saved))
        first.load(media.contentId, session.id, true); waitFor { !first.state.value.loading }
        first.record(media.contentId, first.state.value.playbackToken, StreamPlaybackSnapshot(30_000, 100_000, hasStarted = true))
        repository.awaitWrites()
        val restored = SavedStateHandle(saved.keys().associateWith { saved.get<Any?>(it) })
        val second = retain(RemotePlayerViewModel(repository, lookup, restored))
        second.load(media.contentId, session.id, true); waitFor { !second.state.value.loading }
        assertEquals(30_000L, second.state.value.startPositionMs)
        val newSession = repository.select(media.contentId, option)
        second.load(media.contentId, newSession.id, true); waitFor { !second.state.value.loading }
        assertEquals(0L, second.state.value.startPositionMs)
    }

    @Test fun migrationFromVersionSevenKeepsLocalLibraryAndProgress() = runBlocking {
        val name = "remote-migration-${System.nanoTime()}.db"
        var fileDb = Room.databaseBuilder(app, AppDatabase::class.java, name).allowMainThreadQueries().build()
        val id = fileDb.mediaDao().insertMedia(MediaEntity(filePath = "/fixture/video.mp4", title = "Local film", type = "MOVIE"))
        fileDb.playbackProgressDao().saveProgress(PlaybackProgressEntity(id, 20_000, 100_000))
        fileDb.close()
        app.openOrCreateDatabase(name, 0, null).use { old ->
            old.execSQL("DROP TABLE remote_media"); old.version = 7
        }
        fileDb = Room.databaseBuilder(app, AppDatabase::class.java, name).addMigrations(DatabaseModule.MIGRATION_7_8).allowMainThreadQueries().build()
        try {
            assertEquals("Local film", fileDb.mediaDao().getMediaById(id)?.title)
            assertEquals(20_000L, fileDb.playbackProgressDao().getProgressForMedia(id)?.position)
            assertTrue(fileDb.remoteMediaDao().observeAll().first().isEmpty())
        } finally { fileDb.close(); app.deleteDatabase(name) }
    }

    @Test fun remoteRoutesContainOnlyIdsAndCountAsFullScreenPlayback() {
        val route: Destination = Destination.RemotePlayer(request.contentId, "session-id")
        val descriptor = Destination.RemotePlayer.serializer().descriptor
        assertEquals(listOf("contentId", "sessionId", "fromBeginning"), (0 until descriptor.elementsCount).map { descriptor.getElementName(it) })
        assertFalse(route.toString().contains("http")); assertFalse(route.toString().contains("token="))
        assertTrue(route.isPlayback); assertFalse(Destination.Sources("saved-id").isPlayback)
    }

    @Test fun removingHistoryInvalidatesLateProgressWrites() = runBlocking {
        val media = repository.remember(request)
        val token = repository.beginPlayback(media.contentId)
        repository.remove(media.contentId)
        repository.record(media.contentId, token, StreamPlaybackSnapshot(50_000, 100_000, hasStarted = true))
        repository.awaitWrites(); assertNull(repository.get(media.contentId))
    }
}
