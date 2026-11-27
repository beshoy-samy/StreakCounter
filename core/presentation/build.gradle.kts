plugins {
    alias(libs.plugins.convention.cmp.library)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(projects.core.domain)
            // LocalDate is part of the formatting API.
            api(libs.kotlinx.datetime)
        }
        // Formatting reads the ICU data of the Android framework, which Robolectric provides.
        getByName("androidHostTest").dependencies {
            implementation(libs.robolectric)
        }
    }
}
