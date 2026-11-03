plugins {
    id("com.android.application") version "8.13.2"
    kotlin("android") version "2.3.0"
}
android {
    namespace = "com.kovelyx.nativecheck"
    compileSdk = 36
    defaultConfig {
        applicationId = "com.kovelyx.nativecheck"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }
dependencies {
    implementation(files("libs/kovelyx-engine-source-built.aar"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
}
