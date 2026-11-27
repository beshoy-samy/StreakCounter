package com.streakcounter.feature.streaks.presentation.di

import androidx.appstate.statestore.StateStore
import com.streakcounter.feature.streaks.presentation.list.StreakListViewModel
import com.streakcounter.feature.streaks.presentation.session.StreakSession
import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module
import kotlin.time.Clock

val streaksPresentationModule = module {
    // One app-scoped store for session state (spec 6.4); only StreakSession reads it.
    single { StateStore() }
    singleOf(::StreakSession)
    single<Clock> { Clock.System }
    // Lambda form: viewModelOf can't skip the defaulted `dispatcher` parameter.
    viewModel { StreakListViewModel(get(), get(), get(), get(), get()) }
}
