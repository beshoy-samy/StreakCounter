package com.streakcounter.feature.streaks.data.di

import androidx.datastore.core.DataStore
import com.streakcounter.feature.streaks.data.DataStoreStreakRepository
import com.streakcounter.feature.streaks.data.NoOpWidgetUpdater
import com.streakcounter.feature.streaks.data.StreakFile
import com.streakcounter.feature.streaks.data.createStreakDataStore
import com.streakcounter.feature.streaks.domain.StreakRepository
import com.streakcounter.feature.streaks.domain.WidgetUpdater
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.SupervisorJob
import com.streakcounter.core.data.datastore.systemFileSystem
import okio.FileSystem
import okio.Path
import org.koin.core.module.Module
import org.koin.dsl.module

/** Where the streak file lives on this platform. */
internal class StreakFileLocation(val path: Path)

/** Provides the platform [com.streakcounter.core.data.crypto.Aead] and [StreakFileLocation]. */
internal expect val streaksDataPlatformModule: Module

val streaksDataModule = module {
    includes(streaksDataPlatformModule)

    // Exactly one DataStore per file, alive for the whole process (app and widgets share it).
    single<DataStore<StreakFile>> {
        createStreakDataStore(
            aead = get(),
            scope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
            path = get<StreakFileLocation>().path,
        )
    }
    single<StreakRepository> {
        DataStoreStreakRepository(
            dataStore = get(),
            path = get<StreakFileLocation>().path,
            fileSystem = systemFileSystem(),
            today = get(),
        )
    }
    // Android overrides this with the Glance updater.
    single<WidgetUpdater> { NoOpWidgetUpdater }
}
