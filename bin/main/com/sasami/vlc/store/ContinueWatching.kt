package com.sasami.vlc.store

import com.sasami.vlc.player.PlaybackPolicy

/** Library items with a playable saved position, newest viewing first. */
fun continueWatchingItems(items: List<PlayerItem>, history: List<WatchEntry>): List<Pair<PlayerItem, WatchEntry>> {
    val byPath = items.associateBy { it.path }
    return history.asSequence()
        .sortedByDescending { it.playedAt }
        .distinctBy { it.path }
        .filter { PlaybackPolicy.resumePosition(it.position, it.duration) > 0 }
        .mapNotNull { entry -> byPath[entry.path]?.let { item -> item to entry } }
        .toList()
}
