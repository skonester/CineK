package me.xdan.aperture.ui.screen.player

import android.view.MotionEvent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import me.xdan.aperture.domain.model.StreamPlayerSettings
import me.xdan.aperture.domain.repository.UserPreferencesRepository
import javax.inject.Inject
import kotlin.math.abs

/** The Live TV and Open player's saved options. */
@HiltViewModel
class StreamPlayerViewModel @Inject constructor(
    private val preferences: UserPreferencesRepository
) : ViewModel() {
    /** Null until the saved settings are read, so a player doesn't start with the wrong ones. */
    val settings: StateFlow<StreamPlayerSettings?> = preferences.streamPlayerSettings
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    fun cyclePictureSize() {
        val current = settings.value ?: return
        viewModelScope.launch { preferences.setStreamPictureSize(current.pictureSize.next) }
    }
}

/**
 * Limits automatic reconnects to [limit] within [windowMs], so a channel that has
 * really gone offline ends in an error instead of retrying forever.
 */
class ReconnectBudget(private val limit: Int = 3, private val windowMs: Long = 120_000) {
    private val attempts = ArrayDeque<Long>()

    fun tryAcquire(now: Long): Boolean {
        while (attempts.isNotEmpty() && now - attempts.first() > windowMs) attempts.removeFirst()
        if (attempts.size >= limit) return false
        attempts.addLast(now)
        return true
    }

    fun reset() = attempts.clear()
}

enum class SwipeTarget { BRIGHTNESS, VOLUME }

/**
 * Turns vertical swipes over the video into brightness (left half) and volume
 * (right half) changes, from 0 to 1. Swipes that start near the top or bottom are
 * left alone, so the system gesture areas and the controller's seek bar still work.
 * Feed it every touch event; once it returns true it owns the rest of the gesture.
 */
class SwipeAdjuster(
    private val touchSlop: Int,
    private val currentValue: (SwipeTarget) -> Float,
    private val onAdjust: (SwipeTarget, Float) -> Unit
) {
    var enabled = true
    private var downX = 0f
    private var downY = 0f
    private var eligible = false
    private var startValue = 0f
    var target: SwipeTarget? = null
        private set

    fun onTouch(event: MotionEvent, width: Int, height: Int): Boolean = when (event.actionMasked) {
        MotionEvent.ACTION_DOWN -> {
            downX = event.x
            downY = event.y
            target = null
            eligible = enabled && height > 0 && event.y > height * EdgeFraction && event.y < height * (1 - BottomFraction)
            false
        }
        MotionEvent.ACTION_MOVE -> {
            val active = target
            if (active == null) {
                val dy = event.y - downY
                if (eligible && abs(dy) > touchSlop * 2 && abs(dy) > abs(event.x - downX) * 2) {
                    val started = if (downX < width / 2f) SwipeTarget.BRIGHTNESS else SwipeTarget.VOLUME
                    target = started
                    startValue = currentValue(started)
                    downY = event.y
                    true
                } else false
            } else {
                // A swipe across most of the screen's height covers the whole range.
                val value = (startValue + (downY - event.y) / (height * TravelFraction)).coerceIn(0f, 1f)
                onAdjust(active, value)
                true
            }
        }
        MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> (target != null).also { target = null; eligible = false }
        else -> target != null
    }

    private companion object {
        const val EdgeFraction = 0.1f
        const val BottomFraction = 0.25f
        const val TravelFraction = 0.7f
    }
}
