package com.sasami.vlc

import com.sun.jna.Function
import com.sun.jna.Memory
import com.sun.jna.NativeLibrary
import com.sun.jna.Pointer
import java.awt.Frame
import java.awt.Point
import java.awt.Rectangle

/** Read and drive the actual HWND in JVM-only visual checks; never discovers AWT frames. */
class NativeWindowProbe(val handle: Long) {
    private val user32 = NativeLibrary.getInstance("user32")
    private fun call(name: String, vararg args: Any?): Int =
        user32.getFunction(name, Function.ALT_CONVENTION).invokeInt(args)
    var bounds: Rectangle
        get() = Memory(16).let { rect ->
            check(call("GetWindowRect", Pointer(handle), rect) != 0)
            Rectangle(rect.getInt(0), rect.getInt(4), rect.getInt(8) - rect.getInt(0), rect.getInt(12) - rect.getInt(4))
        }
        set(value) { call("SetWindowPos", Pointer(handle), null, value.x, value.y, value.width, value.height, 0x14) }
    val x get() = bounds.x
    val y get() = bounds.y
    val width get() = bounds.width
    val height get() = bounds.height
    val locationOnScreen: Point get() = bounds.location
    val size get() = bounds.size
    val isShowing get() = call("IsWindowVisible", Pointer(handle)) != 0
    val isDisplayable get() = call("IsWindow", Pointer(handle)) != 0
    var isAlwaysOnTop: Boolean = false
        set(value) { field = value; DesktopRuntime.window!!.setAlwaysOnTop(value) }
    var extendedState: Int
        get() = if (DesktopRuntime.window!!.isMaximized) Frame.MAXIMIZED_BOTH else Frame.NORMAL
        set(value) { DesktopRuntime.window!!.setMaximized(value == Frame.MAXIMIZED_BOTH) }
    fun toFront() = DesktopRuntime.window!!.toFront()
    fun setSize(width: Int, height: Int) { bounds = bounds.apply { setSize(width, height) } }
    companion object {
        fun dialog(): NativeWindowProbe? {
            val user32 = NativeLibrary.getInstance("user32")
            val next = user32.getFunction("FindWindowExW", Function.ALT_CONVENTION)
            var previous: Pointer? = null
            while (true) {
                val ptr = next.invokePointer(arrayOf(null, previous, null, null)) ?: return null
                previous = ptr
                val pid = Memory(4)
                user32.getFunction("GetWindowThreadProcessId", Function.ALT_CONVENTION).invokeInt(arrayOf(ptr, pid))
                if (pid.getInt(0).toLong() != ProcessHandle.current().pid()) continue
                val text = Memory(2048)
                user32.getFunction("GetWindowTextW", Function.ALT_CONVENTION).invokeInt(arrayOf(ptr, text, 1024))
                val title = text.getWideString(0)
                if (title.contains(com.sasami.vlc.store.LibraryStore.dialog!!.substringBefore(':')))
                    return NativeWindowProbe(Pointer.nativeValue(ptr))
            }
        }
    }
}
