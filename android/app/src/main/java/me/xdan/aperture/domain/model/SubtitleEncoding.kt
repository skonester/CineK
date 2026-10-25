package me.xdan.aperture.domain.model

import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction

/**
 * Character sets for local subtitle files. Older .srt files are often saved in a
 * regional code page rather than UTF-8, which shows as garbled letters.
 */
object SubtitleEncoding {
    /** UTF-8 when the file is valid UTF-8, otherwise Windows-1252 (Western). */
    const val AUTO = "auto"

    /** Preference value (a Java charset name, or [AUTO]) to label. */
    val options: List<Pair<String, String>> = listOf(
        AUTO to "Automatic",
        "UTF-8" to "Unicode (UTF-8)",
        "windows-1252" to "Western (Windows-1252)",
        "ISO-8859-15" to "Western (ISO 8859-15)",
        "windows-1250" to "Central European (Windows-1250)",
        "ISO-8859-2" to "Central European (ISO 8859-2)",
        "windows-1251" to "Cyrillic (Windows-1251)",
        "KOI8-R" to "Cyrillic (KOI8-R)",
        "windows-1253" to "Greek (Windows-1253)",
        "windows-1254" to "Turkish (Windows-1254)",
        "windows-1255" to "Hebrew (Windows-1255)",
        "windows-1256" to "Arabic (Windows-1256)",
        "windows-1257" to "Baltic (Windows-1257)",
        "windows-1258" to "Vietnamese (Windows-1258)",
        "TIS-620" to "Thai (TIS-620)",
        "GB18030" to "Simplified Chinese (GB18030)",
        "Big5" to "Traditional Chinese (Big5)",
        "Shift_JIS" to "Japanese (Shift JIS)",
        "EUC-KR" to "Korean (EUC-KR)"
    )

    fun label(value: String): String = options.firstOrNull { it.first == value }?.second ?: value

    /**
     * [bytes] re-encoded as UTF-8 when [encoding] says they are something else, or
     * null when the player can read them as they are. Files that start with a
     * byte-order mark already say what they are, so they are left alone.
     */
    fun toUtf8(bytes: ByteArray, encoding: String): ByteArray? {
        if (hasUnicodeBom(bytes)) return null
        val charset = when (encoding) {
            AUTO -> if (isUtf8(bytes)) return null else Charset.forName("windows-1252")
            else -> runCatching { Charset.forName(encoding) }.getOrNull() ?: return null
        }
        if (charset == Charsets.UTF_8) return null
        return String(bytes, charset).toByteArray(Charsets.UTF_8)
    }

    private fun hasUnicodeBom(bytes: ByteArray): Boolean {
        fun at(index: Int) = bytes.getOrNull(index)?.toInt()?.and(0xFF)
        return (at(0) == 0xEF && at(1) == 0xBB && at(2) == 0xBF) ||
            (at(0) == 0xFF && at(1) == 0xFE) || (at(0) == 0xFE && at(1) == 0xFF)
    }

    private fun isUtf8(bytes: ByteArray): Boolean = try {
        Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes))
        true
    } catch (_: CharacterCodingException) {
        false
    }
}
