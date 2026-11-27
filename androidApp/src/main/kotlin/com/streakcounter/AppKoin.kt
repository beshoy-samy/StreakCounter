package com.streakcounter

import android.content.Context
import com.streakcounter.composeapp.initKoin
import com.streakcounter.feature.streaks.domain.WidgetUpdater
import com.streakcounter.widget.GlanceWidgetUpdater
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

/** Android-only bindings. Loaded after the common modules, so these replace their defaults. */
val androidAppModule = module {
    // Replaces the common NoOpWidgetUpdater (iOS has no widgets).
    single<WidgetUpdater> { GlanceWidgetUpdater(androidContext()) }
}

/** Starts Koin for the app process (activities, widgets, receivers). Safe to call twice. */
fun startAppKoin(context: Context) {
    initKoin {
        androidContext(context.applicationContext)
        modules(androidAppModule)
    }
}
