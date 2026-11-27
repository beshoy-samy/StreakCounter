package com.streakcounter.widget

import android.app.Application
import androidx.compose.ui.unit.DpSize
import androidx.glance.action.actionParametersOf
import androidx.glance.appwidget.testing.unit.runGlanceAppWidgetUnitTest
import androidx.glance.testing.unit.hasContentDescription
import androidx.glance.appwidget.testing.unit.hasRunCallbackClickAction
import androidx.glance.testing.unit.hasText
import androidx.glance.testing.unit.hasTextEqualTo
import com.streakcounter.core.domain.DataError
import com.streakcounter.core.domain.Result
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class SmallStreakWidgetTest {
    private val context get() = RuntimeEnvironment.getApplication()

    private fun render(ui: SmallWidgetUi, size: DpSize, assertions: androidx.glance.appwidget.testing.unit.GlanceAppWidgetUnitTest.() -> Unit) =
        runGlanceAppWidgetUnitTest {
            setContext(context)
            setAppWidgetSize(size)
            provideComposable { SmallWidgetContent(ui = ui, appWidgetId = 1) }
            assertions()
        }

    @Test
    fun checkIn_small_widget_has_plus_and_minus_that_run_the_action() {
        val gym = checkIn(count = 3)
        val ui = smallWidgetUi(Result.Success(listOf(gym)), gym.id.toString(), TODAY)
        render(ui, SmallStreakWidget.SIZE_2X1) {
            onNode(hasText("Read 20 pages")).assertExists()
            onNode(hasTextEqualTo("3")).assertExists()
            onNode(
                hasRunCallbackClickAction<StreakWidgetAction>(
                    actionParametersOf(StreakWidgetAction.streakIdKey to gym.id.toString(), StreakWidgetAction.opKey to "inc"),
                ),
            ).assertExists()
            onNode(
                hasRunCallbackClickAction<StreakWidgetAction>(
                    actionParametersOf(StreakWidgetAction.streakIdKey to gym.id.toString(), StreakWidgetAction.opKey to "dec"),
                ),
            ).assertExists()
        }
    }

    @Test
    fun daysSince_small_widget_has_no_buttons() {
        val since = daysSince(days = 18)
        val ui = smallWidgetUi(Result.Success(listOf(since)), since.id.toString(), TODAY)
        for (size in listOf(SmallStreakWidget.SIZE_2X1, SmallStreakWidget.SIZE_2X2)) {
            render(ui, size) {
                onNode(hasText("No smoking")).assertExists()
                onNode(hasTextEqualTo("18")).assertExists()
                onAllNodes(hasContentDescription("Add one")).assertCountEquals(0)
                onAllNodes(hasContentDescription("Take one")).assertCountEquals(0)
            }
        }
    }

    @Test
    fun deleted_streak_shows_pick_another() {
        val ui = smallWidgetUi(Result.Success(listOf(checkIn())), "6f1c2a7e-0000-4000-8000-000000000000", TODAY)
        render(ui, SmallStreakWidget.SIZE_2X1) {
            onNode(hasText("Streak removed. Tap to pick another.")).assertExists()
            onAllNodes(hasContentDescription("Add one")).assertCountEquals(0)
        }
    }

    @Test
    fun unreadable_shows_open_app_and_no_actions() {
        for (error in listOf(DataError.Local.UNREADABLE, DataError.Local.KEY_UNAVAILABLE, DataError.Local.UNKNOWN)) {
            val ui = smallWidgetUi(Result.Error(error), "6f1c2a7e-0000-4000-8000-000000000000", TODAY)
            render(ui, SmallStreakWidget.SIZE_2X2) {
                onNode(hasText("Open StreakCounter")).assertExists()
                onAllNodes(hasContentDescription("Add one")).assertCountEquals(0)
                onAllNodes(hasContentDescription("Take one")).assertCountEquals(0)
            }
        }
    }

    @Test
    fun reached_goal_shows_goal_reached() {
        val done = checkIn(count = 7, goal = 7)
        val ui = smallWidgetUi(Result.Success(listOf(done)), done.id.toString(), TODAY)
        render(ui, SmallStreakWidget.SIZE_2X2) {
            onNode(hasText("Goal reached")).assertExists()
            onNode(hasContentDescription("Goal reached")).assertExists()
        }
    }

    @Test
    fun list_widget_empty_shows_no_streaks_yet_and_open_app() = runGlanceAppWidgetUnitTest {
        setContext(context)
        setAppWidgetSize(DpSize(StreakListWidget.MIN_WIDTH, StreakListWidget.MIN_HEIGHT))
        provideComposable { ListWidgetContent(ui = listWidgetUi(Result.Success(emptyList()), TODAY)) }
        onNode(hasText("No streaks yet")).assertExists()
        onNode(hasText("Open StreakCounter")).assertExists()
    }

    @Test
    fun list_widget_days_since_row_has_no_buttons() = runGlanceAppWidgetUnitTest {
        setContext(context)
        setAppWidgetSize(DpSize(StreakListWidget.MIN_WIDTH, StreakListWidget.MIN_HEIGHT))
        val since = daysSince()
        val gym = checkIn(name = "Gym", position = 1)
        provideComposable { ListWidgetContent(ui = listWidgetUi(Result.Success(listOf(since, gym)), TODAY)) }
        onNode(hasText("No smoking")).assertExists()
        onAllNodes(hasContentDescription("Add one to No smoking")).assertCountEquals(0)
        onNode(hasContentDescription("Add one to Gym")).assertExists()
    }
}
