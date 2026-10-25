@file:androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])

package me.xdan.aperture.ui.screen.player

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.drm.DrmSessionManagerProvider
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.hls.playlist.DefaultHlsPlaylistTracker
import androidx.media3.exoplayer.hls.playlist.HlsPlaylistTracker
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy

/** The request headers one stream needs, carried on its [MediaItem] as the tag. */
data class RequestHeaders(val values: Map<String, String>)

/**
 * Builds each item's media source with that item's [RequestHeaders], so a channel's
 * User-Agent or Referer reach its manifest and segments but never another item's
 * requests. Items without headers take the player's usual path, which also covers
 * local files and content URIs.
 *
 * HLS gets the patience a desktop player like VLC has. Free channels such as Pluto TV
 * splice ads in on the server with a live window of only a few segments, and around
 * each splice the playlist can pause or a request can fail. Media3's defaults treat
 * that as fatal within seconds; here it waits and retries instead.
 */
class HeaderAwareMediaSourceFactory(private val context: Context) : MediaSource.Factory {
    private val plain = DefaultMediaSourceFactory(context)
    private val plainData = DefaultDataSource.Factory(context)
    private var drmSessionManagerProvider: DrmSessionManagerProvider? = null
    private var loadErrorHandlingPolicy: LoadErrorHandlingPolicy? = null

    override fun setDrmSessionManagerProvider(provider: DrmSessionManagerProvider): MediaSource.Factory {
        drmSessionManagerProvider = provider
        plain.setDrmSessionManagerProvider(provider)
        return this
    }

    override fun setLoadErrorHandlingPolicy(policy: LoadErrorHandlingPolicy): MediaSource.Factory {
        loadErrorHandlingPolicy = policy
        plain.setLoadErrorHandlingPolicy(policy)
        return this
    }

    override fun getSupportedTypes(): IntArray = plain.supportedTypes

    override fun createMediaSource(mediaItem: MediaItem): MediaSource {
        val headers = (mediaItem.localConfiguration?.tag as? RequestHeaders)?.values.orEmpty()
        val data: DataSource.Factory = if (headers.isEmpty()) plainData else {
            val http = DefaultHttpDataSource.Factory().apply {
                headers["User-Agent"]?.let(::setUserAgent)
                setDefaultRequestProperties(headers - "User-Agent")
            }
            DefaultDataSource.Factory(context, http)
        }
        if (mediaItem.localConfiguration?.mimeType == MimeTypes.APPLICATION_M3U8) {
            return HlsMediaSource.Factory(data)
                .setPlaylistTrackerFactory(PatientPlaylistTracker)
                .setLoadErrorHandlingPolicy(loadErrorHandlingPolicy ?: PatientLoadErrors)
                .apply { drmSessionManagerProvider?.let(::setDrmSessionManagerProvider) }
                .createMediaSource(mediaItem)
        }
        if (headers.isEmpty()) return plain.createMediaSource(mediaItem)
        return DefaultMediaSourceFactory(data)
            .apply {
                drmSessionManagerProvider?.let(::setDrmSessionManagerProvider)
                loadErrorHandlingPolicy?.let(::setLoadErrorHandlingPolicy)
            }
            .createMediaSource(mediaItem)
    }

    private companion object {
        /**
         * Target durations a live playlist may go without new segments before it counts
         * as stuck. Media3's default is 3.5, about 17 seconds on Pluto TV.
         */
        const val StuckTargetDurations = 12.0

        val PatientPlaylistTracker = HlsPlaylistTracker.Factory { dataSourceFactory, policy, parserFactory, cmcd, executor ->
            DefaultHlsPlaylistTracker(dataSourceFactory, policy, parserFactory, cmcd, StuckTargetDurations, executor)
        }

        /** Retries a failed playlist or segment request up to 10 times, at most 5 seconds apart. */
        val PatientLoadErrors = object : DefaultLoadErrorHandlingPolicy(10) {
            override fun getRetryDelayMsFor(loadErrorInfo: LoadErrorHandlingPolicy.LoadErrorInfo): Long {
                val delay = super.getRetryDelayMsFor(loadErrorInfo)
                return if (delay == androidx.media3.common.C.TIME_UNSET) delay else delay.coerceAtMost(5_000)
            }
        }
    }
}
