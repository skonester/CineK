package me.xdan.aperture.data.remoteplayback

import androidx.room.Entity
import androidx.room.PrimaryKey
import me.xdan.aperture.data.sources.SourceMediaType
import me.xdan.aperture.data.sources.SourceQuery
import me.xdan.aperture.data.sources.SourceSite
import me.xdan.aperture.data.sources.httpUrl
import java.security.MessageDigest
import java.text.Normalizer
import java.util.Locale

data class RemoteMediaRequest(val query: SourceQuery, val embedSite: SourceSite? = null, val embedUrl: String? = null) {
    init {
        require((embedSite == null) == (embedUrl == null))
        require(embedSite == null || embedSite in setOf(SourceSite.FILEMOON, SourceSite.DOOD, SourceSite.VIDSRC))
        require(embedUrl == null || httpUrl(embedUrl) != null) { "Enter an HTTP(S) embed link without account credentials." }
        require(query.type != SourceMediaType.SHOW || query.season != null) { "Select a season and episode." }
    }

    /** Persist a host/page locator, never signed queries, fragments, headers, or a resolved media URL. */
    val savedEmbedUrl: String? get() = embedUrl?.let { httpUrl(it)?.newBuilder()?.query(null)?.fragment(null)?.build()?.toString() }

    val contentId: String get() {
        val q = query
        val title = Normalizer.normalize(q.title, Normalizer.Form.NFKC).lowercase(Locale.ROOT).trim()
            .replace(Regex("\\s+"), " ")
        val identity = when {
            q.tmdbId != null -> "tmdb:${q.tmdbId}"
            q.imdbId != null -> "imdb:${q.imdbId.lowercase(Locale.ROOT)}"
            embedSite != null -> "embed:${embedSite.name}:$savedEmbedUrl"
            else -> "title:$title:${q.year ?: "unknown"}"
        }
        val raw = "$identity:${q.type.name}:${q.season ?: 0}:${q.episode ?: 0}"
        return MessageDigest.getInstance("SHA-256").digest(raw.toByteArray()).joinToString("") { "%02x".format(it) }
    }
}

@Entity(tableName = "remote_media")
data class RemoteMediaEntity(
    @PrimaryKey val contentId: String,
    val title: String,
    val mediaType: String,
    val year: Int?,
    val tmdbId: Int?,
    val imdbId: String?,
    val season: Int?,
    val episode: Int?,
    val embedSite: String?,
    val embedUrl: String?,
    val lastProvider: String? = null,
    val lastQuality: Int? = null,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val isCompleted: Boolean = false,
    val lastUpdated: Long = 0
) {
    fun request(): RemoteMediaRequest = RemoteMediaRequest(
        SourceQuery(title, SourceMediaType.valueOf(mediaType), year, tmdbId, imdbId, season, episode),
        embedSite?.let(SourceSite::valueOf), embedUrl
    )

    val displayTitle: String get() = if (season != null) "$title · S${season.toString().padStart(2, '0')}E${episode.toString().padStart(2, '0')}" else title
    val canResume: Boolean get() = !isCompleted && durationMs > 0 && positionMs >= durationMs * 0.05 && positionMs < durationMs * 0.95
    val resumePositionMs: Long get() = if (canResume) positionMs else 0
}

data class StreamPlaybackSnapshot(val positionMs: Long, val durationMs: Long, val ended: Boolean = false, val hasStarted: Boolean = false)

/** Unknown timelines and players that never became ready must not erase an existing resume point. */
internal fun RemoteMediaEntity.withSnapshot(snapshot: StreamPlaybackSnapshot, now: Long): RemoteMediaEntity {
    if (!snapshot.hasStarted) return this
    if (snapshot.durationMs <= 0 && snapshot.positionMs <= 0 && positionMs > 0 && !snapshot.ended) return this
    val duration = snapshot.durationMs.takeIf { it > 0 } ?: durationMs
    val position = snapshot.positionMs.coerceAtLeast(0).let { if (duration > 0) it.coerceAtMost(duration) else it }
    return copy(positionMs = position, durationMs = duration,
        isCompleted = snapshot.ended || (duration > 0 && position >= duration * 0.95), lastUpdated = now)
}
