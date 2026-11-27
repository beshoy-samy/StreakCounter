package com.streakcounter.widget

import android.app.Application
import androidx.glance.GlanceId
import androidx.glance.action.actionParametersOf
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import com.streakcounter.core.domain.DataError
import com.streakcounter.core.domain.Today
import com.streakcounter.core.testing.FakeStreakRepository
import com.streakcounter.core.testing.FakeToday
import com.streakcounter.core.testing.FakeWidgetUpdater
import com.streakcounter.feature.streaks.domain.StreakCommand
import com.streakcounter.feature.streaks.domain.StreakRepository
import com.streakcounter.feature.streaks.domain.WidgetUpdater
import com.streakcounter.feature.streaks.domain.valueOn
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class StreakWidgetActionTest {
    private object TestGlanceId : GlanceId

    private val today = FakeToday(TODAY)
    private val gym = checkIn(count = 3, name = "Gym")
    private val since = daysSince()
    private val repository = FakeStreakRepository(today, listOf(gym, since.copy(position = 1)))
    private val updater = FakeWidgetUpdater()

    @Before
    fun setUp() {
        startKoin {
            modules(
                module {
                    single<StreakRepository> { repository }
                    single<Today> { today }
                    single<WidgetUpdater> { updater }
                },
            )
        }
    }

    @After
    fun tearDown() {
        stopKoin()
    }

    private suspend fun tap(id: String, op: String) = StreakWidgetAction().onAction(
        RuntimeEnvironment.getApplication(),
        TestGlanceId,
        actionParametersOf(StreakWidgetAction.streakIdKey to id, StreakWidgetAction.opKey to op),
    )

    @Test
    fun action_inc_increments_and_updates_widgets() = runTest {
        tap(gym.id.toString(), "inc")

        assertThat(repository.executed).containsExactly(StreakCommand.Increment(gym.id))
        assertThat(repository.current!!.first { it.id == gym.id }.valueOn(TODAY)).isEqualTo(4)
        assertThat(updater.updateAllCalls).isEqualTo(1)
    }

    @Test
    fun action_dec_decrements_and_updates_widgets() = runTest {
        tap(gym.id.toString(), "dec")

        assertThat(repository.executed).containsExactly(StreakCommand.Decrement(gym.id))
        assertThat(repository.current!!.first { it.id == gym.id }.valueOn(TODAY)).isEqualTo(2)
        assertThat(updater.updateAllCalls).isEqualTo(1)
    }

    @Test
    fun action_on_unreadable_does_not_write() = runTest {
        for (error in listOf(DataError.Local.UNREADABLE, DataError.Local.KEY_UNAVAILABLE, DataError.Local.UNKNOWN)) {
            repository.emitReadError(error)
            tap(gym.id.toString(), "inc")
        }

        assertThat(repository.executed).isEmpty()
        // Still redraws, so every widget shows "Open StreakCounter".
        assertThat(updater.updateAllCalls).isEqualTo(3)
    }

    @Test
    fun action_on_removed_streak_does_not_write() = runTest {
        repository.setStreaks(listOf(since))

        tap(gym.id.toString(), "inc")

        assertThat(repository.executed).isEmpty()
        assertThat(updater.updateAllCalls).isEqualTo(1)
    }

    @Test
    fun action_on_days_since_does_not_write() = runTest {
        tap(since.id.toString(), "inc")

        assertThat(repository.executed).isEmpty()
    }

    @Test
    fun malformed_parameters_do_nothing() = runTest {
        tap("not-a-uuid", "inc")
        tap(gym.id.toString(), "reset")

        assertThat(repository.executed).isEmpty()
    }
}
