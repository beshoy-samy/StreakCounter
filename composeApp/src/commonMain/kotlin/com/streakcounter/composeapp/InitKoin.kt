package com.streakcounter.composeapp

import com.streakcounter.core.data.di.coreDataModule
import com.streakcounter.feature.streaks.data.di.streaksDataModule
import com.streakcounter.feature.streaks.presentation.di.streaksPresentationModule
import org.koin.core.context.startKoin
import org.koin.mp.KoinPlatformTools
import org.koin.dsl.KoinAppDeclaration

/**
 * Starts Koin once per process. Safe to call again: a second call does nothing instead of
 * throwing KoinApplicationAlreadyStartedException. iOS calls it from `iOSApp.init` (Swift).
 *
 * [config] runs after the common modules are loaded, so modules it adds replace common bindings
 * (Android swaps the no-op WidgetUpdater for its Glance one).
 */
fun initKoin(config: KoinAppDeclaration? = null) {
    if (KoinPlatformTools.defaultContext().getOrNull() != null) return
    startKoin {
        modules(
            coreDataModule,
            streaksDataModule,
            streaksPresentationModule,
        )
        config?.invoke(this)
    }
}
