package com.sasami.vlc

import com.sasami.vlc.player.*
import com.sasami.vlc.store.*
import java.awt.*
import java.awt.event.WindowEvent
import java.io.File
import javax.imageio.ImageIO
import javax.sound.sampled.*
import javax.swing.SwingUtilities
import kotlin.concurrent.thread

/** Opt-in real Windows/VLC smoke run, isolated from the user's library. */
object DesktopSmoke {
    @JvmStatic fun main(args: Array<String>) {
        val output = File("build/desktop-smoke").absoluteFile.apply { mkdirs() }
        val config = File(output, "config").apply { mkdirs() }
        System.setProperty("cinek.configDir", config.path)
        val backend = VideoBackend.fromKey(System.getenv("CINEK_SMOKE_BACKEND").orEmpty())
        File(config, "config.json").writeText("""{"lang":"en","player":{"playerList":[],"videoOutput":"${backend.key}"}}""")
        File(config, "library.json").writeText("{}")
        val wav = File(output, "Playback check.wav")
        val samples = ByteArray(44100 * 2 * 30)
        AudioSystem.write(AudioInputStream(samples.inputStream(), AudioFormat(44100f, 16, 1, true, false), 44100L * 30),
            AudioFileFormat.Type.WAVE, wav)
        thread(name = "desktop-verifier", isDaemon = true) {
            try {
                fun waitFor(label: String, timeout: Long = 15000, condition: () -> Boolean) {
                    val until = System.currentTimeMillis() + timeout
                    while (!condition()) {
                        check(System.currentTimeMillis() < until) { "Timed out: $label" }
                        Thread.sleep(100)
                    }
                    println("PASS: $label")
                }
                fun ui(action: () -> Unit) = SwingUtilities.invokeAndWait(action)
                waitFor("VLC initialized") { VlcPlayer.mediaPlayer != null }
                check(VlcPlayer.activeVideoBackend == backend) { "Video backend selection was not applied" }
                println("PASS: ${backend.label} backend selected")
                val frame = Frame.getFrames().first { it.title == "CineK" }
                waitFor("Window visible") { frame.isShowing && frame.width > 0 }
                ui { frame.isAlwaysOnTop = true; frame.toFront() }
                Thread.sleep(1500)
                fun capture(name: String) {
                    ImageIO.write(Robot().createScreenCapture(Rectangle(frame.locationOnScreen, frame.size)), "png", File(output, name))
                }
                fun captureDarkDialog(name: String) {
                    val dialogWindow = Window.getWindows().firstOrNull { it is Dialog && it.isShowing }
                        ?: error("Dialog did not open: $name")
                    val screenshot = Robot().createScreenCapture(Rectangle(dialogWindow.locationOnScreen, dialogWindow.size))
                    ImageIO.write(screenshot, "png", File(output, name))
                    listOf(Point(screenshot.width / 2, screenshot.height - 18),
                        Point(screenshot.width - 18, screenshot.height / 2)).forEach { point ->
                        val pixel = Color(screenshot.getRGB(point.x, point.y))
                        check(maxOf(pixel.red, pixel.green, pixel.blue) < 90) {
                            "Light dialog window background in $name at $point: $pixel"
                        }
                    }
                    println("PASS: dark dialog background in $name")
                }
                capture("library-empty.png")
                ui { DesktopPlayback.importFiles(listOf(wav)) }
                waitFor("Background media import") { PlayerStore.playerList.any { it.path == wav.path } && !LibraryStore.busy }
                val item = PlayerItem(wav.name, wav.path, "audio", wav.length())
                ui {
                    PlayerStore.playerList = listOf(item,
                        PlayerItem("Morning light.mp4", File(output, "Morning light.mp4").path, "video", 120000000),
                        PlayerItem("A quiet afternoon.mkv", File(output, "A quiet afternoon.mkv").path, "video", 520000000))
                    LibraryStore.favorite(item.path)
                    val id = LibraryStore.createPlaylist("Weekend")!!
                    LibraryStore.addToPlaylist(id, item.path)
                }
                Thread.sleep(600)
                capture("library-populated.png")
                ui { DesktopPlayback.play(item, listOf(item), fromStart = true) }
                waitFor("Native media plays") { PlayerStore.isPlaying && PlayerStore.currentTime > 500 }
                val surface = VlcPlayer.mediaPlayerComponent
                ui { DesktopPlayback.seek(10000) }
                waitFor("Seek reaches target") { PlayerStore.currentTime >= 9000 }
                ui { DesktopPlayback.speed(1.5f) }
                check(kotlin.math.abs(VlcPlayer.mediaPlayer!!.status().rate() - 1.5f) < 0.01f)
                println("PASS: playback rate")
                ui { LibraryStore.showLibrary = true }
                Thread.sleep(600)
                check(VlcPlayer.mediaPlayerComponent === surface)
                check(VlcPlayer.mediaPlayerComponent!!.videoSurfaceComponent().isDisplayable)
                check(PlayerStore.isPlaying)
                println("PASS: library navigation preserves native surface and playback")
                ui { LibraryStore.showLibrary = false; PlayerStore.isPlayListCollapsed = false }
                Thread.sleep(600)
                capture("player.png")
                ui { LibraryStore.dialog = "tracks" }
                Thread.sleep(800)
                captureDarkDialog("tracks.png")
                ui { LibraryStore.dialog = null; VlcPlayer.pause() }
                waitFor("Pause") { !PlayerStore.isPlaying }
                ui { DesktopPlayback.checkpoint() }
                check(LibraryStore.history.first { it.path == item.path }.position > 8000)
                ui { DesktopPlayback.stop() }
                waitFor("Stop returns to library") { PlayerStore.activeId.isEmpty() && LibraryStore.showLibrary }
                ui { DesktopPlayback.play(item, listOf(item)) }
                waitFor("Resume saved position") { PlayerStore.isPlaying && PlayerStore.currentTime > 8000 }
                ui { DesktopPlayback.stop(); frame.setSize(1000, 650) }
                Thread.sleep(500)
                capture("library-minimum.png")
                val avi = File(output, "Video check.avi")
                VideoFixture.create(avi)
                val video = PlayerItem(avi.name, avi.path, "video", avi.length())
                ui { DesktopPlayback.play(video, listOf(video), fromStart = true) }
                waitFor("Native video renders") { VlcPlayer.mediaPlayer!!.video().videoDimension()?.width == 320 }
                waitFor("Video clock advances") { PlayerStore.isPlaying && PlayerStore.currentTime > 600 }
                capture("video.png")
                val nativeHandle = com.sun.jna.Native.getComponentID(VlcPlayer.mediaPlayerComponent!!.videoSurfaceComponent())
                val snapshot = File(output, "native-frame.png")
                check(VlcPlayer.mediaPlayer!!.snapshots().save(snapshot))
                waitFor("Video snapshot saved") { snapshot.isFile && snapshot.length() > 0 }
                check(ImageIO.read(snapshot).width == 320)
                ui { LibraryStore.showLibrary = true }
                Thread.sleep(400)
                check(VlcPlayer.mediaPlayerComponent === surface && PlayerStore.isPlaying)
                val previewCanvas = VlcPlayer.mediaPlayerComponent!!.videoSurfaceComponent()
                waitFor("Library preview has a bounded 16:9 viewport") {
                    previewCanvas.width in 250..330 && kotlin.math.abs(previewCanvas.width.toDouble() / previewCanvas.height - 16.0 / 9.0) < 0.05
                }
                fun previewShowsWholeFrame(): Boolean {
                    val screenshot = Robot().createScreenCapture(Rectangle(previewCanvas.locationOnScreen, previewCanvas.size))
                    val tl = Color(screenshot.getRGB(screenshot.width / 10, screenshot.height / 10))
                    val tr = Color(screenshot.getRGB(screenshot.width * 9 / 10, screenshot.height / 10))
                    val bl = Color(screenshot.getRGB(screenshot.width / 10, screenshot.height * 9 / 10))
                    val br = Color(screenshot.getRGB(screenshot.width * 9 / 10, screenshot.height * 9 / 10))
                    ImageIO.write(screenshot, "png", File(output, "library-video-preview.png"))
                    return tl.red < 80 && tl.green > 170 && tr.red > 170 && tr.green > 170 &&
                        bl.red < 80 && bl.green < 80 && br.red > 170 && br.green < 80
                }
                waitFor("All four video corners visible in library preview") { previewShowsWholeFrame() }
                capture("library-with-preview.png")
                ui { LibraryStore.showLibrary = false; VlcPlayer.pause() }
                waitFor("Video paused for window transition checks") { !PlayerStore.isPlaying }
                Thread.sleep(500)
                fun assertVideoVisible(name: String) {
                    val canvas = VlcPlayer.mediaPlayerComponent!!.videoSurfaceComponent()
                    check(com.sun.jna.Native.getComponentID(canvas) == nativeHandle) { "Native video handle changed: $name" }
                    capture("window-$name")
                    var rendered = false
                    var screen: java.awt.image.BufferedImage? = null
                    repeat(20) {
                        if (!rendered) {
                            screen = Robot().createScreenCapture(Rectangle(canvas.locationOnScreen, canvas.size))
                            var colorful = 0
                            var samples = 0
                            val captured = screen!!
                            for (y in captured.height / 5 until captured.height * 4 / 5 step 8) {
                                for (x in captured.width / 5 until captured.width * 4 / 5 step 8) {
                                    val pixel = Color(captured.getRGB(x, y))
                                if (maxOf(pixel.red, pixel.green, pixel.blue) > 80 &&
                                    maxOf(pixel.red, pixel.green, pixel.blue) - minOf(pixel.red, pixel.green, pixel.blue) > 45) colorful++
                                    samples++
                                }
                            }
                            rendered = samples > 0 && colorful.toDouble() / samples > 0.15
                            if (!rendered) Thread.sleep(150)
                        }
                    }
                    ImageIO.write(screen, "png", File(output, name))
                    check(rendered) { "Video surface is black or not rendered: $name" }
                    println("PASS: visible video pixels in $name")
                }
                assertVideoVisible("before-fullscreen.png")
                repeat(3) { cycle ->
                    ui { LibraryStore.fullscreen = true }
                    Thread.sleep(600)
                    assertVideoVisible("fullscreen-$cycle.png")
                    ui { LibraryStore.fullscreen = false }
                    Thread.sleep(600)
                    assertVideoVisible("after-fullscreen-$cycle.png")
                }
                val restoredBounds = Rectangle(frame.bounds)
                ui { frame.extendedState = Frame.MAXIMIZED_BOTH }
                Thread.sleep(500)
                ui { LibraryStore.fullscreen = true }
                Thread.sleep(600)
                assertVideoVisible("fullscreen-from-maximized.png")
                ui { LibraryStore.fullscreen = false }
                Thread.sleep(600)
                check(frame.extendedState == Frame.MAXIMIZED_BOTH) { "Maximized state was not restored" }
                assertVideoVisible("restored-maximized.png")
                // Check actual input and layout behavior, not only a visibility flag.
                ui { frame.extendedState = Frame.NORMAL; frame.bounds = restoredBounds; LibraryStore.fullscreen = true; LibraryStore.message = null }
                Thread.sleep(600)
                val robot = Robot()
                robot.mouseMove(frame.x + frame.width / 2, frame.y + frame.height / 2)
                waitFor("Fullscreen controls hide after idle", 6000) { !com.sasami.vlc.ui.PlayerChrome.visible }
                waitFor("Hidden controls release the whole video viewport") {
                    com.sasami.vlc.ui.PlayerChrome.toolbarBounds == null &&
                        VlcPlayer.mediaPlayerComponent!!.videoSurfaceComponent().height == frame.height
                }
                assertVideoVisible("fullscreen-controls-hidden.png")
                robot.mouseMove(frame.x + frame.width / 2 + 30, frame.y + frame.height / 2)
                waitFor("Native video mouse movement restores controls") {
                    com.sasami.vlc.ui.PlayerChrome.visible && com.sasami.vlc.ui.PlayerChrome.toolbarBounds != null
                }
                val toolbar = com.sasami.vlc.ui.PlayerChrome.toolbarBounds!!
                robot.mouseMove(frame.x + toolbar.x + 20, frame.y + toolbar.y + toolbar.height - 25)
                Thread.sleep(com.sasami.vlc.ui.PlayerChrome.IDLE_MILLIS + 400)
                check(com.sasami.vlc.ui.PlayerChrome.visible) { "Controls hid while the pointer was over them" }
                println("PASS: toolbar hover prevents hiding")
                robot.mousePress(java.awt.event.InputEvent.BUTTON1_DOWN_MASK)
                robot.mouseMove(frame.x + frame.width / 2, frame.y + frame.height / 2)
                Thread.sleep(com.sasami.vlc.ui.PlayerChrome.IDLE_MILLIS + 400)
                check(com.sasami.vlc.ui.PlayerChrome.visible) { "Controls hid during a mouse drag" }
                robot.mouseRelease(java.awt.event.InputEvent.BUTTON1_DOWN_MASK)
                println("PASS: mouse drag prevents hiding")
                waitFor("Controls hide again after interaction", 6000) { !com.sasami.vlc.ui.PlayerChrome.visible }
                ui {
                    KeyboardFocusManager.getCurrentKeyboardFocusManager().dispatchEvent(
                        java.awt.event.KeyEvent(frame, java.awt.event.KeyEvent.KEY_PRESSED, System.currentTimeMillis(), 0,
                            java.awt.event.KeyEvent.VK_M, 'm'))
                }
                waitFor("Keyboard input restores controls") { com.sasami.vlc.ui.PlayerChrome.visible }
                ui { LibraryStore.dialog = "playback" }
                Thread.sleep(500)
                captureDarkDialog("playback-controls.png")
                Thread.sleep(com.sasami.vlc.ui.PlayerChrome.IDLE_MILLIS + 400)
                check(com.sasami.vlc.ui.PlayerChrome.visible) { "Controls hid with settings open" }
                println("PASS: open settings prevent hiding")
                ui { LibraryStore.dialog = null; LibraryStore.fullscreen = false }
                waitFor("Windowed controls restored") {
                    com.sasami.vlc.ui.PlayerChrome.visible && com.sasami.vlc.ui.PlayerChrome.toolbarBounds != null
                }
                ui { frame.extendedState = Frame.NORMAL; frame.bounds = restoredBounds; DesktopPlayback.seek(1000); VlcPlayer.play() }
                repeat(2) { cycle ->
                    ui { LibraryStore.fullscreen = true }
                    Thread.sleep(600)
                    assertVideoVisible("playing-fullscreen-$cycle.png")
                    ui { LibraryStore.fullscreen = false }
                    Thread.sleep(600)
                    assertVideoVisible("playing-windowed-$cycle.png")
                }
                val previousPlay = LibraryStore.history.first { it.path == video.path }.playedAt
                ui { VlcPlayer.play(); LibraryStore.repeat = "one"; DesktopPlayback.seek(9500) }
                waitFor("Seek near video end") { PlayerStore.currentTime >= 8500 }
                waitFor("Repeat one restarts video") { PlayerStore.isPlaying && PlayerStore.currentTime in 100..3000 && LibraryStore.history.first { it.path == video.path }.playedAt > previousPlay }
                ui { LibraryStore.repeat = "off"; DesktopPlayback.seek(9500) }
                waitFor("Queue stops at end with repeat off") { PlayerStore.activeId.isEmpty() }
                println("PASS: desktop smoke complete")
                ui { frame.dispatchEvent(WindowEvent(frame, WindowEvent.WINDOW_CLOSING)) }
            } catch (error: Throwable) {
                error.printStackTrace()
                kotlin.system.exitProcess(1)
            }
        }
        com.sasami.vlc.main(emptyArray())
    }
}
