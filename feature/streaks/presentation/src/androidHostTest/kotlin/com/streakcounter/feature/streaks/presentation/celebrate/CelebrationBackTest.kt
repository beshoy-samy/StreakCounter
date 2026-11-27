package com.streakcounter.feature.streaks.presentation.celebrate

import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import com.streakcounter.core.designsystem.theme.StreakTheme
import com.streakcounter.feature.streaks.domain.CelebrationSize
import com.streakcounter.feature.streaks.domain.StreakType
import com.streakcounter.feature.streaks.presentation.UiTestBase
import com.streakcounter.feature.streaks.presentation.list.CelebrationUi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.uuid.Uuid

/** The system Back button (gesture or key) on the big celebration means "Keep going". */
@OptIn(ExperimentalTestApi::class)
class CelebrationBackTest : UiTestBase() {
    @Test
    fun back_press_keeps_going() = runComposeUiTest {
        var keepGoing = 0
        var dispatcherOwner: androidx.activity.OnBackPressedDispatcherOwner? = null
        setContent {
            dispatcherOwner = LocalOnBackPressedDispatcherOwner.current
            StreakTheme(darkTheme = false) {
                CelebrationOverlay(
                    ui = CelebrationUi(Uuid.random(), CelebrationSize.BIG, 30, "Gym", StreakType.CHECK_IN),
                    reducedMotion = true,
                    onKeepGoing = { keepGoing++ },
                    onSetNewGoal = {},
                )
            }
        }
        waitForIdle()

        runOnUiThread { dispatcherOwner!!.onBackPressedDispatcher.onBackPressed() }
        waitForIdle()

        assertEquals(1, keepGoing)
    }
}
