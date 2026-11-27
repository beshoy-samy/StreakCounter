package com.streakcounter.feature.streaks.presentation.di

import androidx.lifecycle.ViewModelStore
import assertk.assertThat
import assertk.assertions.isSameInstanceAs
import com.streakcounter.core.domain.Today
import com.streakcounter.core.testing.FakeStreakRepository
import com.streakcounter.core.testing.FakeToday
import com.streakcounter.core.testing.FakeWidgetUpdater
import com.streakcounter.feature.streaks.domain.StreakRepository
import com.streakcounter.feature.streaks.domain.WidgetUpdater
import com.streakcounter.feature.streaks.presentation.list.StreakListViewModel
import com.streakcounter.feature.streaks.presentation.session.StreakSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.LocalDate
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test

@OptIn(ExperimentalCoroutinesApi::class)
class StreaksPresentationModuleTest {

    @BeforeTest
    fun setUp() = Dispatchers.setMain(StandardTestDispatcher())

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun resolves_the_list_ViewModel_with_one_app_scoped_session() {
        val today = FakeToday(LocalDate(2026, 11, 27))
        val koin = koinApplication {
            modules(
                module {
                    single<Today> { today }
                    single<StreakRepository> { FakeStreakRepository(today) }
                    single<WidgetUpdater> { FakeWidgetUpdater() }
                },
                streaksPresentationModule,
            )
        }.koin

        val store = ViewModelStore()
        try {
            store.put("list", koin.get<StreakListViewModel>())
            assertThat(koin.get<StreakSession>()).isSameInstanceAs(koin.get<StreakSession>())
        } finally {
            store.clear()
            koin.close()
        }
    }
}
