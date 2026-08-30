import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }

android {
    namespace = "com.easyflow.keyboard"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.easyflow.keyboard"
        minSdk = 26
        targetSdk = 35
        versionCode = 14
        versionName = "0.12.0"
        ndk { abiFilters += listOf("arm64-v8a") }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin { compilerOptions { jvmTarget.set(JvmTarget.JVM_17) } }

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("ai.moonshine:moonshine-voice:0.1.3")
    implementation("com.google.ai.edge.litertlm:litertlm-android:0.16.1")
    testImplementation("junit:junit:4.13.2")
}
