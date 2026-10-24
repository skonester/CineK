package me.xdan.aperture.util

import android.os.Environment
import java.io.File

object MediaScanner {
    private val VIDEO_EXTENSIONS = setOf(
        "mp4", "m4v", "mkv", "avi", "mov", "wmv", "flv", "f4v", "webm", "ts", "m2ts", "mts",
        "3gp", "3g2", "mpg", "mpeg", "ogv", "vob", "divx", "asf"
    )

    fun scanDirectories(): List<File> {
        val root = Environment.getExternalStorageDirectory()
        val allFiles = mutableListOf<File>()
        
        // Targeted scan of common video directories
        val targets = listOf(
            "Movies",
            "TV Shows",
            "TV",
            "Shows",
            "Download",
            "DCIM",
            "Videos"
        )
        targets.forEach { target ->
            val dir = File(root, target)
            if (dir.exists() && dir.isDirectory) {
                scanDir(dir, allFiles)
            }
        }
        
        return allFiles
    }

    fun isVideoFile(name: String): Boolean =
        name.substringAfterLast('.', missingDelimiterValue = "").lowercase() in VIDEO_EXTENSIONS

    private fun scanDir(dir: File, result: MutableList<File>) {
        val files = dir.listFiles() ?: return
        for (file in files) {
            if (file.isDirectory) {
                // Prevent infinite recursion and skip hidden directories
                if (!file.name.startsWith(".")) {
                    scanDir(file, result)
                }
            } else {
                if (isVideoFile(file.name)) {
                    result.add(file)
                }
            }
        }
    }
}
