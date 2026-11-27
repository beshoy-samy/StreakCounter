package com.streakcounter.core.data.di

import com.streakcounter.core.data.time.SystemToday
import com.streakcounter.core.domain.Today
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.datetime.TimeZone
import org.koin.dsl.module
import kotlin.time.Clock

val coreDataModule = module {
    // One app-scoped Today, alive for the whole process; UI and widgets share it.
    single<Today> {
        SystemToday(
            clock = Clock.System,
            timeZone = { TimeZone.currentSystemDefault() },
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
        )
    }
}
