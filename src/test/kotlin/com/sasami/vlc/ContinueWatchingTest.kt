package com.sasami.vlc

import com.sasami.vlc.store.PlayerItem
import com.sasami.vlc.store.WatchEntry
import com.sasami.vlc.store.continueWatchingItems
import kotlin.test.Test
import kotlin.test.assertEquals

class ContinueWatchingTest {
    @Test fun onlyResolvablePartialPlaybackAppearsInMostRecentOrder() {
        val items = listOf("older", "newer", "finished", "too-short", "unstarted")
            .map { PlayerItem(it, it, "video", 1) }
        val history = listOf(
            WatchEntry("older", 20_000, 60_000, 10),
            WatchEntry("finished", 58_000, 60_000, 50),
            WatchEntry("missing", 20_000, 60_000, 60),
            WatchEntry("too-short", 5_000, 10_000, 70),
            WatchEntry("newer", 12_000, 60_000, 30),
            WatchEntry("unstarted", 4_000, 60_000, 80)
        )

        assertEquals(listOf("newer", "older"), continueWatchingItems(items, history).map { it.first.path })
    }
}
