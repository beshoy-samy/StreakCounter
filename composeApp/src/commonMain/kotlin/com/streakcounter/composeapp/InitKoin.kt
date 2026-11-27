package com.streakcounter.composeapp

import com.streakcounter.core.data.di.coreDataModule
import com.streakcounter.feature.streaks.data.di.streaksDataModule
import org.koin.core.context.startKoin
import org.koin.mp.KoinPlatformTools
import org.koin.dsl.KoinAppDeclaration

/**
 * Starts Koin once per process. Safe to call again: a second call does nothing instead of
 * throwing KoinApplicationAlreadyStartedException. iOS calls it from `iOSApp.init` (Swift).
 */
fun initKoin(config: KoinAppDeclaration? = null) {
    if (KoinPlatformTools.defaultContext().getOrNull() != null) return
    startKoin {
        config?.invoke(this)
        modules(
            coreDataModule,
            streaksDataModule,
        )
    }
}
