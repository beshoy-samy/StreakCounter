plugins {
    alias(libs.plugins.convention.kmp.library)
}

// Shared test fixtures (fakes, builders). Consumed via commonTestImplementation(projects.core.testing).
kotlin {
    sourceSets {
        commonMain.dependencies {
            api(projects.core.domain)
            // FakeAead implements core:data's Aead.
            api(projects.core.data)
            // FakeStreakRepository, FakeWidgetUpdater implement the streaks domain interfaces.
            api(projects.feature.streaks.domain)
            api(libs.kotlin.test)
            api(libs.assertk)
            api(libs.turbine)
            api(libs.kotlinx.coroutines.test)
        }
    }
}
