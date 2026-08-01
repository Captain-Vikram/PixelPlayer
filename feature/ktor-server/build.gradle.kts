plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.jetbrains.kotlin.android)
}

android {
    namespace = "com.theveloper.pixelplay.feature.ktorserver"
    compileSdk = 37

    defaultConfig {
        minSdk = 30
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
    }
}

dependencies {
    api(project(":core:common"))
    
    api(libs.ktor.server.core)
    api(libs.ktor.server.cio)
    implementation(libs.timber)
    implementation(libs.okhttp)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.exoplayer.ffmpeg)
    implementation(libs.androidx.media)
    implementation(libs.androidx.core.ktx)
    implementation(libs.tdlib)
    implementation("javax.inject:javax.inject:1")
}
