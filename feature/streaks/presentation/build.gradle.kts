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
            implementation(libs.kotlinx.datetime)
        }
        commonTest.dependencies {
            implementation(projects.core.testing)
            implementation(libs.appstate.transform.testing)
            implementation(libs.jetbrains.compose.ui.test)
        }
        // runComposeUiTest on the JVM host runs under Robolectric.
        getByName("androidHostTest").dependencies {
            implementation(libs.robolectric)
            implementation(project.dependencies.platform(libs.androidx.compose.bom))
            implementation(libs.androidx.compose.ui.test.manifest)
        }
        getByName("androidDeviceTest").dependencies {
            implementation(libs.androidx.test.runner)
            implementation(libs.androidx.test.ext.junit)
        }
    }
}
