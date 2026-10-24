package com.sasami.vlc.player

import java.io.File

/** VLC 3 Windows video output modules shipped with CineK. */
enum class VideoBackend(val key: String, val label: String, val module: String?, private val plugin: String?) {
    AUTOMATIC("auto", "Automatic", null, null),
    DIRECT3D11("direct3d11", "Direct3D 11", "direct3d11", "libdirect3d11_plugin.dll"),
    DIRECT3D9("direct3d9", "Direct3D 9", "direct3d9", "libdirect3d9_plugin.dll"),
    OPENGL("glwin32", "OpenGL", "glwin32", "libglwin32_plugin.dll");

    fun isAvailable(vlcDirectory: File): Boolean = plugin == null || File(vlcDirectory, "plugins/video_output/$plugin").isFile

    companion object {
        fun fromKey(key: String): VideoBackend = entries.firstOrNull { it.key == key } ?: AUTOMATIC
    }
}
