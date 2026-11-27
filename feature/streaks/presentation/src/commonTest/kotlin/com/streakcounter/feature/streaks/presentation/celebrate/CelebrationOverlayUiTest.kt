package com.streakcounter.feature.streaks.presentation.celebrate

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.saveable.LocalSaveableStateRegistry
import androidx.compose.runtime.saveable.SaveableStateRegistry
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.unit.dp
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
    fun escape_means_keep_going() = runComposeUiTest {
        var keepGoing = 0
        show(ui("No smoking"), onKeepGoing = { keepGoing++ })
        waitForIdle()

        onRoot().performKeyInput { pressKey(Key.Escape) }

        assertEquals(1, keepGoing)
    }

    @Test
    fun card_is_a_labelled_pane() = runComposeUiTest {
        show(ui("No smoking"))
        waitForIdle()

        onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.PaneTitle)).assertExists()
        assertEquals(
            "No smoking · 30 days",
            onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.PaneTitle)).fetchSemanticsNode()
                .config[SemanticsProperties.PaneTitle],
        )
    }

    private fun androidx.compose.ui.test.ComposeUiTest.confetti(
        file: String,
        reduced: Boolean,
        key: String,
        tick: () -> Int = { 0 },
        onFinished: () -> Unit = {},
    ) = setContent {
        StreakTheme(darkTheme = false) {
            tick()
            LottieConfetti(
                file = file,
                durationMillis = 1_000,
                reducedMotion = reduced,
                playKey = key,
                modifier = Modifier.size(100.dp).testTag("c"),
                onFinished = onFinished,
            )
        }
    }

    private fun androidx.compose.ui.test.ComposeUiTest.progress() =
        onNodeWithTag("c").fetchSemanticsNode().config[CelebrationProgress]

    @Test
    fun motion_reaches_the_end_once_and_never_restarts_on_recomposition() = runComposeUiTest {
        mainClock.autoAdvance = false
        var recompose by mutableStateOf(0)
        var finished = 0
        confetti("files/celebrate_small.json", reduced = false, key = "k", tick = { recompose }, onFinished = { finished++ })
        waitForIdle()
        assertTrue(progress() < 1f)

        mainClock.advanceTimeBy(1_500)
        assertEquals(1f, progress())
        assertEquals(1, finished)

        // Recomposing (new lambda instances, state changes) must not play it again.
        recompose++
        mainClock.advanceTimeBy(200)
        recompose++
        mainClock.advanceTimeBy(2_000)
        assertEquals(1f, progress())
        assertEquals(1, finished)
    }

    @Test
    fun missing_file_shows_the_still_state_without_crashing() = runComposeUiTest {
        mainClock.autoAdvance = false
        var finished = 0
        confetti("files/does_not_exist.json", reduced = false, key = "missing", onFinished = { finished++ })
        waitForIdle()
        mainClock.advanceTimeBy(500)

        assertEquals(1f, progress())
        assertEquals(0, finished)
    }

    @Test
    fun finished_animation_is_not_replayed_after_state_restoration() = runComposeUiTest {
        mainClock.autoAdvance = false
        // Activity recreation: the saveable registry is saved, the composition is thrown away and
        // rebuilt from what was saved.
        var registry by mutableStateOf(SaveableStateRegistry(null) { true })
        var alive by mutableStateOf(true)
        var finished = 0
        setContent {
            CompositionLocalProvider(LocalSaveableStateRegistry provides registry) {
                if (alive) {
                    StreakTheme(darkTheme = false) {
                        LottieConfetti(
                            file = "files/celebrate_small.json",
                            durationMillis = 1_000,
                            reducedMotion = false,
                            playKey = "rot",
                            modifier = Modifier.size(100.dp).testTag("c"),
                            onFinished = { finished++ },
                        )
                    }
                }
            }
        }
        waitForIdle()
        mainClock.advanceTimeBy(1_500)
        assertEquals(1f, progress())

        val saved = registry.performSave()
        alive = false
        waitForIdle()
        registry = SaveableStateRegistry(saved) { true }
        alive = true
        waitForIdle()

        assertEquals(1f, progress())
        mainClock.advanceTimeBy(2_000)
        assertEquals(1f, progress())
        assertEquals(1, finished)
    }

    @Test
    fun motion_plays_and_rests_on_the_last_frame() = runComposeUiTest {
        mainClock.autoAdvance = false
        show(ui("No smoking"), reducedMotion = false)
        waitForIdle()
        assertTrue(animationProgress() < 1f)

        mainClock.advanceTimeBy(5_000)
        assertEquals(1f, animationProgress())
        onNodeWithText("Keep going").assertIsDisplayed()
    }
}
