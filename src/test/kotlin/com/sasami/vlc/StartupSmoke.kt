package com.sasami.vlc

import com.sasami.vlc.player.DesktopPlayback
import com.sasami.vlc.store.PlayerStore
import java.awt.Frame
import java.awt.event.WindowEvent
import java.io.File
import javax.sound.sampled.*
import javax.swing.SwingUtilities
import kotlin.concurrent.thread

/** Exercises the real entry point with multiple Explorer-style file arguments. */
object StartupSmoke {
    @JvmStatic fun main(args: Array<String>) {
        val output = File("build/startup-smoke").absoluteFile.apply { mkdirs() }
        val config = File(output, "config").apply { mkdirs() }
        System.setProperty("sasami.configDir", config.path)
        File(config, "config.json").writeText("""{"lang":"en","player":{"playerList":[]}}""")
        File(config, "library.json").writeText("{}")
        val media = File(output, "Caf\u00e9 \u97f3\u697d #1.WAV")
        AudioSystem.write(AudioInputStream(ByteArray(44100 * 2 * 30).inputStream(),
            AudioFormat(44100f, 16, 1, true, false), 44100L * 30), AudioFileFormat.Type.WAVE, media)
        val second = File(output, "Second file.wav").apply { media.copyTo(this, overwrite = true) }
        thread(name = "startup-verifier", isDaemon = true) {
            try {
                val deadline = System.currentTimeMillis() + 20000
                while (!PlayerStore.isPlaying || PlayerStore.currentTime < 500) {
                    check(System.currentTimeMillis() < deadline) { "Startup file did not start playing" }
                    Thread.sleep(100)
                }
                check(PlayerStore.activeId == media.absolutePath)
                check(DesktopPlayback.queue == listOf(media.absolutePath, second.absolutePath))
                check(PlayerStore.playerList.size == 2)
                println("PASS: startup plays filenames with spaces and Unicode; multiple files enter the queue")
                SwingUtilities.invokeAndWait {
                    val frame = Frame.getFrames().first { it.title == "CineK" }
                    frame.dispatchEvent(WindowEvent(frame, WindowEvent.WINDOW_CLOSING))
                }
            } catch (failure: Throwable) {
                failure.printStackTrace()
                kotlin.system.exitProcess(1)
            }
        }
        com.sasami.vlc.main(arrayOf(media.path, second.path))
    }
}
