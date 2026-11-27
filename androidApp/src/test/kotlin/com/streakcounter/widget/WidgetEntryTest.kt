package com.streakcounter.widget

import android.app.Activity
import android.app.Application
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import com.streakcounter.core.domain.DataError
import com.streakcounter.core.domain.Result
import com.streakcounter.core.testing.FakeStreakRepository
import com.streakcounter.core.testing.FakeToday
import com.streakcounter.feature.streaks.domain.Streak
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import org.koin.core.context.stopKoin
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.IOException

/** Findings 3 to 6: widget entry points survive a missing Koin, failing receivers and stale errors. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class WidgetEntryTest {
    @After
    fun tearDown() {
        stopKoin()
    }

    // ---- Koin at widget entry points ----

    @Test
    fun widget_dependencies_start_koin_when_nothing_started_it_yet() {
        assertThat(GlobalContext.getOrNull()).isEqualTo(null)

        val deps = widgetDependencies(RuntimeEnvironment.getApplication())

        assertThat(deps.widgets).isInstanceOf<GlanceWidgetUpdater>()
    }

    // ---- Widget reads start again after an error (finding 6) ----

    @Test
    fun widget_reads_show_real_data_after_a_refresh_once_the_error_is_fixed() = runTest(UnconfinedTestDispatcher()) {
        val repository = FakeStreakRepository(FakeToday(TODAY), listOf(checkIn()))
        repository.emitReadError(DataError.Local.KEY_UNAVAILABLE)
        val refreshes = MutableStateFlow(0L)
        val shown = mutableListOf<ListWidgetUi>()
        backgroundScope.launch {
            widgetReads(repository, refreshes).map { listWidgetUi(it, TODAY) }.toList(shown)
        }
        assertThat(shown.last()).isEqualTo(ListWidgetUi.Unreadable)

        // Try again in the app reads the file fine; its widget refresh bumps the signal.
        val streak = checkIn(name = "Gym")
        repository.setStreaks(listOf(streak))
        assertThat(shown.last()).isEqualTo(ListWidgetUi.Unreadable)
        refreshes.value++

        assertThat(shown.last()).isEqualTo(ListWidgetUi.Shown(listOf(streak.toWidgetStreak(TODAY)), TODAY))
    }

    @Test
    fun widget_reads_follow_the_file_between_refreshes() = runTest(UnconfinedTestDispatcher()) {
        val first = checkIn(name = "Gym")
        val repository = FakeStreakRepository(FakeToday(TODAY), listOf(first))
        val reads = mutableListOf<Result<List<Streak>, DataError.Local>>()
        backgroundScope.launch { widgetReads(repository, MutableStateFlow(0L)).toList(reads) }

        val second = first.copy(count = 9)
        repository.setStreaks(listOf(second))

        assertThat(reads).containsExactly(Result.Success(listOf(first)), Result.Success(listOf(second)))
    }

    // ---- Midnight receiver (finding 3) ----

    @Test
    fun midnight_broadcast_finishes_when_the_refresh_and_the_rearm_throw() = runTest {
        var finished = 0

        handleMidnightBroadcast(
            refresh = { throw IllegalStateException("KoinApplication has not been started") },
            rearm = { throw SecurityException("alarm") },
            finish = { finished++ },
        )

        assertThat(finished).isEqualTo(1)
    }

    @Test
    fun midnight_broadcast_finishes_when_the_refresh_hangs() = runTest {
        var finished = 0
        var rearmed = 0

        handleMidnightBroadcast(refresh = { awaitCancellation() }, rearm = { rearmed++ }, finish = { finished++ })

        assertThat(rearmed).isEqualTo(1)
        assertThat(finished).isEqualTo(1)
    }

    // ---- Setup screen confirm (finding 4) ----

    @Test
    fun confirm_on_a_removed_widget_cancels() = runTest {
        assertThat(storeWidgetChoice { throw IllegalArgumentException("Invalid appWidgetId") })
            .isEqualTo(Activity.RESULT_CANCELED)
    }

    @Test
    fun confirm_that_cannot_write_the_widget_state_cancels() = runTest {
        assertThat(storeWidgetChoice { throw IOException("disk") }).isEqualTo(Activity.RESULT_CANCELED)
    }

    @Test
    fun confirm_that_stores_the_choice_is_ok() = runTest {
        var stored = false
        assertThat(storeWidgetChoice { stored = true }).isEqualTo(Activity.RESULT_OK)
        assertThat(stored).isEqualTo(true)
    }
}
