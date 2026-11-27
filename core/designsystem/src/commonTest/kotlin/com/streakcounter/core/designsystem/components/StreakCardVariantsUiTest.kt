package com.streakcounter.core.designsystem.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.streakcounter.core.designsystem.UiTestBase
import com.streakcounter.core.designsystem.theme.LightPalette
import com.streakcounter.core.designsystem.theme.StreakTheme
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class StreakCardVariantsUiTest : UiTestBase() {
    private fun near(a: Color, b: Color) =
        abs(a.red - b.red) < 0.03f && abs(a.green - b.green) < 0.03f && abs(a.blue - b.blue) < 0.03f

    /** Colour of the card's top edge (stroke centre row) at [xDp] for a 300 dp wide card. */
    private fun topEdgePixels(direction: LayoutDirection, xs: List<Int>): List<Color> {
        var result: List<Color> = emptyList()
        runComposeUiTest {
            setContent {
                CompositionLocalProvider(LocalLayoutDirection provides direction) {
                    StreakTheme(darkTheme = false) {
                        StreakCardFrame(
                            progress = 0.15f,
                            onClick = {},
                            onClickLabel = "Edit Gym",
                            modifier = Modifier.width(300.dp).testTag("card"),
                        ) { Text("Gym") }
                    }
                }
            }
            val image = onNodeWithTag("card").captureToImage()
            val scale = image.width / 300f
            val map = image.toPixelMap()
            val y = (1.5f * scale).toInt()
            result = xs.map { map[(it * scale).toInt(), y] }
        }
        return result
    }

    @Test
    fun ltrProgressRunsFromTopStartRtlFromTopEnd() {
        val primary = LightPalette.primary
        val track = LightPalette.outlineVariant
        // 0.15 of the perimeter covers roughly x = 32..150 from the start corner; 80 is inside, 220 is not.
        val (ltrA, ltrB) = topEdgePixels(LayoutDirection.Ltr, listOf(80, 220))
        assertTrue(near(ltrA, primary), "LTR x=80 should be progress, was $ltrA")
        assertTrue(near(ltrB, track), "LTR x=220 should be track, was $ltrB")

        val (rtlA, rtlB) = topEdgePixels(LayoutDirection.Rtl, listOf(80, 220))
        assertTrue(near(rtlA, track), "RTL x=80 should be track, was $rtlA")
        assertTrue(near(rtlB, primary), "RTL x=220 should be progress, was $rtlB")
    }

    @Test
    fun quietPlusIsIconOnlyWithDescriptionAndTouchSize() = runComposeUiTest {
        var clicks = 0
        setContent { StreakTheme(darkTheme = false) { PlusButton(PlusKind.QUIET, "Add", { clicks++ }) } }
        val node = onNodeWithContentDescription("Add")
        node.assertHasClickAction().assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp)
        node.performClick()
        assertEquals(1, clicks)
    }

    @Test
    fun donePlusIsLabelledClickablePill() = runComposeUiTest {
        var clicks = 0
        setContent {
            StreakTheme(darkTheme = false) { Row { PlusButton(PlusKind.DONE, "Done", { clicks++ }) } }
        }
        val node = onNodeWithText("Done")
        node.assertHasClickAction().assertHeightIsAtLeast(56.dp)
        node.performClick()
        assertEquals(1, clicks)
    }
}
