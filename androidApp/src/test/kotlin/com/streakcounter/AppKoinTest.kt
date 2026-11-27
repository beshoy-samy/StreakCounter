package com.streakcounter

import android.app.Application
import assertk.assertThat
import assertk.assertions.isEmpty
import assertk.assertions.isInstanceOf
import com.streakcounter.core.domain.Today
import com.streakcounter.feature.streaks.domain.StreakRepository
import com.streakcounter.feature.streaks.domain.WidgetUpdater
import com.streakcounter.widget.GlanceWidgetUpdater
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import org.koin.core.context.stopKoin
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import kotlin.concurrent.thread

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class AppKoinTest {
    @After
    fun tearDown() {
        stopKoin()
    }

    @Test
    fun start_app_koin_binds_the_glance_updater_over_the_common_no_op() {
        startAppKoin(RuntimeEnvironment.getApplication())

        assertThat(GlobalContext.get().get<WidgetUpdater>()).isInstanceOf<GlanceWidgetUpdater>()
    }

    /**
     * Widgets, receivers and the application can all start Koin at once (a Glance worker may run
     * before Application.onCreate). Every caller must return with the modules loaded, none may throw.
     */
    @Test
    fun concurrent_starts_start_koin_once_and_every_caller_sees_the_modules() {
        val app = RuntimeEnvironment.getApplication()
        repeat(ROUNDS) {
            stopKoin()
            val go = CountDownLatch(1)
            val errors = ConcurrentLinkedQueue<Throwable>()
            val callers = List(THREADS) {
                thread {
                    go.await()
                    try {
                        startAppKoin(app)
                        val koin = GlobalContext.get()
                        koin.get<WidgetUpdater>()
                        koin.get<StreakRepository>()
                        koin.get<Today>()
                    } catch (t: Throwable) {
                        errors += t
                    }
                }
            }
            go.countDown()
            callers.forEach { it.join() }
            assertThat(errors.map { it::class.simpleName }).isEmpty()
        }
    }

    private companion object {
        const val ROUNDS = 25
        const val THREADS = 8
    }
}
