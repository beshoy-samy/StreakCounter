package com.streakcounter.convention

import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

/** The `:composeApp` module is the only one that ships an iOS framework (hosted by `iosApp`). */
internal fun Project.configureIosFramework() {
    extensions.configure<KotlinMultiplatformExtension> {
        listOf(iosArm64(), iosSimulatorArm64()).forEach { iosTarget ->
            iosTarget.binaries.framework {
                baseName = "ComposeApp"
                isStatic = true
                binaryOption("bundleId", "com.streakcounter.composeapp")
            }
        }
    }
}
