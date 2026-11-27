package com.streakcounter.convention

import com.android.build.api.dsl.KotlinMultiplatformAndroidLibraryTarget
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

/**
 * Android + iOS targets for a KMP library module. The default hierarchy template is applied
 * automatically by KGP, so no manual dependsOn / applyDefaultHierarchyTemplate is needed.
 */
internal fun Project.configureKotlinMultiplatform() {
    extensions.configure<KotlinMultiplatformExtension> {
        // `kotlin { android { } }` from com.android.kotlin.multiplatform.library
        targets.withType(KotlinMultiplatformAndroidLibraryTarget::class.java).configureEach {
            namespace = pathToPackageName()
            compileSdk = intVersion("projectCompileSdkVersion")
            minSdk = intVersion("projectMinSdkVersion")

            // Library bytecode target stays low so consumers are not forced up.
            compilerOptions { jvmTarget.set(JvmTarget.JVM_11) }

            // Host (JVM) tests: commonTest + androidHostTest. Android resources are included so Robolectric
            // based Compose UI tests can load Compose Multiplatform resources (fonts, drawables).
            withHostTest {
                isIncludeAndroidResources = true
                // Plain JVM host tests run against stubbed android.jar classes. The Compose runtime
                // (also under AppState `transform` in ViewModel tests) calls android.os.Trace, which
                // would throw "not mocked"; return defaults instead. Robolectric tests are unaffected.
                isReturnDefaultValues = true
            }
            // Device tests: commonTest + androidDeviceTest
            withDeviceTest {
                instrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
            }
        }

        iosArm64()
        iosSimulatorArm64()

        jvmToolchain(21)

        compilerOptions {
            freeCompilerArgs.add("-Xexpect-actual-classes")
            freeCompilerArgs.add("-opt-in=kotlin.time.ExperimentalTime")
        }
    }
}
