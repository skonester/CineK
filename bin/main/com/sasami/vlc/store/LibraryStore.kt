package com.sasami.vlc.store

import androidx.compose.runtime.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

@Serializable
data class WatchEntry(val path: String, val position: Long = 0, val duration: Long = 0, val playedAt: Long = 0)
@Serializable
data class SavedPlaylist(val id: String, val name: String, val paths: List<String> = emptyList())
@Serializable
data class LibraryConfig(
    val favorites: Set<String> = emptySet(),
    val history: List<WatchEntry> = emptyList(),
    val playlists: List<SavedPlaylist> = emptyList(),
    val volume: Int = 80,
    val resume: Boolean = true,
    val grid: Boolean = true,
    val repeat: String = "off",
    val shuffle: Boolean = false
)

/** Additive storage: the original player/config.json schema remains compatible. */
object LibraryStore {
    var favorites by mutableStateOf<Set<String>>(emptySet())
    var history by mutableStateOf<List<WatchEntry>>(emptyList())
    var playlists by mutableStateOf<List<SavedPlaylist>>(emptyList())
    var resume by mutableStateOf(true)
    var grid by mutableStateOf(true)
    var repeat by mutableStateOf("off")
    var shuffle by mutableStateOf(false)
    var page by mutableStateOf("Library")
    var selectedPlaylist by mutableStateOf<String?>(null)
    var selectedFolder by mutableStateOf<String?>(null)
    var showLibrary by mutableStateOf(true)
    var dialog by mutableStateOf<String?>(null)
    var message by mutableStateOf<String?>(null)
    var busy by mutableStateOf(false)
    var fullscreen by mutableStateOf(false)
    var sleepDeadline by mutableStateOf(0L)
    var speed by mutableStateOf(1f)
    var aspect by mutableStateOf("Original")
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }
    private val file: File get() = File(AppPaths.configDir, "library.json")

    fun load() {
        try {
            if (!file.exists()) return
            val data = json.decodeFromString<LibraryConfig>(file.readText())
            favorites = data.favorites
            history = data.history
            playlists = data.playlists
            resume = data.resume
            grid = data.grid
            repeat = data.repeat.takeIf { it in listOf("off", "all", "one") } ?: "off"
            shuffle = data.shuffle
            PlayerStore.volume = data.volume.coerceIn(0, 100)
        } catch (e: Exception) { message = "Could not read library settings: ${e.message}" }
    }

    fun save() {
        try {
            file.parentFile.mkdirs()
            val data = LibraryConfig(favorites, history, playlists, PlayerStore.volume, resume, grid, repeat, shuffle)
            val temporary = File(file.parentFile, "library.json.tmp")
            temporary.writeText(json.encodeToString(LibraryConfig.serializer(), data))
            Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
        } catch (e: Exception) { message = "Could not save library settings: ${e.message}" }
    }

    fun favorite(path: String) {
        favorites = if (path in favorites) favorites - path else favorites + path
        save()
    }

    fun record(path: String, position: Long, duration: Long, started: Boolean = false) {
        if (path.isBlank()) return
        val previous = history.firstOrNull { it.path == path }
        val entry = WatchEntry(path, position.coerceAtLeast(0), duration.coerceAtLeast(0),
            if (started) System.currentTimeMillis() else previous?.playedAt ?: System.currentTimeMillis())
        history = (listOf(entry) + history.filterNot { it.path == path }).take(300)
    }

    fun createPlaylist(name: String): String? {
        if (name.isBlank()) return null
        val id = java.util.UUID.randomUUID().toString()
        playlists = playlists + SavedPlaylist(id, name.trim())
        save()
        return id
    }

    fun addToPlaylist(id: String, path: String) {
        playlists = playlists.map { if (it.id == id) it.copy(paths = (it.paths + path).distinct()) else it }
        save()
    }

    fun removeFromPlaylist(id: String, path: String) {
        playlists = playlists.map { if (it.id == id) it.copy(paths = it.paths - path) else it }
        save()
    }

    fun remove(path: String) {
        favorites = favorites - path
        history = history.filterNot { it.path == path }
        playlists = playlists.map { it.copy(paths = it.paths - path) }
        save()
    }
}
