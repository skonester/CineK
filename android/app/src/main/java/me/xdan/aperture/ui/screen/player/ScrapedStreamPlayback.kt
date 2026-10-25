package me.xdan.aperture.ui.screen.player

import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import me.xdan.aperture.data.sources.ResolvedStream

/** Carries the scraper's Referer/User-Agent into the existing header-aware stream player. */
fun ResolvedStream.toPlayableItem(title: String): PlayableItem = PlayableItem(
    uri = url,
    title = title,
    headers = headers,
    mimeType = mimeType,
    subtitles = subtitles
)

/** Complete Media3 item for consumers that also support the resolved MIME hint and sidecar subtitles. */
fun ResolvedStream.toMediaItem(title: String): MediaItem = toPlayableItem(title).asMediaItem()

internal fun PlayableItem.asMediaItem(asHls: Boolean? = null, mediaId: String? = null): MediaItem =
    externalMediaItem(uri, title, asHls ?: (mimeType?.contains("mpegurl", true) ?: looksLikeHls(uri, live)), headers, mediaId)
    .buildUpon()
    .apply { if (asHls == null && mimeType != null) setMimeType(mimeType) }
    .setSubtitleConfigurations(subtitles.map { subtitle ->
        MediaItem.SubtitleConfiguration.Builder(android.net.Uri.parse(subtitle.url))
            .setLabel(subtitle.label)
            .setLanguage(subtitle.language)
            .setMimeType(if (subtitle.url.substringBefore('?').endsWith(".srt", true)) MimeTypes.APPLICATION_SUBRIP else MimeTypes.TEXT_VTT)
            .build()
    })
    .build()
