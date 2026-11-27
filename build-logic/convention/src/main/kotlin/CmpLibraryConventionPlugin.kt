import com.android.build.api.dsl.KotlinMultiplatformAndroidLibraryTarget
import com.streakcounter.convention.libs
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

class CmpLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            with(pluginManager) {
                apply("com.streakcounter.convention.kmp.library")
                apply("org.jetbrains.kotlin.plugin.compose")
                apply("org.jetbrains.compose")
            }

            // Compose resources reach Android as assets, which the KMP library plugin only packages
            // when Android resources are enabled.
            extensions.configure<KotlinMultiplatformExtension> {
                targets.withType(KotlinMultiplatformAndroidLibraryTarget::class.java).configureEach {
                    androidResources { enable = true }
                }
            }

            dependencies {
                "commonMainImplementation"(libs.findLibrary("jetbrains-compose-runtime").get())
                "commonMainImplementation"(libs.findLibrary("jetbrains-compose-ui").get())
                "commonMainImplementation"(libs.findLibrary("jetbrains-compose-foundation").get())
                "commonMainImplementation"(libs.findLibrary("jetbrains-compose-material3").get())
                "commonMainImplementation"(libs.findLibrary("jetbrains-compose-resources").get())

                // KMP Android library modules have no build types, so debugImplementation is
                // unavailable; androidRuntimeClasspath keeps @Preview tooling out of the API.
                "androidRuntimeClasspath"(libs.findLibrary("androidx-compose-ui-tooling").get())
            }
        }
    }
}
