package com.sasami.vlc.player

import androidx.compose.runtime.*
import com.sasami.vlc.store.*
import com.sasami.vlc.utils.FileUtils
import kotlinx.coroutines.*
import uk.co.caprica.vlcj.player.base.MediaPlayer
import uk.co.caprica.vlcj.player.base.MediaPlayerEventAdapter
import java.io.File
import java.net.URI
import javax.swing.SwingUtilities

/** Desktop features use the existing VLC instance; native discovery and surface ownership stay in VlcPlayer. */
object DesktopPlayback {
    var queue by mutableStateOf<List<String>>(emptyList())
    var transientItems by mutableStateOf<List<PlayerItem>>(emptyList())
    var error by mutableStateOf<String?>(null)
    private var attached: MediaPlayer? = null
    private var pendingResume = 0L
    private var lastVolume = 80
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    fun attach() {
        val player = VlcPlayer.mediaPlayer ?: return
        if (attached === player) return
        attached = player
        player.events().addMediaPlayerEventListener(object : MediaPlayerEventAdapter() {
            override fun playing(mediaPlayer: MediaPlayer) {
                SwingUtilities.invokeLater {
                    error = null
                    val position = pendingResume
                    pendingResume = 0
                    if (position > 0) VlcPlayer.seekTo(position)
                    mediaPlayer.controls().setRate(LibraryStore.speed)
                    mediaPlayer.video().setAspectRatio(LibraryStore.aspect.takeUnless { it == "Original" })
                }
            }
            override fun error(mediaPlayer: MediaPlayer) {
                SwingUtilities.invokeLater {
                    error = "This media could not be opened. Check the file or stream address."
                    PlayerStore.isPlaying = false
                }
            }
        })
    }

    fun play(item: PlayerItem, items: List<PlayerItem>? = null, fromStart: Boolean = false, persist: Boolean = true) {
        if (!item.path.contains("://") && !File(item.path).isFile) {
            LibraryStore.message = "File not found: ${item.name}"
            return
        }
        checkpoint()
        if (items != null) queue = items.map { it.path }.distinct()
        if (items != null) transientItems = if (persist) emptyList() else items
        if (item.path !in queue) queue = queue + item.path
        if (persist) PlayerStore.addPlayerList(item)
        attach()
        if (VlcPlayer.mediaPlayer == null) {
            LibraryStore.message = "VLC could not initialize. Check the bundled VLC runtime."
            return
        }
        val history = LibraryStore.history.firstOrNull { it.path == item.path }
        pendingResume = if (LibraryStore.resume && !fromStart && history != null)
            PlaybackPolicy.resumePosition(history.position, history.duration) else 0
        LibraryStore.record(item.path, pendingResume, history?.duration ?: 0, started = true)
        LibraryStore.save()
        error = null
        PlayerStore.currentTime = 0
        PlayerStore.duration = 0
        LibraryStore.showLibrary = false
        VlcPlayer.load(item.path)
    }

    fun toggle() {
        if (PlayerStore.activeId.isBlank()) {
            PlayerStore.playerList.firstOrNull()?.let { play(it, PlayerStore.playerList) }
        } else VlcPlayer.toggle()
    }

    fun checkpoint() {
        if (PlayerStore.activeId.isNotEmpty() && PlayerStore.duration > 0) {
            LibraryStore.record(PlayerStore.activeId, PlayerStore.currentTime, PlayerStore.duration)
            LibraryStore.save()
        }
    }

    fun stop() {
        checkpoint()
        pendingResume = 0
        VlcPlayer.stop()
        LibraryStore.showLibrary = true
        LibraryStore.fullscreen = false
        error = null
    }

    fun advance(automatic: Boolean = false) {
        val items = queue.mapNotNull { path -> (transientItems + PlayerStore.playerList).find { it.path == path } }
            .ifEmpty { PlayerStore.playerList }
        val current = items.indexOfFirst { it.path == PlayerStore.activeId }
        if (automatic) {
            LibraryStore.record(PlayerStore.activeId, 0, LibraryStore.history.find { it.path == PlayerStore.activeId }?.duration ?: 0)
            LibraryStore.save()
        }
        val index = if (LibraryStore.shuffle && items.size > 1 && !(automatic && LibraryStore.repeat == "one"))
            items.indices.filter { it != current }.random()
        else PlaybackPolicy.nextIndex(items.size, current, LibraryStore.repeat, automatic)
        if (index == null) { stop(); return }
        play(items[index], items, fromStart = automatic, persist = items[index] !in transientItems)
    }

    fun previous() {
        if (PlayerStore.currentTime > 3000) { seek(0); return }
        val items = queue.mapNotNull { path -> (transientItems + PlayerStore.playerList).find { it.path == path } }
            .ifEmpty { PlayerStore.playerList }
        if (items.isEmpty()) return
        val current = items.indexOfFirst { it.path == PlayerStore.activeId }
        play(items[(current - 1 + items.size).coerceAtLeast(0) % items.size], items, fromStart = true,
            persist = items[(current - 1 + items.size).coerceAtLeast(0) % items.size] !in transientItems)
    }

