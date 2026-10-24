package com.sasami.vlc.ui

import java.awt.Frame
import java.awt.Rectangle
import javax.swing.JFrame

/** Resize the existing undecorated window without recreating VLC's native peer. */
class BorderlessFullscreen(private val frame: JFrame) {
    private var restoredBounds: Rectangle? = null
    private var restoredState = Frame.NORMAL
    private var restoredOnTop = false

    fun setEnabled(enabled: Boolean) {
        if (enabled && restoredBounds == null) {
            val monitor = Rectangle(frame.graphicsConfiguration.bounds)
            restoredBounds = Rectangle(frame.bounds)
            restoredState = frame.extendedState
            restoredOnTop = frame.isAlwaysOnTop
            frame.extendedState = Frame.NORMAL
            frame.bounds = monitor
            frame.isAlwaysOnTop = true
            frame.validate()
        } else if (!enabled) {
            val bounds = restoredBounds ?: return
            restoredBounds = null
            frame.extendedState = Frame.NORMAL
            frame.bounds = bounds
            frame.isAlwaysOnTop = restoredOnTop
            frame.extendedState = restoredState
            frame.validate()
        }
    }
}
