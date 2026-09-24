package com.sasami.vlc

import kotlin.test.Test
import kotlin.test.assertEquals
import com.sasami.vlc.utils.FileUtils
import java.io.File

class DesktopFeatureTest {
    @Test fun queuePersistenceAndPlaylistRegression() = FeatureChecks.main(emptyArray())

    @Test fun installerAssociationsMatchSupportedMedia() {
        val entries = File("installer/CineK.iss").readLines()
            .filter { it.contains("Capabilities\\FileAssociations") }
        fun field(line: String, name: String) = line.substringAfter("$name: \"").substringBefore('"')
        val extensions = entries.map { field(it, "ValueName").removePrefix(".") }
        assertEquals(FileUtils.mediaExtensions, extensions.toSet())
        assertEquals(extensions.size, extensions.toSet().size)
        entries.forEach { entry ->
            val extension = field(entry, "ValueName")
            val expected = if (FileUtils.isSupportedVideoFile("file$extension")) "CineK.Video" else "CineK.Audio"
            assertEquals(expected, field(entry, "ValueData"))
        }
    }
}
