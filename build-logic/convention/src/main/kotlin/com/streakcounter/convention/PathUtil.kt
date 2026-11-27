package com.streakcounter.convention

import org.gradle.api.Project

fun Project.pathToPackageName(): String {
    val relativePackageName = path
        .replace(':', '.')
        .replace('-', '_')
        .lowercase()
    return "com.streakcounter$relativePackageName"
}
