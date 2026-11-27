import com.streakcounter.convention.configureKotlinMultiplatform
import com.streakcounter.convention.libs
import dev.whyoleg.cryptography.gradle.CryptographyExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies

class KmpLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            with(pluginManager) {
                apply("com.android.kotlin.multiplatform.library")
                apply("org.jetbrains.kotlin.multiplatform")
                apply("org.jetbrains.kotlin.plugin.serialization")
                apply("dev.whyoleg.cryptography")
            }

            // The CryptoKit provider's klib links Swift libs from a hard-coded /Applications/Xcode.app.
            // Every iOS binary may pull it in (composeApp's framework, and test binaries through
            // core:data / core:testing), so each one gets the -L path of the active Xcode (xcrun).
            extensions.configure<CryptographyExtension> { configureSwiftLinkerOpts.set(true) }

            configureKotlinMultiplatform()

            dependencies {
                "commonMainImplementation"(libs.findLibrary("kotlinx-serialization-json").get())
                "commonTestImplementation"(libs.findLibrary("kotlin-test").get())
                "commonTestImplementation"(libs.findLibrary("assertk").get())
                "commonTestImplementation"(libs.findLibrary("turbine").get())
                "commonTestImplementation"(libs.findLibrary("kotlinx-coroutines-test").get())
            }
        }
    }
}
