package com.streakcounter.composeapp

import androidx.compose.ui.window.ComposeUIViewController
import com.streakcounter.core.domain.Today
import org.koin.mp.KoinPlatform

// Lives for the process, not for one view controller. Koin is already started by iOSApp.init.
private val timeChangeObserver: TimeChangeObserver by lazy {
    TimeChangeObserver(KoinPlatform.getKoin().get<Today>()).also { it.start() }
}

fun MainViewController() = ComposeUIViewController {
    timeChangeObserver
    App()
}
