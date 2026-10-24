package com.sasami.vlc.player

import com.sasami.vlc.store.PlayerStore
import com.sasami.vlc.store.LibraryStore
import com.sun.jna.Native
import com.sun.jna.NativeLibrary
import com.sun.jna.Library
import com.sun.jna.Pointer
import com.sun.jna.Memory
import com.sun.jna.win32.StdCallLibrary
import uk.co.caprica.vlcj.factory.MediaPlayerFactory
import uk.co.caprica.vlcj.player.component.EmbeddedMediaPlayerComponent
import uk.co.caprica.vlcj.player.embedded.EmbeddedMediaPlayer
import uk.co.caprica.vlcj.player.base.MediaPlayer
import uk.co.caprica.vlcj.player.base.MediaPlayerEventAdapter
import uk.co.caprica.vlcj.binding.LibVlc
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

data class AudioDevice(val id: String, val name: String)

private interface WinKernel32 : StdCallLibrary {
    fun SetDllDirectoryW(path: String?): Boolean

    companion object {
        val INSTANCE = try {
            Native.load("kernel32", WinKernel32::class.java) as WinKernel32
        } catch (e: Throwable) {
            null
        }
    }
}

/** VLC 3 resets `vout` when attaching an HWND. This bundled-core call runs after that reset. */
private interface VlcCoreVariables : Library {
    fun var_SetChecked(player: Pointer, name: String, type: Int, value: Pointer): Int
}

object VlcPlayer {
    private val coreVariables by lazy { Native.load("libvlccore", VlcCoreVariables::class.java) }
    var activeVideoBackend: VideoBackend = VideoBackend.AUTOMATIC
        private set
    private var factory: MediaPlayerFactory? = null
    private var _mediaPlayerComponent: EmbeddedMediaPlayerComponent? = null
    val mediaPlayerComponent: EmbeddedMediaPlayerComponent?
        get() = _mediaPlayerComponent

    val mediaPlayer: EmbeddedMediaPlayer?
        get() = _mediaPlayerComponent?.mediaPlayer()

    private var initialized = false

    fun init() {
        if (initialized) return
        
        // Setup JNA path for libvlc DLLs
        val vlcPath = runtimeDirectory().absolutePath
        
        val isWindows = System.getProperty("os.name").lowercase().contains("win")
        if (isWindows && File(vlcPath).exists()) {
            WinKernel32.INSTANCE?.SetDllDirectoryW(vlcPath)
        }

        if (File(vlcPath).exists()) {
            NativeLibrary.addSearchPath("libvlc", vlcPath)
            System.setProperty("jna.library.path", vlcPath)
        }

        val pluginsPath = File(vlcPath, "plugins").absolutePath
        val vlcArgs = mutableListOf(
            "--no-video-title-show",
            "--no-audio-time-stretch",
            "--file-caching=1000",
            "--network-caching=1000",
            "--live-caching=1000",
            "--disc-caching=1000"
        )
        if (File(pluginsPath).exists()) {
            vlcArgs.add("--plugin-path=$pluginsPath")
        }

        try {
            val fact = MediaPlayerFactory(vlcArgs)
            factory = fact
            val requested = VideoBackend.fromKey(PlayerStore.videoOutput)
            activeVideoBackend = requested.takeIf { verifiedCoreVersion() && it.isAvailable(File(vlcPath)) }
                ?: VideoBackend.AUTOMATIC
            if (requested != activeVideoBackend) {
                PlayerStore.videoOutput = VideoBackend.AUTOMATIC.key
                PlayerStore.save()
                LibraryStore.message = "The selected video backend is unavailable; using Automatic."
            }
            _mediaPlayerComponent = EmbeddedMediaPlayerComponent(fact, null, null, null, null)
            
            val mp = mediaPlayer ?: throw IllegalStateException("Failed to retrieve vlcj media player")
            
            // Attach event listeners to sync state with PlayerStore
            mp.events().addMediaPlayerEventListener(object : MediaPlayerEventAdapter() {
                override fun playing(mediaPlayer: MediaPlayer) {
                    PlayerStore.isPlaying = true
                }

                override fun paused(mediaPlayer: MediaPlayer) {
                    PlayerStore.isPlaying = false
                }

                override fun stopped(mediaPlayer: MediaPlayer) {
                    PlayerStore.isPlaying = false
                    PlayerStore.currentTime = 0L
                    PlayerStore.duration = 0L
                }

                override fun finished(mediaPlayer: MediaPlayer) {
                    PlayerStore.isPlaying = false
                    PlayerStore.currentTime = 0L
                    PlayerStore.duration = 0L
                    CoroutineScope(Dispatchers.Main).launch {
                        DesktopPlayback.advance(automatic = true)
                    }
                }

                override fun timeChanged(mediaPlayer: MediaPlayer, newTime: Long) {
                    PlayerStore.currentTime = newTime
                }

                override fun lengthChanged(mediaPlayer: MediaPlayer, newLength: Long) {
                    PlayerStore.duration = newLength
                }
            })
            
            // Apply volume
            mp.audio().setVolume(PlayerStore.volume)
            initialized = true
        } catch (e: Exception) {
            System.err.println("Failed to initialize libvlc/vlcj: ${e.message}")
            e.printStackTrace()
        }
    }

    fun runtimeDirectory(): File {
        val appResourcesDir = System.getProperty("compose.application.resources.dir")
        return if (appResourcesDir != null && File(appResourcesDir, "vlc").isDirectory)
            File(appResourcesDir, "vlc") else File("resources/common/vlc").absoluteFile
    }

