package me.xdan.aperture.ui.component.touch

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class TouchControlsTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun tvSurfaceIgnoresTouch() {
        // The reason this package exists: the TV component only listens for D-pad Enter.
        var clicks = 0
        compose.setContent {
            androidx.tv.material3.Surface(onClick = { clicks++ }, modifier = Modifier.testTag("tv").size(80.dp)) {}
        }
        compose.onNodeWithTag("tv").performTouchInput { click() }
        compose.runOnIdle { assertEquals(0, clicks) }
    }

    @Test
    fun surfaceButtonAndIconButtonRespondToTaps() {
        var surfaceClicks = 0
        var buttonClicks = 0
        var outlinedClicks = 0
        var iconClicks = 0
        compose.setContent {
            Column {
                Surface(onClick = { surfaceClicks++ }, modifier = Modifier.testTag("surface").size(80.dp)) {}
                Button(onClick = { buttonClicks++ }, modifier = Modifier.testTag("button")) { Text("Play") }
                OutlinedButton(onClick = { outlinedClicks++ }, modifier = Modifier.testTag("outlined")) { Text("Cancel") }
                IconButton(onClick = { iconClicks++ }, modifier = Modifier.testTag("icon")) {}
            }
        }
        listOf("surface", "button", "outlined", "icon").forEach { tag ->
            compose.onNodeWithTag(tag).performTouchInput { click() }
        }
        compose.runOnIdle {
            assertEquals(1, surfaceClicks)
            assertEquals(1, buttonClicks)
            assertEquals(1, outlinedClicks)
            assertEquals(1, iconClicks)
        }
    }

    @Test
    fun longPressCallsOnLongClickInsteadOfOnClick() {
        var clicks = 0
        var longClicks = 0
        compose.setContent {
            Box(
                Modifier
                    .testTag("card")
                    .size(80.dp)
                    .touchClickable(remember { MutableInteractionSource() }, onLongClick = { longClicks++ }) { clicks++ }
            )
        }
        compose.onNodeWithTag("card").performTouchInput { longClick() }
        compose.runOnIdle {
            assertEquals(1, longClicks)
            assertEquals(0, clicks)
        }
    }

    @Test
    fun touchSeekReportsTapAndDragPositions() {
        val fractions = mutableListOf<Float>()
        var starts = 0
        var ends = 0
        compose.setContent {
            Box(
                Modifier
                    .testTag("seek")
                    .size(width = 200.dp, height = 24.dp)
                    .touchSeek(onSeekStart = { starts++ }, onSeekEnd = { ends++ }) { fractions += it }
            )
        }
        compose.onNodeWithTag("seek").performTouchInput { click(percentOffset(0.75f, 0.5f)) }
        compose.runOnIdle {
            assertEquals(0.75f, fractions.last(), 0.02f)
            assertEquals(1, starts)
            assertEquals(1, ends)
        }

        compose.onNodeWithTag("seek").performTouchInput { swipeLeft() }
        compose.runOnIdle {
            assertTrue("drag should end near the left edge", fractions.last() < 0.1f)
            assertEquals(2, starts)
            assertEquals(2, ends)
        }
    }

    @Test
    fun verticalSwipeOnSeekBarScrollsInsteadOfSeeking() {
        var seeks = 0
        compose.setContent {
            Column(Modifier.testTag("list").height(200.dp).verticalScroll(rememberScrollState())) {
                Box(
                    Modifier
                        .testTag("seek")
                        .fillMaxWidth()
                        .height(48.dp)
                        .touchSeek { seeks++ }
                )
                Box(Modifier.height(600.dp))
            }
        }
        compose.onNodeWithTag("seek").performTouchInput { swipeUp() }
        compose.runOnIdle { assertEquals(0, seeks) }
    }
}
