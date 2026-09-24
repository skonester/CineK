package com.sasami.vlc

import com.sasami.vlc.utils.DesktopLaunchArguments
import kotlin.test.Test
import kotlin.test.assertEquals

class DesktopLaunchArgumentsTest {
    @Test fun windowsQuotesAndUnicode() {
        if (!System.getProperty("os.name").startsWith("Windows")) return
        val executable = "C:\\Program Files\\CineK\\CineK.exe"
        val first = "C:\\Media\\Caf\u00e9 \u97f3\u697d #1.avi"
        val second = "C:\\Media\\Second file.M2TS"
        assertEquals(listOf(executable, first, second),
            DesktopLaunchArguments.parseWindowsCommandLine("\"$executable\" \"$first\" \"$second\""))
        assertEquals(listOf(executable), DesktopLaunchArguments.parseWindowsCommandLine("\"$executable\""))
    }
}
