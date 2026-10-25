package me.xdan.aperture.ui.screen.player

import android.content.Context
import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import me.xdan.aperture.domain.model.SubtitleEncoding
import me.xdan.aperture.ui.screen.open.displayName
import java.io.File

/** The subtitle type for a file called [name], or null when it isn't a subtitle format the player reads. */
internal fun subtitleMimeFor(name: String): String? = when (name.substringAfterLast('.', "").lowercase()) {
    "srt" -> MimeTypes.APPLICATION_SUBRIP
    "vtt" -> MimeTypes.TEXT_VTT
    "ssa", "ass" -> MimeTypes.TEXT_SSA
    "ttml", "dfxp", "xml" -> MimeTypes.APPLICATION_TTML
    else -> null
}

/**
 * Copies the subtitle file the viewer picked into the cache, as UTF-8 when [encoding]
 * says it is another character set, and returns it ready to attach and switch on.
 * The copy means playback no longer needs the picker's permission to the original.
 * Returns null when the file can't be read, is too big, or isn't a subtitle.
 */
internal fun copyAddedSubtitle(context: Context, uri: Uri, encoding: String): MediaItem.SubtitleConfiguration? {
    val name = displayName(context, uri)
    val mimeType = subtitleMimeFor(name) ?: return null
    val bytes = runCatching {
        context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
    }.getOrNull() ?: return null
    if (bytes.size > MaxAddedSubtitleBytes) return null
    val text = if (mimeType == MimeTypes.APPLICATION_TTML) bytes else SubtitleEncoding.toUtf8(bytes, encoding) ?: bytes
    val directory = File(context.cacheDir, "added_subtitles").apply { mkdirs() }
    val copy = File(directory, "${uri.toString().hashCode().toUInt()}.${name.substringAfterLast('.')}")
    if (runCatching { copy.writeBytes(text) }.isFailure) return null
    return MediaItem.SubtitleConfiguration.Builder(Uri.fromFile(copy))
        .setMimeType(mimeType)
        .setLabel(name.substringBeforeLast('.'))
        .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
        .build()
}

private const val MaxAddedSubtitleBytes = 8 * 1024 * 1024
