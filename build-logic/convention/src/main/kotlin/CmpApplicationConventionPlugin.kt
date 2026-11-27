import com.streakcounter.convention.configureIosFramework
import org.gradle.api.Plugin
import org.gradle.api.Project

/** Plugin for `:composeApp`: a KMP *library* (Gradle 9 / AGP 9) that ships the iOS framework. */
class CmpApplicationConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("com.streakcounter.convention.cmp.library")
            configureIosFramework()
        }
    }
}
