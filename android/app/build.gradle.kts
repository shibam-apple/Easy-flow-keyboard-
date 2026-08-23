plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }

android {
    namespace = "com.easyflow.keyboard"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.easyflow.keyboard"
        minSdk = 26
        targetSdk = 35
        versionCode = 2
        versionName = "0.2.0"
        // Pin the exact upstream revision so the verified model cannot change underneath the app.
        buildConfigField("String", "WHISPER_MODEL_URL", "\"https://huggingface.co/ggerganov/whisper.cpp/resolve/f281eb45af861ab5e5297d23694b7d46e090c02c/ggml-base.en-q5_1.bin\"")
        buildConfigField("String", "WHISPER_MODEL_SHA256", "\"4baf70dd0d7c4247ba2b81fafd9c01005ac77c2f9ef064e00dcf195d0e2fdd2f\"")
    }
    buildFeatures { buildConfig = true }
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
    if (project.findProject(":whisperlib") != null) implementation(project(":whisperlib"))
    testImplementation("junit:junit:4.13.2")
}
