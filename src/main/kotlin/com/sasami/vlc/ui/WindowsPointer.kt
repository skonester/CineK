package com.sasami.vlc.ui

import androidx.compose.ui.geometry.Offset
import com.sun.jna.Function
import com.sun.jna.Memory
import com.sun.jna.NativeLibrary
import com.sun.jna.Pointer

/** Native VLC children can consume pointer messages before Compose receives them. */
internal object WindowsPointer {
    private val user32 by lazy { NativeLibrary.getInstance("user32") }
    private val point by lazy { Memory(8) }
    private val rect by lazy { Memory(16) }
    private var hiddenCalls = 0
    private fun call(name: String, vararg args: Any?): Int =
        user32.getFunction(name, Function.ALT_CONVENTION).invokeInt(args)

    fun position(handle: Long): Offset? {
        if (call("GetCursorPos", point) == 0 || call("ScreenToClient", Pointer(handle), point) == 0 ||
            call("GetClientRect", Pointer(handle), rect) == 0) return null
        val x = point.getInt(0)
        val y = point.getInt(4)
        return if (x in 0 until rect.getInt(8) && y in 0 until rect.getInt(12)) Offset(x.toFloat(), y.toFloat()) else null
    }

    fun pressed(): Boolean = call("GetAsyncKeyState", 1) and 0x8000 != 0

    fun hideCursor(hidden: Boolean) {
        if (hidden && hiddenCalls == 0) {
            do {
                val count = call("ShowCursor", 0)
                hiddenCalls++
            } while (count >= 0 && hiddenCalls < 16)
        } else if (!hidden && hiddenCalls > 0) {
            repeat(hiddenCalls) { call("ShowCursor", 1) }
            hiddenCalls = 0
        }
    }
}
