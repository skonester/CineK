package me.xdan.aperture.util

import java.io.File

data class CleanedMediaInfo(
    val title: String,
    val year: Int? = null,
    val season: Int? = null,
    val episode: Int? = null
)

object FilenameParser {
    private val YEAR_REGEX = Regex("(?<=\\D)(\\d{4})(?=\\D|$)")
    private val TV_SHOW_PATTERNS = listOf(
        Regex("(?i)^(.*?)(?:[. _-]+)?S(\\d{1,2})[. _-]*E(\\d{1,3})(?:[. _-]+.*)?$"),
        Regex("(?i)^(.*?)(?:[. _-]+)?(\\d{1,2})[. _-]*x[. _-]*(\\d{1,3})(?:[. _-]+.*)?$")
    )
    private val SEASON_DIRECTORY_REGEX = Regex("(?i)^(?:season[. _-]*|s)(\\d{1,2})$")
    private val SHOW_SEASON_SUFFIX_REGEX = Regex("(?i)^(.*?)[. _-]+(?:season[. _-]*|s)(\\d{1,2})$")
    
    private val NOISE_REGEX = Regex("(?i)[. ](1080p|720p|4k|2160p|x264|x265|h264|h265|web-dl|bluray|brrip|dvdrip|multi|dual-audio|hc|sub|eng|ita|fre|ger|spa|rus|chi|kor|jpn|hevc|aac|ac3|dts|dd5\\.1|xvid|divx|repack|proper|internal|readnfo|nfofix|complete|unrated|extended|directors.cut|theatrical|limited|remastered|criterion)(?=$|[. _-]).*")

    fun parse(filename: String, filePath: String? = null): CleanedMediaInfo {
        // Strip extension
        val nameWithoutExtension = filename.substringBeforeLast(".")
        
        // Try TV Show match first
        val tvMatch = TV_SHOW_PATTERNS.firstNotNullOfOrNull { it.find(nameWithoutExtension) }
        if (tvMatch != null) {
            val filenameTitle = cleanTitle(tvMatch.groupValues[1])
            val (title, year) = splitTrailingYear(filenameTitle.ifBlank {
                inferShowTitle(filePath) ?: "Unknown TV Show"
            })
            val season = tvMatch.groupValues[2].toIntOrNull()
            val episode = tvMatch.groupValues[3].toIntOrNull()
            return CleanedMediaInfo(title, year, season, episode)
        }
        
        // Try Movie match
        var title = nameWithoutExtension
        val yearMatch = YEAR_REGEX.find(nameWithoutExtension)
        val year = yearMatch?.groupValues?.get(1)?.toIntOrNull()
        
        if (yearMatch != null) {
            title = nameWithoutExtension.substring(0, yearMatch.range.first)
        }
        
        title = cleanTitle(title)
        
        return CleanedMediaInfo(title, year)
    }

    private fun cleanTitle(title: String): String {
        return title.replace(NOISE_REGEX, "")
            .replace(".", " ")
            .replace("_", " ")
            .replace(Regex("[\\[\\]{}()]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    /**
     * Splits a release year off the end of a show title: "Mindhunter (2017)" and
     * "Mindhunter.2017" both name the show Mindhunter, first aired in 2017. TMDB finds
     * no show called "Mindhunter 2017". A title that is only a year, such as 1923, keeps it.
     */
    private fun splitTrailingYear(title: String): Pair<String, Int?> {
        val match = TRAILING_YEAR_REGEX.matchEntire(title) ?: return title to null
        return match.groupValues[1].trim() to match.groupValues[2].toInt()
    }

    private val TRAILING_YEAR_REGEX = Regex("^(.*\\S)\\s+((?:19|20)\\d{2})\\s*-?$")

    private fun inferShowTitle(filePath: String?): String? {
        val parent = filePath?.let(::File)?.parentFile ?: return null
        val showDirectory = if (SEASON_DIRECTORY_REGEX.matches(parent.name)) {
            parent.parentFile
        } else {
            parent
        } ?: return null
        val directoryName = showDirectory.name
        val titleWithoutSeasonSuffix = SHOW_SEASON_SUFFIX_REGEX
            .matchEntire(directoryName)
            ?.groupValues
            ?.getOrNull(1)
            ?: directoryName
        return cleanTitle(titleWithoutSeasonSuffix).takeIf { it.isNotBlank() }
    }
}
