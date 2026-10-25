package me.xdan.aperture.domain.model

/** How a stream's picture fills the screen. */
enum class StreamPictureSize(val preferenceValue: String, val label: String) {
    FIT("fit", "Fit"),
    STRETCH("stretch", "Stretch"),
    ZOOM("zoom", "Zoom");

    val next: StreamPictureSize get() = entries[(ordinal + 1) % entries.size]

    companion object {
        fun fromPreference(value: String?): StreamPictureSize = entries.firstOrNull { it.preferenceValue == value } ?: FIT
    }
}

/** The app that plays live channels: CineK's own player, or one handed the stream. */
enum class LivePlayerApp(val preferenceValue: String, val label: String, val packages: List<String>) {
    CINEK("cinek", "CineK", emptyList()),
    MX_PLAYER("mx", "MX Player", listOf("com.mxtech.videoplayer.pro", "com.mxtech.videoplayer.ad")),
    VLC("vlc", "VLC", listOf("org.videolan.vlc"));

    val next: LivePlayerApp get() = entries[(ordinal + 1) % entries.size]

    companion object {
        fun fromPreference(value: String?): LivePlayerApp = entries.firstOrNull { it.preferenceValue == value } ?: CINEK
    }
}

/** Options for the Live TV and Open player, as opposed to the library player. */
data class StreamPlayerSettings(
    val livePlayerApp: LivePlayerApp = LivePlayerApp.CINEK,
    val pictureSize: StreamPictureSize = StreamPictureSize.FIT,
    val showClock: Boolean = false,
    /** Shows the channel's logo, number and name for a few seconds after each change. */
    val channelBanner: Boolean = true,
    /** Vertical swipes on a touchscreen: brightness on the left half, volume on the right. */
    val swipeGestures: Boolean = true,
    val confirmLiveExit: Boolean = false,
    /** Shared with the library player; for subtitle files the viewer adds. */
    val subtitleEncoding: String = SubtitleEncoding.AUTO
)
