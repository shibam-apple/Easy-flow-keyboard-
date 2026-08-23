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
        buildConfigField("String", "WHISPER_MODEL_URL", "\"https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-base.en-q5_1.bin\"")
        buildConfigField("String", "WHISPER_MODEL_SHA256", "\"323473b7c41bfb7fb994c1e9526abdcc7c55d3a909c8fa0c29f753005e87d372\"")
    }
    buildFeatures { buildConfig = true }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    if (project.findProject(":whisperlib") != null) implementation(project(":whisperlib"))
    testImplementation("junit:junit:4.13.2")
}
