package com.streakcounter.composeapp

import com.streakcounter.core.data.di.coreDataModule
import com.streakcounter.feature.streaks.data.di.streaksDataModule
import org.koin.core.context.startKoin
import org.koin.dsl.KoinAppDeclaration

/** Starts Koin once per process. Each platform entry point calls it exactly once. */
fun initKoin(config: KoinAppDeclaration? = null) {
    startKoin {
        config?.invoke(this)
        modules(
            coreDataModule,
            streaksDataModule,
        )
    }
}
