plugins {
    alias(libs.plugins.convention.cmp.application)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(projects.core.domain)
            implementation(projects.core.data)
            implementation(projects.core.designsystem)
            implementation(projects.core.presentation)
            implementation(projects.feature.streaks.domain)
            implementation(projects.feature.streaks.data)
            implementation(projects.feature.streaks.presentation)
            implementation(project.dependencies.platform(libs.koin.bom))
            implementation(libs.koin.core)
            implementation(libs.koin.compose)
            // LocalLifecycleOwner for refreshing Today on ON_START.
            implementation(libs.jetbrains.lifecycle.compose)
        }
        androidMain.dependencies {
            implementation(libs.jetbrains.lifecycle.compose)
        }
    }
}
