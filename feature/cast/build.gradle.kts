plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.jetbrains.kotlin.android)
}

android {
    namespace = "com.theveloper.pixelplay.feature.cast"
    compileSdk = 37

    defaultConfig {
        minSdk = 24
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
    api(project(":core:shared"))
    api(project(":feature:ktor-server"))
    
    // Cast SDK
    api(libs.google.play.services.cast.framework)
    api(libs.androidx.mediarouter)
    
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.timber)
    implementation(libs.okhttp)
    
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.exoplayer.ffmpeg)
    implementation(libs.androidx.media)
    
    // Hilt / Dagger
    implementation(libs.hilt.android)
    implementation("javax.inject:javax.inject:1")
}
