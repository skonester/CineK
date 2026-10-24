package com.sasami.vlc.ui

import kotlinx.coroutines.delay
import java.awt.SplashScreen

/**
 * The launcher's `-splash` logo (see build.gradle.kts) appears before the JVM loads any
 * classes, and AWT closes it as soon as the first window is shown. CineK keeps its
 * main window hidden until the library and VLC are ready, so the logo covers the whole
 * start-up instead of a blank window that freezes while VLC loads its plugins.
 */
object StartupSplash {
    /** Long enough that a fast start shows the logo rather than flashing it. */
    const val MINIMUM_MILLIS = 1_500L

    /** Waits until the logo has been up for [MINIMUM_MILLIS]; returns at once without one. */
    suspend fun awaitMinimum() {
        val showing = runCatching { SplashScreen.getSplashScreen()?.isVisible == true }.getOrDefault(false)
        if (!showing) return
        val started = ProcessHandle.current().info().startInstant().orElse(null)?.toEpochMilli() ?: return
        val remaining = MINIMUM_MILLIS - (System.currentTimeMillis() - started)
        if (remaining > 0) delay(remaining)
    }
}
