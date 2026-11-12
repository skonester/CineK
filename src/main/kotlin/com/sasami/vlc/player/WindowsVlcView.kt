package com.sasami.vlc.player

import com.sun.jna.Function
import com.sun.jna.NativeLibrary
import com.sun.jna.Pointer
import com.sun.jna.WString
import dev.nucleusframework.window.tao.NucleusPlatformView
import dev.nucleusframework.window.tao.nucleusHwndPlatformView

/** An owned Win32 child window. Nucleus reparents/sizes it and disposes it after detaching. */
object WindowsVlcView {
    fun create(parent: Long): NucleusPlatformView.HWnd {
        val user32 = NativeLibrary.getInstance("user32")
        val createWindow = user32.getFunction("CreateWindowExW", Function.ALT_CONVENTION)
        val hwnd = createWindow.invokePointer(arrayOf(
            0, WString("STATIC"), WString(""), 0x46000004, // CHILD | CLIPSIBLINGS | CLIPCHILDREN | SS_BLACKRECT
            0, 0, 1, 1, Pointer(parent), null, null, null))
        check(hwnd != null) { "Could not create the VLC video window" }
        val handle = Pointer.nativeValue(hwnd)
        return nucleusHwndPlatformView(
            handle = { handle },
            onResize = { width, height -> if (width > 0 && height > 0) VlcPlayer.attachSurface(handle) },
            onDispose = {
                VlcPlayer.detachSurface(handle)
                user32.getFunction("DestroyWindow", Function.ALT_CONVENTION).invokeInt(arrayOf(hwnd))
            },
        )
    }
}
