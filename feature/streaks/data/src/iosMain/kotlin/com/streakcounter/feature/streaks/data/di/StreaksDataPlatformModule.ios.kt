package com.streakcounter.feature.streaks.data.di

import com.streakcounter.core.data.crypto.Aead
import com.streakcounter.core.data.crypto.KeychainAead
import com.streakcounter.core.data.datastore.dataStorePath
import com.streakcounter.feature.streaks.data.STREAK_FILE_NAME
import org.koin.core.module.Module
import org.koin.dsl.module

internal actual val streaksDataPlatformModule: Module = module {
    single<Aead> { KeychainAead() }
    // Application Support/datastore/streaks.enc, directory excluded from backup.
    single { StreakFileLocation(dataStorePath(STREAK_FILE_NAME)) }
}
