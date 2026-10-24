package com.sasami.vlc.player

/** Pure queue policy, shared by automatic advance and the desktop controls. */
object PlaybackPolicy {
    fun nextIndex(size: Int, current: Int, repeat: String, automatic: Boolean): Int? {
        if (size == 0) return null
        if (current !in 0 until size) return 0
        if (automatic && repeat == "one") return current
        if (current + 1 < size) return current + 1
        return if (!automatic || repeat == "all") 0 else null
    }

    fun resumePosition(position: Long, duration: Long): Long =
        if (position >= 5000 && duration > 0 && position < duration - 5000) position else 0
}
