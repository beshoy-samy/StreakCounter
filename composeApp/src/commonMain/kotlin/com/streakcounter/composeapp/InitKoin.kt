package com.streakcounter.composeapp

import com.streakcounter.core.data.di.coreDataModule
import com.streakcounter.feature.streaks.data.di.streaksDataModule
import com.streakcounter.feature.streaks.presentation.di.streaksPresentationModule
import org.koin.core.context.startKoin
import org.koin.mp.KoinPlatformTools
import org.koin.mp.Lockable
import org.koin.dsl.KoinAppDeclaration

/**
 * Starts Koin once per process. Safe to call again and from several threads at once (Android
 * widgets, receivers and the application may race): a later call does nothing instead of throwing
 * KoinApplicationAlreadyStartedException, and returns only once the modules are loaded. iOS calls
 * it from `iOSApp.init` (Swift).
 *
 * [config] runs after the common modules are loaded, so modules it adds replace common bindings
 * (Android swaps the no-op WidgetUpdater for its Glance one).
 */
fun initKoin(config: KoinAppDeclaration? = null) {
    // Koin registers the application before it loads the modules, so a bare "already started?"
    // check could let a second caller through, or return before the bindings exist.
    KoinPlatformTools.synchronized(initLock) {
        if (KoinPlatformTools.defaultContext().getOrNull() != null) return@synchronized
        startKoin {
            modules(
                coreDataModule,
                streaksDataModule,
                streaksPresentationModule,
            )
            config?.invoke(this)
        }
    }
}

private val initLock = Lockable()
