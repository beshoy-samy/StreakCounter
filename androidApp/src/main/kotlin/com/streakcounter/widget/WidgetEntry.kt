package com.streakcounter.widget

import android.content.Context
import com.streakcounter.core.domain.DataError
import com.streakcounter.core.domain.Result
import com.streakcounter.core.domain.Today
import com.streakcounter.feature.streaks.domain.Streak
import com.streakcounter.feature.streaks.domain.StreakRepository
import com.streakcounter.feature.streaks.domain.WidgetUpdater
import com.streakcounter.startAppKoin
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.update
import org.koin.core.context.GlobalContext

internal class WidgetDependencies(
    val repository: StreakRepository,
    val today: Today,
    val widgets: WidgetUpdater,
)

/**
 * Every widget entry point (Glance session, button callback, receiver) starts here. Glance runs
 * through WorkManager, which can start before Application.onCreate, so Koin is started first
 * (idempotent and thread-safe).
 */
internal fun widgetDependencies(context: Context): WidgetDependencies {
    startAppKoin(context)
    val koin = GlobalContext.get()
    return WidgetDependencies(koin.get(), koin.get(), koin.get())
}

/**
 * Bumped by [GlanceWidgetUpdater.updateAll]. A running widget session collects the repository
 * again on every bump: the repository's flow ends after an error, so without this a widget that
 * showed "Open StreakCounter" would keep showing it after the app fixed the problem.
 */
internal object WidgetRefreshSignal {
    private val _generation = MutableStateFlow(0L)
    val generation: StateFlow<Long> = _generation

    fun bump() = _generation.update { it + 1 }
}

/** The stored streaks for a widget, read again from the start on each of [refreshes]. */
@OptIn(ExperimentalCoroutinesApi::class)
internal fun widgetReads(
    repository: StreakRepository,
    refreshes: Flow<Long> = WidgetRefreshSignal.generation,
): Flow<Result<List<Streak>, DataError.Local>> = refreshes.flatMapLatest { repository.streaks }
