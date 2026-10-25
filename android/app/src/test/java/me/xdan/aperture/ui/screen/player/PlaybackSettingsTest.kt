package me.xdan.aperture.ui.screen.player

import me.xdan.aperture.data.local.entity.MediaEntity
import me.xdan.aperture.domain.model.SubtitleEncoding
import me.xdan.aperture.ui.screen.home.episodeAfter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.nio.charset.Charset

class PlaybackSettingsTest {
    private val cue = "1\n00:00:01,000 --> 00:00:02,000\n"

    @Test
    fun automaticEncodingKeepsUtf8AndRescuesWesternFiles() {
        assertNull(SubtitleEncoding.toUtf8("${cue}Café déjà vu".toByteArray(Charsets.UTF_8), SubtitleEncoding.AUTO))
        val western = "${cue}Café déjà vu".toByteArray(Charset.forName("windows-1252"))
        assertEquals("${cue}Café déjà vu", SubtitleEncoding.toUtf8(western, SubtitleEncoding.AUTO)?.toString(Charsets.UTF_8))
    }

    @Test
    fun chosenEncodingConvertsUnlessTheFileSaysOtherwise() {
        val cyrillic = "${cue}Привет".toByteArray(Charset.forName("windows-1251"))
        assertEquals("${cue}Привет", SubtitleEncoding.toUtf8(cyrillic, "windows-1251")?.toString(Charsets.UTF_8))
        // A byte-order mark is trusted over the setting.
        val withBom = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + "${cue}Привет".toByteArray(Charsets.UTF_8)
        assertNull(SubtitleEncoding.toUtf8(withBom, "windows-1251"))
        assertNull(SubtitleEncoding.toUtf8(cyrillic, "UTF-8"))
        assertNull(SubtitleEncoding.toUtf8(cyrillic, "no-such-charset"))
        // Every offered character set exists on the JVM.
        SubtitleEncoding.options.map { it.first }.filter { it != SubtitleEncoding.AUTO }.forEach { Charset.forName(it) }
    }

    private fun episode(id: Long, season: Int?, number: Int?, hidden: Boolean = false) = MediaEntity(
        id = id, filePath = "/tv/show/$id.mkv", title = "Show", type = "EPISODE",
        seasonNumber = season, episodeNumber = number, isHidden = hidden
    )

    @Test
    fun nextEpisodeFollowsSeasonAndEpisodeOrder() {
        val s1e1 = episode(1, 1, 1)
        val s1e2 = episode(2, 1, 2)
        val s1e3Hidden = episode(3, 1, 3, hidden = true)
        val s2e1 = episode(4, 2, 1)
        val unnumbered = episode(5, null, null)
        // The library lists newest first, which isn't episode order.
        val library = listOf(unnumbered, s2e1, s1e3Hidden, s1e2, s1e1)
        assertEquals(s1e2, episodeAfter(library, s1e1))
        // Hidden episodes are skipped, so season 1 runs on into season 2.
        assertEquals(s2e1, episodeAfter(library, s1e2))
        assertEquals(unnumbered, episodeAfter(library, s2e1))
        assertNull(episodeAfter(library, unnumbered))
        // Playing a hidden episode still moves on from it.
        assertEquals(s2e1, episodeAfter(library, s1e3Hidden))
    }

    @Test
    fun upNextNamesWhatIsKnown() {
        assertEquals("S1 · E2 · Pilot", episodeLabel(1, 2, "Pilot"))
        assertEquals("E7", episodeLabel(null, 7, " "))
        assertEquals("Next episode", episodeLabel(null, null, null))
    }
}
