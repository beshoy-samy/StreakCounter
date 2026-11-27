package com.streakcounter.feature.streaks.data.di

import com.streakcounter.core.data.crypto.Aead
import com.streakcounter.core.data.crypto.KeystoreAead
import com.streakcounter.core.data.datastore.dataStorePath
import com.streakcounter.feature.streaks.data.STREAK_FILE_NAME
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.dsl.module

internal actual val streaksDataPlatformModule: Module = module {
    single<Aead> { KeystoreAead() }
    // filesDir/datastore/streaks.enc, excluded from backup by the app's backup rules.
    single { StreakFileLocation(androidContext().dataStorePath(STREAK_FILE_NAME)) }
}