    fun seek(time: Long) {
        val mp = VlcPlayer.mediaPlayer ?: return
        if (PlayerStore.activeId.isNotBlank() && mp.status().isSeekable && PlayerStore.duration > 0)
            VlcPlayer.seekTo(time.coerceIn(0, PlayerStore.duration))
    }

    fun skip(delta: Long) = seek(PlayerStore.currentTime + delta)
    fun volume(value: Int) { VlcPlayer.setVolume(value.coerceIn(0, 100)); LibraryStore.save() }
    fun mute() {
        if (PlayerStore.volume > 0) { lastVolume = PlayerStore.volume; volume(0) }
        else volume(lastVolume.coerceAtLeast(1))
    }

    fun speed(value: Float) {
        if (VlcPlayer.mediaPlayer?.controls()?.setRate(value) == true) LibraryStore.speed = value
        else LibraryStore.message = "Playback speed is unavailable for this media."
    }

    fun enqueue(item: PlayerItem) {
        if (item.path !in queue) queue = queue + item.path
        LibraryStore.message = "Added to queue"
    }

    fun moveQueue(path: String, delta: Int) {
        val mutable = queue.toMutableList()
        val index = mutable.indexOf(path)
        val target = index + delta
        if (index >= 0 && target in mutable.indices) {
            mutable.add(target, mutable.removeAt(index))
            queue = mutable
        }
    }

    fun remove(item: PlayerItem) {
        if (item.path == PlayerStore.activeId) stop()
        queue = queue - item.path
        PlayerStore.removeVideo(item)
        LibraryStore.remove(item.path)
    }

    fun importFiles(files: List<File>, playFirst: Boolean = false) {
        scope.launch {
            LibraryStore.busy = true
            try {
                val items = withContext(Dispatchers.IO) {
                    FileUtils.collectMediaFiles(files).map { file ->
                        PlayerItem(file.name, file.absolutePath,
                            if (FileUtils.isSupportedVideoFile(file.name)) "video" else "audio", file.length())
                    }
                }
                PlayerStore.playerList = (PlayerStore.playerList + items).distinctBy { it.path }
                PlayerStore.save()
                LibraryStore.message = if (items.isEmpty()) "No supported media found." else "Added ${items.size} media files"
                if (playFirst) items.firstOrNull()?.let { play(it, items) }
            } catch (e: Exception) { LibraryStore.message = "Import failed: ${e.message}" }
            finally { LibraryStore.busy = false }
        }
    }

    fun openFiles() {
        val chooser = javax.swing.JFileChooser().apply {
            isMultiSelectionEnabled = true
            fileFilter = javax.swing.filechooser.FileNameExtensionFilter("Media files", *FileUtils.mediaExtensions.toTypedArray())
        }
        if (chooser.showOpenDialog(null) == javax.swing.JFileChooser.APPROVE_OPTION)
            importFiles(chooser.selectedFiles.toList(), true)
    }

    fun openFolder() { FileUtils.pickFolder()?.let { importFiles(listOf(File(it))) } }

    fun stream(address: String): String? {
        val uri = try { URI(address.trim()) } catch (_: Exception) { return "Enter a valid stream URL." }
        if (uri.scheme?.lowercase() !in setOf("http", "https", "rtsp", "rtmp", "udp", "rtp") || uri.rawAuthority.isNullOrBlank())
            return "Use an http, https, rtsp, rtmp, udp or rtp stream URL."
        val path = uri.toString()
        val name = uri.path?.substringAfterLast('/')?.takeIf { it.isNotBlank() } ?: uri.host ?: "Network stream"
        play(PlayerItem(name, path, "stream", 0))
        return null
    }

    fun subtitles() {
        val chooser = javax.swing.JFileChooser().apply {
            fileFilter = javax.swing.filechooser.FileNameExtensionFilter("Subtitles", "srt", "ass", "ssa", "vtt", "sub")
        }
        if (chooser.showOpenDialog(null) == javax.swing.JFileChooser.APPROVE_OPTION) {
            val ok = VlcPlayer.mediaPlayer?.subpictures()?.setSubTitleFile(chooser.selectedFile) == true
            LibraryStore.message = if (ok) "Subtitle loaded" else "Could not load that subtitle file."
        }
    }

    fun snapshot() {
        val chooser = javax.swing.JFileChooser().apply { selectedFile = File("CineK-${System.currentTimeMillis()}.png") }
        if (chooser.showSaveDialog(null) != javax.swing.JFileChooser.APPROVE_OPTION) return
        val target = chooser.selectedFile.let { if (it.extension.equals("png", true)) it else File(it.path + ".png") }
        if (target.exists() && javax.swing.JOptionPane.showConfirmDialog(null, "Replace ${target.name}?", "Save snapshot",
                javax.swing.JOptionPane.YES_NO_OPTION) != javax.swing.JOptionPane.YES_OPTION) return
        val ok = VlcPlayer.mediaPlayer?.snapshots()?.save(target) == true
        LibraryStore.message = if (ok) "Snapshot saved to ${target.name}" else "No video frame is available."
    }
}
