plugins {
    alias(libs.plugins.convention.kmp.library)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(libs.kotlinx.coroutines.core)
            api(libs.kotlinx.datetime)
        }
        commonTest.dependencies {
            implementation(libs.appstate.statestore)
        }
    }
}
