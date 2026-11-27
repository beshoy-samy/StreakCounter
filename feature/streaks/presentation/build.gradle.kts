plugins {
    alias(libs.plugins.convention.cmp.feature)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(projects.core.domain)
            implementation(projects.feature.streaks.domain)
            // Owner requirement: screen and session state run on Jetpack AppState.
            implementation(libs.appstate.transform)
            implementation(libs.appstate.statestore)
        }
        commonTest.dependencies {
            implementation(projects.core.testing)
            implementation(libs.appstate.transform.testing)
        }
    }
}
