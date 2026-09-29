package com.sasami.vlc.store

import java.io.File

/** CineK's per-user data folder. `cinek.configDir` isolates test runs from the user's library. */
object AppPaths {
    val configDir: File get() = System.getProperty("cinek.configDir")?.let(::File)
        ?: File(System.getenv("APPDATA") ?: File(System.getProperty("user.home"), "AppData/Roaming").path, "CineK")
}
