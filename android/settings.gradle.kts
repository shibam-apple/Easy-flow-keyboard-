pluginManagement { repositories { google(); mavenCentral(); gradlePluginPortal() } }
dependencyResolutionManagement { repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS); repositories { google(); mavenCentral() } }
rootProject.name = "EasyFlow"
include(":app")

val whisperSource = file("third_party/whisper.cpp/examples/whisper.android/lib")
if (whisperSource.exists()) {
    include(":whisperlib")
    project(":whisperlib").projectDir = whisperSource
}
