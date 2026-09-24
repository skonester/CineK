package com.sasami.vlc.utils

import com.sun.jna.NativeLibrary
import com.sun.jna.WString
import com.sun.jna.ptr.IntByReference
import java.io.File

/** Preserve Windows Unicode paths even when a packaged JVM launcher uses the system code page. */
object DesktopLaunchArguments {
    fun resolve(args: Array<String>): Array<String> {
        val executable = ProcessHandle.current().info().command().orElse("")
        if (!System.getProperty("os.name").startsWith("Windows") ||
            !File(executable).name.equals("CineK.exe", ignoreCase = true)) return args
        return runCatching {
            val command = NativeLibrary.getInstance("kernel32").getFunction("GetCommandLineW")
                .invokePointer(emptyArray()).getWideString(0)
            parseWindowsCommandLine(command).drop(1).toTypedArray()
        }.getOrElse { args }
    }

    internal fun parseWindowsCommandLine(command: String): List<String> {
        val count = IntByReference()
        val arguments = NativeLibrary.getInstance("shell32").getFunction("CommandLineToArgvW")
            .invokePointer(arrayOf(WString(command), count))
            ?: error("Windows could not parse the launch command")
        return try {
            arguments.getPointerArray(0, count.value).map { it.getWideString(0) }
        } finally {
            NativeLibrary.getInstance("kernel32").getFunction("LocalFree").invokePointer(arrayOf(arguments))
        }
    }
}
