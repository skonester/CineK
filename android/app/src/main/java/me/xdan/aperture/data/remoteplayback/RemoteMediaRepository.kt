package me.xdan.aperture.data.remoteplayback

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import me.xdan.aperture.data.sources.ResolvedStream
import me.xdan.aperture.data.sources.SourceQuery
import me.xdan.aperture.data.sources.SourceResults
import me.xdan.aperture.data.sources.SourceSite
import me.xdan.aperture.data.sources.StreamingScrapers
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

interface RemoteSourceLookup {
    suspend fun find(query: SourceQuery, site: SourceSite): SourceResults
    suspend fun resolve(site: SourceSite, embedUrl: String): SourceResults
}

@Singleton
class ScraperRemoteSourceLookup @Inject constructor(private val scrapers: StreamingScrapers) : RemoteSourceLookup {
    override suspend fun find(query: SourceQuery, site: SourceSite) = scrapers.findSources(query, setOf(site))
    override suspend fun resolve(site: SourceSite, embedUrl: String) = scrapers.resolve(site, embedUrl)
}

data class RemoteSourceOption(val provider: SourceSite, val stream: ResolvedStream, val resolvedAt: Long = System.currentTimeMillis())
data class RemotePlaybackSession(val id: String, val contentId: String, val option: RemoteSourceOption, val createdAt: Long)

@Singleton
class RemoteMediaRepository @Inject constructor(private val dao: RemoteMediaDao) {
    val history = dao.observeAll()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val writes = Channel<suspend () -> Unit>(Channel.UNLIMITED)
    private val active = ConcurrentHashMap<String, String>()
    private val latest = ConcurrentHashMap<String, StreamPlaybackSnapshot>()
    private val requests = ConcurrentHashMap<String, RemoteMediaRequest>()
    private val sessions = LinkedHashMap<String, RemotePlaybackSession>()
    private val _saveError = MutableStateFlow<String?>(null)
    val saveError = _saveError.asStateFlow()

    init {
        scope.launch {
            for (write in writes) {
                try { write(); _saveError.value = null }
                catch (e: kotlinx.coroutines.CancellationException) { throw e }
                catch (_: Exception) { _saveError.value = "Could not save remote watch progress." }
            }
        }
    }

    suspend fun remember(request: RemoteMediaRequest): RemoteMediaEntity {
        val query = request.query
        val media = RemoteMediaEntity(request.contentId, query.title.trim(), query.type.name, query.year,
            query.tmdbId, query.imdbId, query.season, query.episode, request.embedSite?.name, request.savedEmbedUrl)
        dao.remember(media)
        requests[media.contentId] = request // Complete signed embed input is session-only.
        return requireNotNull(get(media.contentId))
    }

    suspend fun get(id: String): RemoteMediaEntity? = dao.get(id)?.let { media ->
        latest[id]?.let { media.withSnapshot(it, media.lastUpdated) } ?: media
    }

    suspend fun request(id: String): RemoteMediaRequest? = requests[id] ?: get(id)?.request()

    suspend fun select(id: String, option: RemoteSourceOption): RemotePlaybackSession {
        dao.selectProvider(id, option.provider.name, option.stream.quality)
        val session = RemotePlaybackSession(UUID.randomUUID().toString(), id, option, option.resolvedAt)
        synchronized(sessions) {
            sessions[session.id] = session
            while (sessions.size > 8) sessions.remove(sessions.keys.first())
        }
        return session
    }

    fun session(id: String?, contentId: String): RemotePlaybackSession? = synchronized(sessions) {
        sessions[id]?.takeIf { it.contentId == contentId && System.currentTimeMillis() - it.createdAt < 10 * 60_000 }
    }

    fun beginPlayback(id: String): String = UUID.randomUUID().toString().also { active[id] = it }

    /** App-scope ordered writes survive a player ViewModel being cleared during navigation. */
    fun record(id: String, playbackToken: String, snapshot: StreamPlaybackSnapshot) {
        if (active[id] != playbackToken || !snapshot.hasStarted) return
        latest[id] = snapshot
        val now = System.currentTimeMillis()
        writes.trySend { if (active[id] == playbackToken) dao.saveProgress(id, snapshot, now) }
    }

    suspend fun remove(id: String) {
        active.remove(id); latest.remove(id); requests.remove(id)
        awaitWrites()
        dao.delete(id)
    }

    suspend fun awaitWrites() {
        val completed = CompletableDeferred<Unit>()
        writes.send { completed.complete(Unit) }
        completed.await()
    }

    internal fun close() { writes.close(); scope.cancel() }
}
