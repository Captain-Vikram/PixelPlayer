// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.jetbrains.kotlin.android) apply false
    alias(libs.plugins.aboutlibraries) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.dagger.hilt.android) apply false
    alias(libs.plugins.android.test) apply false
    alias(libs.plugins.baselineprofile) apply false
    alias(libs.plugins.kotlin.serialization) apply false
}

allprojects {
    configurations.all {
        resolutionStrategy {
            force("org.jetbrains.kotlin:kotlin-metadata-jvm:2.4.0")
            force("org.jetbrains.kotlinx:kotlinx-metadata-jvm:0.9.0")
        }
    }
}

// Workaround for AGP 8.x bug: check*Classpath tasks have a non-serializable
// 'compileVersionMap' input (Info objects not implementing Serializable),
// causing fingerprinting failures even without the configuration cache.
// See: https://issuetracker.google.com/issues/294893975
subprojects {
    afterEvaluate {
        tasks.matching { it.name.startsWith("check") && it.name.endsWith("Classpath") }.configureEach {
            enabled = false
        }
    }
}
