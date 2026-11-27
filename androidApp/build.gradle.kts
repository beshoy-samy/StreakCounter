plugins {
    alias(libs.plugins.convention.android.application.compose)
}

android {
    testOptions {
        // Robolectric tests read the widget strings and drawables.
        unitTests.isIncludeAndroidResources = true
    }
}

dependencies {
    implementation(projects.composeApp)
    implementation(projects.core.domain)
    implementation(projects.core.designsystem)
    implementation(projects.core.presentation)
    implementation(projects.feature.streaks.domain)
    implementation(projects.feature.streaks.presentation)
    implementation(libs.androidx.activity.compose)
    implementation(libs.kotlinx.datetime)
    implementation(libs.glance.appwidget)
    implementation(platform(libs.koin.bom))
    implementation(libs.koin.android)

    testImplementation(projects.core.testing)
    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.glance.appwidget.testing)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.assertk)
}