    private fun verifiedCoreVersion(): Boolean = runCatching { LibVlc.libvlc_get_version().startsWith("3.0.23") }.getOrDefault(false)

    fun availableVideoBackends(): List<VideoBackend> = VideoBackend.entries.filter {
        it == VideoBackend.AUTOMATIC || (verifiedCoreVersion() && it.isAvailable(runtimeDirectory()))
    }

    fun load(filePath: String) {
        if (!initialized) init()
        PlayerStore.activeId = filePath
        val mp = mediaPlayer ?: return
        
        val videoSurface = mediaPlayerComponent?.videoSurfaceComponent()
        if (videoSurface?.isDisplayable == true) {
            playOnSurface(mp, filePath)
            mp.audio().setVolume(PlayerStore.volume)
            if (PlayerStore.audioDeviceId.isNotEmpty()) {
                setAudioDevice(PlayerStore.audioDeviceId)
            }
        } else {
            val listener = object : java.awt.event.HierarchyListener {
                override fun hierarchyChanged(e: java.awt.event.HierarchyEvent) {
                    if (mediaPlayerComponent?.videoSurfaceComponent()?.isDisplayable == true) {
                        playOnSurface(mp, filePath)
                        mp.audio().setVolume(PlayerStore.volume)
                        if (PlayerStore.audioDeviceId.isNotEmpty()) {
                            setAudioDevice(PlayerStore.audioDeviceId)
                        }
                        mediaPlayerComponent?.videoSurfaceComponent()?.removeHierarchyListener(this)
                    }
                }
            }
            videoSurface?.addHierarchyListener(listener)
        }
    }

    private fun playOnSurface(mp: EmbeddedMediaPlayer, path: String) {
        val module = activeVideoBackend.module
        if (module == null) { mp.media().play(path); return }
        try {
            if (!mp.media().prepare(path)) return
            mp.videoSurface().attachVideoSurface()
            val value = Memory(module.length + 1L).apply { setString(0, module) }
            check(coreVariables.var_SetChecked(mp.mediaPlayerInstance().pointer, "vout", 0x40, value) == 0)
            check(LibVlc.libvlc_media_player_play(mp.mediaPlayerInstance()) == 0)
        } catch (e: Throwable) {
            activeVideoBackend = VideoBackend.AUTOMATIC
            PlayerStore.videoOutput = VideoBackend.AUTOMATIC.key
            PlayerStore.save()
            LibraryStore.message = "Could not use the selected video backend; using Automatic."
            mp.media().play(path)
        }
    }

    fun play() {
        if (initialized) {
            mediaPlayer?.controls()?.play()
        }
    }

    fun pause() {
        if (initialized) {
            mediaPlayer?.controls()?.pause()
        }
    }

    fun toggle() {
        if (initialized) {
            val mp = mediaPlayer ?: return
            if (mp.status().isPlaying) {
                pause()
            } else {
                play()
            }
        }
    }

    fun stop() {
        if (initialized) {
            mediaPlayer?.controls()?.stop()
            PlayerStore.activeId = ""
            PlayerStore.isPlaying = false
        }
    }

    fun seekTo(timeMs: Long) {
        if (initialized) {
            mediaPlayer?.controls()?.setTime(timeMs)
        }
    }

    fun setVolume(volume: Int) {
        PlayerStore.volume = volume
        if (initialized) {
            mediaPlayer?.audio()?.setVolume(volume)
        }
    }

    fun getAudioDevices(): List<AudioDevice> {
        if (!initialized) return emptyList()
        val outputs = factory?.audio()?.audioOutputs() ?: return emptyList()
        val result = mutableListOf<AudioDevice>()
        for (output in outputs) {
            val devices = output.devices ?: continue
            for (device in devices) {
                result.add(AudioDevice(id = device.deviceId, name = device.longName ?: device.deviceId))
            }
        }
        return result
    }

    fun setAudioDevice(deviceId: String) {
        if (initialized) {
            PlayerStore.audioDeviceId = deviceId
            PlayerStore.save()
            val mp = mediaPlayer ?: return
            if (deviceId.isEmpty()) {
                mp.audio().setOutputDevice(null, null)
                return
            }
            val outputs = factory?.audio()?.audioOutputs() ?: return
            for (output in outputs) {
                val devices = output.devices ?: continue
                if (devices.any { it.deviceId == deviceId }) {
                    mp.audio().setOutputDevice(output.name, deviceId)
                    return
                }
            }
            mp.audio().setOutputDevice(null, deviceId)
        }
    }

    fun setAudioOutput(outputName: String) {
        if (initialized) {
            PlayerStore.audioOutput = outputName
            PlayerStore.save()
            mediaPlayer?.audio()?.setOutput(outputName)
        }
    }

    fun nextVideo() {
        val list = PlayerStore.playerList
        if (list.isEmpty()) return
        val index = list.indexOfFirst { it.path == PlayerStore.activeId }
        if (index == -1) {
            load(list.first().path)
        } else {
            val nextIndex = (index + 1) % list.size
            load(list[nextIndex].path)
        }
    }

    fun prevVideo() {
        val list = PlayerStore.playerList
        if (list.isEmpty()) return
        val index = list.indexOfFirst { it.path == PlayerStore.activeId }
        if (index == -1) {
            load(list.last().path)
        } else {
            val prevIndex = (index - 1 + list.size) % list.size
            load(list[prevIndex].path)
        }
    }

    fun release() {
        if (initialized) {
            _mediaPlayerComponent?.release()
            _mediaPlayerComponent = null
            factory?.release()
            factory = null
            initialized = false
        }
    }
}
