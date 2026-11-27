plugins {
    alias(libs.plugins.convention.kmp.library)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(projects.core.domain)
            implementation(projects.core.data)
            implementation(projects.feature.streaks.domain)
            implementation(project.dependencies.platform(libs.koin.bom))
            implementation(libs.koin.core)
        }
        androidMain.dependencies {
            // androidContext() for the DataStore path
            implementation(libs.koin.android)
        }
        commonTest.dependencies {
            implementation(projects.core.testing)
            implementation(libs.okio.fakefilesystem)
        }
    }
}
