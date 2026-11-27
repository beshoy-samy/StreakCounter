import java.time.Duration
import org.jetbrains.kotlin.gradle.targets.native.tasks.KotlinNativeSimulatorTest
import org.jetbrains.kotlin.gradle.tasks.KotlinNativeLink
import org.jetbrains.kotlin.konan.target.HostManager

plugins {
    alias(libs.plugins.convention.kmp.library)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(projects.core.domain)
            // EncryptingSerializer implements OkioSerializer, so DataStore is part of this module's API.
            api(libs.datastore.core.okio)
        }
        commonTest.dependencies {
            implementation(projects.core.testing)
        }
        iosMain.dependencies {
            // AES-GCM on Apple is only available through CryptoKit (CommonCrypto has no public GCM API).
            implementation(libs.cryptography.core)
            implementation(libs.cryptography.provider.cryptokit)
        }
        getByName("androidDeviceTest").dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.assertk)
            implementation(libs.androidx.test.runner)
            implementation(libs.androidx.test.ext.junit)
        }
    }
}

// Real-Keychain tests (KeychainAeadTest) need an installed simulator app with entitlements, which
// the plain Kotlin/Native test runner cannot provide. They are excluded from iosSimulatorArm64Test
// and run by iosSimulatorKeychainTest instead (see ios-keychain-test/run.sh). Both are in allTests.
val keychainTests = "com.streakcounter.core.data.crypto.KeychainAeadTest"

kotlin {
    iosSimulatorArm64 {
        // Embed simulator entitlements the way Xcode does for simulator builds. Test binary only.
        binaries.getTest("DEBUG").linkerOpts(
            "-sectcreate", "__TEXT", "__entitlements",
            file("ios-keychain-test/test.entitlements").absolutePath,
        )
    }
}

val iosSimulatorTest = tasks.named<KotlinNativeSimulatorTest>("iosSimulatorArm64Test") {
    filter.excludeTestsMatching(keychainTests)
}

val iosSimulatorKeychainTest = tasks.register<Exec>("iosSimulatorKeychainTest") {
    group = "verification"
    description = "Runs the real-Keychain iOS tests inside a simulator app bundle."
    // Needs Xcode tools: on other hosts KGP skips the iOS link, so there is nothing to run.
    onlyIf { HostManager.hostIsMac }
    // KGP's simulator test boots and shuts down the same device: never overlap with it.
    mustRunAfter(iosSimulatorTest)
    // simctl launch blocks until the app exits; a hung test must not hang the build.
    timeout.set(Duration.ofMinutes(5))
    val link = tasks.named<KotlinNativeLink>("linkDebugTestIosSimulatorArm64")
    dependsOn(link)
    // Plain values and providers only, so the configuration cache can store the task.
    val script = file("ios-keychain-test/run.sh").absolutePath
    val kexe = link.flatMap { it.outputFile }
    val device = iosSimulatorTest.flatMap { it.device }
    val workDir = layout.buildDirectory.dir("ios-keychain-test")
    val filter = "$keychainTests.*"
    inputs.file(kexe)
    executable = "sh"
    argumentProviders.add(
        CommandLineArgumentProvider {
            listOf(script, kexe.get().absolutePath, device.get(), workDir.get().asFile.absolutePath, filter)
        }
    )
}

tasks.named("allTests") { dependsOn(iosSimulatorKeychainTest) }
