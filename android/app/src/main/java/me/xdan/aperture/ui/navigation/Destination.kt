package me.xdan.aperture.ui.navigation

import kotlinx.serialization.Serializable
import androidx.navigation3.runtime.NavKey

@Serializable
sealed interface Destination : NavKey {
    @Serializable
    data object Home : Destination

    @Serializable
    data object Search : Destination

    @Serializable
    data object Movies : Destination

    @Serializable
    data object Shows : Destination

    @Serializable
    data object MyList : Destination

    @Serializable
    data object Settings : Destination

    /** Open a file or stream, and check what the library can see. */
    @Serializable
    data object Open : Destination

    @Serializable
    data object LiveTv : Destination

    @Serializable
    data class Player(
        val mediaId: Long,
        val startFromBeginning: Boolean = false
    ) : Destination

    /** A file or stream from outside the library, by URI. */
    @Serializable
    data class ExternalPlayer(
        val uri: String,
        val title: String
    ) : Destination

    /**
     * A live channel, stepping through the rest of its playlist category. The list
     * itself stays in the channel repository; the backstack only saves the keys.
     */
    @Serializable
    data class LivePlayer(
        val sourceId: String,
        val category: String,
        val channelUrl: String,
        val channelName: String
    ) : Destination
}

/** Full-screen playback, which hides the drawer and holds off ambient mode. */
val Destination.isPlayback: Boolean
    get() = this is Destination.Player || this is Destination.ExternalPlayer || this is Destination.LivePlayer
