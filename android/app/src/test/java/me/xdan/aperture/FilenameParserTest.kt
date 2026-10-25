package me.xdan.aperture

import me.xdan.aperture.util.FilenameParser
import org.junit.Assert.assertEquals
import org.junit.Test

class FilenameParserTest {
    @Test
    fun parsesCompactSeasonEpisodeFormat() {
        val result = FilenameParser.parse("The.Four.Seasons.S02E04.Spring.mkv")

        assertEquals("The Four Seasons", result.title)
        assertEquals(2, result.season)
        assertEquals(4, result.episode)
    }

    @Test
    fun parsesSpacedSeasonEpisodeFormatUsingFolders() {
        val result = FilenameParser.parse(
            filename = "S2 E4 Spring.mkv",
            filePath = "/media/TV/The Four Seasons/Season 2/S2 E4 Spring.mkv"
        )

        assertEquals("The Four Seasons", result.title)
        assertEquals(2, result.season)
        assertEquals(4, result.episode)
    }

    @Test
    fun stripsSeasonSuffixFromShowFolderNames() {
        val result = FilenameParser.parse(
            filename = "S2 E8 Maratona.mp4",
            filePath = "/media/Tv Shows/The Four Seasons Season 2/S2 E8 Maratona.mp4"
        )

        assertEquals("The Four Seasons", result.title)
        assertEquals(2, result.season)
        assertEquals(8, result.episode)
    }

    @Test
    fun parsesCrossSeasonEpisodeFormat() {
        val result = FilenameParser.parse("The Four Seasons 2x04 Spring.mkv")

        assertEquals("The Four Seasons", result.title)
        assertEquals(2, result.season)
        assertEquals(4, result.episode)
    }

    @Test
    fun splitsTheReleaseYearOffShowTitles() {
        for (name in listOf(
            "Mindhunter (2017) - S01E01 - Episode 1.mkv",
            "Mindhunter.2017.S01E01.1080p.NF.WEB-DL.mkv",
            "Mindhunter 2017 1x01.mkv"
        )) {
            val result = FilenameParser.parse(name)
            assertEquals(name, "Mindhunter", result.title)
            assertEquals(name, 2017, result.year)
            assertEquals(name, 1, result.season)
            assertEquals(name, 1, result.episode)
        }
        val plain = FilenameParser.parse("Mindhunter.S02E03.mkv")
        assertEquals("Mindhunter", plain.title)
        assertEquals(null, plain.year)
    }

    @Test
    fun splitsTheReleaseYearOffShowFolders() {
        val result = FilenameParser.parse(
            filename = "S01E04.mkv",
            filePath = "/media/TV/Mindhunter (2017)/Season 1/S01E04.mkv"
        )
        assertEquals("Mindhunter", result.title)
        assertEquals(2017, result.year)
    }

    @Test
    fun keepsShowsNamedAfterAYear() {
        val result = FilenameParser.parse("1923.S01E01.mkv")
        assertEquals("1923", result.title)
        assertEquals(null, result.year)
    }

    @Test
    fun preservesMovieSequelNumber() {
        val result = FilenameParser.parse("Five Nights at Freddy's 2 2025.mkv")

        assertEquals("Five Nights at Freddy's 2", result.title)
        assertEquals(2025, result.year)
    }

    @Test
    fun removesParenthesisedReleaseYearFromTitle() {
        val result = FilenameParser.parse("Five.Nights.At.Freddys.2 (2025).mp4")

        assertEquals("Five Nights At Freddys 2", result.title)
        assertEquals(2025, result.year)
    }
}
