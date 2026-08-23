plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }

android {
    namespace = "com.easyflow.keyboard"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.easyflow.keyboard"
        minSdk = 26
        targetSdk = 35
        versionCode = 5
        versionName = "0.5.0"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("ai.moonshine:moonshine-voice:0.1.3")
    testImplementation("junit:junit:4.13.2")
}
