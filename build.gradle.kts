buildscript {
    // The Android Gradle Plugin goes on the root classpath (next to the Kotlin plugin) so both
    // see each other. It is skipped with -Psmwc.coreOnly so the core module builds without Google's Maven.
    if (!project.hasProperty("smwc.coreOnly")) {
        repositories { google() }
        dependencies { classpath("com.android.tools.build:gradle:9.4.1") }
    }
}

plugins {
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.kotlin.compose) apply false
}
