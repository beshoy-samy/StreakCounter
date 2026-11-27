package com.streakcounter.widget

import android.app.Application
import assertk.assertThat
import assertk.assertions.isEqualTo
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.stopKoin
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class GlanceWidgetUpdaterTest {
    @After
    fun tearDown() {
        stopKoin()
    }

    @Test
    fun update_all_tells_running_widget_sessions_to_read_again() = runTest {
        val before = WidgetRefreshSignal.generation.value

        GlanceWidgetUpdater(RuntimeEnvironment.getApplication()).updateAll()

        assertThat(WidgetRefreshSignal.generation.value).isEqualTo(before + 1)
    }

    @Test
    fun a_failing_widget_update_is_swallowed_and_the_other_still_runs() = runTest {
        var listUpdated = 0
        val updater = GlanceWidgetUpdater(
            listOf(
                { throw IllegalStateException("Glance broke") },
                { listUpdated++ },
            ),
        )

        updater.updateAll()

        assertThat(listUpdated).isEqualTo(1)
    }
}
