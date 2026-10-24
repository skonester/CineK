package com.sasami.vlc.utils

import com.sasami.vlc.store.*
import java.io.File
import java.net.URI
import javax.swing.JFileChooser
import javax.swing.JOptionPane
import javax.swing.filechooser.FileNameExtensionFilter

object PlaylistFiles {
    fun parse(text: String, base: File): List<PlayerItem> {
        var title: String? = null
        return buildList {
            text.removePrefix("\uFEFF").lineSequence().map { it.trim() }.forEach { line ->
                when {
                    line.startsWith("#EXTINF:", true) -> title = line.substringAfter(',', "").takeIf { it.isNotBlank() }
                    line.isBlank() || line.startsWith("#") -> Unit
                    else -> {
                        val uri = runCatching { URI(line) }.getOrNull()
                        val network = uri?.scheme?.lowercase() in setOf("http", "https", "rtsp", "rtmp", "udp", "rtp")
                        val file = if (uri?.scheme == "file") runCatching { File(uri) }.getOrNull()
                            else File(line).let { if (it.isAbsolute) it else File(base, line) }
                        if (network || (file != null && FileUtils.isSupportedMediaFile(file.name))) {
                            add(PlayerItem(title ?: if (network) uri?.path?.substringAfter('/')?.takeIf { it.isNotBlank() } ?: line else file!!.name,
                                if (network) line else file!!.absolutePath,
                                if (network) "stream" else if (FileUtils.isSupportedVideoFile(file!!.name)) "video" else "audio",
                                if (network) 0 else file!!.length()))
                        }
                        title = null
                    }
                }
            }
        }.distinctBy { it.path }
    }

    fun encode(items: List<PlayerItem>): String = "#EXTM3U\n" + items.joinToString("\n") {
        "#EXTINF:-1,${it.name.replace('\n', ' ').replace('\r', ' ')}\n${it.path.replace("\n", "").replace("\r", "")}"
    } + "\n"

    fun importPlaylist() {
        val chooser = JFileChooser().apply { fileFilter = FileNameExtensionFilter("M3U playlists", "m3u", "m3u8") }
        if (chooser.showOpenDialog(null) != JFileChooser.APPROVE_OPTION) return
        try {
            val file = chooser.selectedFile
            val items = parse(file.readText(), file.parentFile)
            if (items.isEmpty()) { LibraryStore.message = "This playlist contains no supported entries."; return }
            PlayerStore.playerList = (PlayerStore.playerList + items).distinctBy { it.path }
            PlayerStore.save()
            val id = LibraryStore.createPlaylist(file.nameWithoutExtension)!!
            LibraryStore.playlists = LibraryStore.playlists.map { if (it.id == id) it.copy(paths = items.map { item -> item.path }) else it }
            LibraryStore.save()
            LibraryStore.selectedPlaylist = id
        } catch (e: Exception) { LibraryStore.message = "Could not import playlist: ${e.message}" }
    }

    fun exportPlaylist(playlist: SavedPlaylist) {
        val chooser = JFileChooser().apply { selectedFile = File(playlist.name.replace(Regex("[^a-zA-Z0-9 _-]"), "_") + ".m3u8") }
        if (chooser.showSaveDialog(null) != JFileChooser.APPROVE_OPTION) return
        val file = chooser.selectedFile
        if (file.exists() && JOptionPane.showConfirmDialog(null, "Replace ${file.name}?", "Export playlist",
                JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) return
        try {
            file.writeText(encode(playlist.paths.mapNotNull { path -> PlayerStore.playerList.find { it.path == path } }))
            LibraryStore.message = "Playlist exported"
        } catch (e: Exception) { LibraryStore.message = "Could not export playlist: ${e.message}" }
    }
}
