package com.streakcounter.widget

import android.app.Application
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import com.streakcounter.startAppKoin
import com.streakcounter.feature.streaks.domain.WidgetUpdater
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
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
    fun android_koin_binds_the_glance_updater_over_the_common_no_op() {
        startAppKoin(RuntimeEnvironment.getApplication())

        assertThat(GlobalContext.get().get<WidgetUpdater>()).isInstanceOf<GlanceWidgetUpdater>()
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
