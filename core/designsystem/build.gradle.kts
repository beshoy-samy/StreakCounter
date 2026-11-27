plugins {
    alias(libs.plugins.convention.cmp.library)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(libs.jetbrains.compose.ui.tooling.preview)
        }
        commonTest.dependencies {
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

// Feature modules use the shared icons (Res.drawable.ic_*) and fonts.
compose.resources {
    publicResClass = true
}
