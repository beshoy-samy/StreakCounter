package com.streakcounter.composeapp

import androidx.compose.ui.window.ComposeUIViewController
import com.streakcounter.core.domain.Today
import org.koin.mp.KoinPlatform

// Koin and the observer live for the process, not for one view controller.
private val appStart: TimeChangeObserver by lazy {
    initKoin()
    TimeChangeObserver(KoinPlatform.getKoin().get<Today>()).also { it.start() }
}

fun MainViewController() = ComposeUIViewController {
    appStart
    App()
}
