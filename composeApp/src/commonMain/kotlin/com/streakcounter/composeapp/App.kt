package com.streakcounter.composeapp

import androidx.compose.runtime.Composable
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.streakcounter.core.designsystem.theme.StreakTheme
import com.streakcounter.core.domain.Today
import com.streakcounter.feature.streaks.presentation.list.StreakListRoot
import org.koin.compose.koinInject

@Composable
fun App() {
    val today = koinInject<Today>()
    // Coming back to the app may follow a midnight, time-zone or clock change nobody told us about.
    LifecycleEventEffect(Lifecycle.Event.ON_START) { today.refresh() }

    StreakTheme {
        StreakListRoot()
    }
}
