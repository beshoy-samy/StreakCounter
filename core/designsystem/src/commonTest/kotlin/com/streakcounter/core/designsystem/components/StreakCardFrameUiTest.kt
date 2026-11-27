package com.streakcounter.core.designsystem.components

import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import com.streakcounter.core.designsystem.UiTestBase
import com.streakcounter.core.designsystem.theme.StreakTheme
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class StreakCardFrameUiTest : UiTestBase() {
    @Composable
    private fun Card(
        progress: Float? = 0.5f,
        onCard: () -> Unit = {},
        onPlus: () -> Unit = {},
        onMinus: () -> Unit = {},
    ) {
        StreakTheme(darkTheme = false) {
            StreakCardFrame(progress = progress, onClick = onCard, onClickLabel = "Edit Gym") {
                Text("Gym")
                Row {
                    MinusButton(enabled = true, contentDescription = "Remove one", onClick = onMinus)
                    PlusButton(kind = PlusKind.CHECK_IN, label = "Check in", onClick = onPlus)
                }
            }
        }
    }

    @Test
    fun wholeCardIsClickableWithLabel() = runComposeUiTest {
        var clicks = 0
        setContent { Card(onCard = { clicks++ }) }

        // Top edge past the corner radius, inside the padding: nothing but the card is under the pointer.
        onNode(hasText("Gym")).performTouchInput { click(Offset(48f, 8f)) }
        assertEquals(1, clicks)
    }

    @Test
    fun cardClickCarriesAccessibilityLabel() = runComposeUiTest {
        setContent { Card() }
        val label = onNode(hasText("Gym")).fetchSemanticsNode().config
            .getOrNull(SemanticsActions.OnClick)?.label
        assertEquals("Edit Gym", label)
    }

    @Test
    fun buttonsHandleOwnClicksAboveTheCard() = runComposeUiTest {
        var card = 0
        var plus = 0
        var minus = 0
        setContent { Card(onCard = { card++ }, onPlus = { plus++ }, onMinus = { minus++ }) }

        onNodeWithText("Check in").performClick()
        onNodeWithContentDescription("Remove one").performClick()

        assertEquals(1, plus)
        assertEquals(1, minus)
        assertEquals(0, card)
    }

    @Test
    fun plusAndMinusMeetTouchTarget() = runComposeUiTest {
        setContent { Card() }
        onNodeWithContentDescription("Remove one").assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp)
        onNode(hasClickAction() and hasText("Check in")).assertHeightIsAtLeast(56.dp).assertWidthIsAtLeast(48.dp)
    }

    @Test
    fun rendersWithNullAndFullProgress() = runComposeUiTest {
        setContent { Card(progress = null) }
        onNodeWithText("Gym").assertExists()
    }

    @Test
    fun rendersWithFullProgress() = runComposeUiTest {
        setContent { Card(progress = 1f) }
        onNode(hasContentDescription("Remove one")).assertExists()
    }
}
