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
        }
    }
}
