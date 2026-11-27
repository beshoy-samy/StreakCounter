plugins {
    `kotlin-dsl`
}

group = "com.streakcounter.buildlogic"

dependencies {
    compileOnly(libs.android.gradlePlugin)
    compileOnly(libs.kotlin.gradlePlugin)
    compileOnly(libs.compose.compiler.gradlePlugin)
    compileOnly(libs.compose.multiplatform.gradlePlugin)
    compileOnly(libs.cryptography.gradlePlugin)
}

gradlePlugin {
    plugins {
        register("androidApplication") {
            id = "com.streakcounter.convention.android.application"
            implementationClass = "AndroidApplicationConventionPlugin"
        }
        register("androidApplicationCompose") {
            id = "com.streakcounter.convention.android.application.compose"
            implementationClass = "AndroidApplicationComposeConventionPlugin"
        }
        register("cmpApplication") {
            id = "com.streakcounter.convention.cmp.application"
            implementationClass = "CmpApplicationConventionPlugin"
        }
        register("kmpLibrary") {
            id = "com.streakcounter.convention.kmp.library"
            implementationClass = "KmpLibraryConventionPlugin"
        }
        register("cmpLibrary") {
            id = "com.streakcounter.convention.cmp.library"
            implementationClass = "CmpLibraryConventionPlugin"
        }
        register("cmpFeature") {
            id = "com.streakcounter.convention.cmp.feature"
            implementationClass = "CmpFeatureConventionPlugin"
        }
    }
}

kotlin {
    jvmToolchain(21)
}
