package com.streakcounter.feature.streaks.presentation.celebrate

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import com.streakcounter.core.designsystem.theme.StreakTheme
import com.streakcounter.feature.streaks.domain.CelebrationSize
import com.streakcounter.feature.streaks.domain.StreakType
import com.streakcounter.feature.streaks.presentation.UiTestBase
import com.streakcounter.feature.streaks.presentation.list.CelebrationUi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

@OptIn(ExperimentalTestApi::class)
class CelebrationOverlayUiTest : UiTestBase() {

    private fun ui(name: String?, value: Int = 30, type: StreakType = StreakType.CHECK_IN) =
        CelebrationUi(Uuid.parse("00000000-0000-0000-0000-000000000001"), CelebrationSize.BIG, value, name, type)

    private fun androidx.compose.ui.test.ComposeUiTest.show(
        ui: CelebrationUi,
        reducedMotion: Boolean = true,
        onKeepGoing: () -> Unit = {},
        onSetNewGoal: () -> Unit = {},
    ) = setContent {
        StreakTheme(darkTheme = false) {
            CelebrationOverlay(ui, reducedMotion, onKeepGoing, onSetNewGoal)
        }
    }

    private fun androidx.compose.ui.test.ComposeUiTest.animationProgress(): Float =
        onNodeWithTag(CELEBRATION_ANIMATION_TAG).fetchSemanticsNode().config[CelebrationProgress]

    @Test
    fun big_shows_heading_and_two_actions() = runComposeUiTest {
        var keepGoing = 0
        var newGoal = 0
        show(ui("No smoking"), onKeepGoing = { keepGoing++ }, onSetNewGoal = { newGoal++ })

        onNodeWithText("No smoking · 30 days").assertIsDisplayed()
        onNodeWithText("The count keeps going from here.").assertIsDisplayed()
        onNodeWithText("Keep going").performClick()
        onNodeWithText("Set a new goal").performClick()
        assertEquals(1, keepGoing)
        assertEquals(1, newGoal)
    }

    @Test
    fun times_heading_uses_times() = runComposeUiTest {
        show(ui("Pushups", value = 40, type = StreakType.TIMES))
        onNodeWithText("Pushups · 40 times").assertIsDisplayed()
    }

    @Test
    fun unnamed_heading_has_no_separator() = runComposeUiTest {
        show(ui(name = null))

        onNodeWithText("30 days").assertIsDisplayed()
        onNodeWithText("·", substring = true).assertDoesNotExist()
        assertTrue(onNodeWithText("30 days").fetchSemanticsNode().config[SemanticsProperties.Text].isNotEmpty())
    }

    @Test
    fun reduced_motion_does_not_play() = runComposeUiTest {
        mainClock.autoAdvance = false
        show(ui("No smoking"), reducedMotion = true)
        waitForIdle()
        assertEquals(1f, animationProgress())

        mainClock.advanceTimeBy(3_000)
        assertEquals(1f, animationProgress())
    }

    @Test
    fun motion_plays_once_and_rests_on_the_last_frame() = runComposeUiTest {
        mainClock.autoAdvance = false
        show(ui("No smoking"), reducedMotion = false)
        waitForIdle()
        assertTrue(animationProgress() < 1f)

        mainClock.advanceTimeBy(5_000)
        assertEquals(1f, animationProgress())
        onNodeWithText("Keep going").assertIsDisplayed()
    }
}
