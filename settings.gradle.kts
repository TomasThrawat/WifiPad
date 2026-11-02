pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.PREFER_SETTINGS)
    val storageUrl = System.getenv("FLUTTER_STORAGE_BASE_URL") ?: "https://storage.googleapis.com"
    repositories {
        google()
        mavenCentral()
        maven("$storageUrl/download.flutter.io")
    }
}
rootProject.name = "WifiPad"

// CircleCI links the selected native host directory to ./app for each Gradle invocation.
include(":app")

// Flutter generates this bridge project when pub dependencies are resolved.
val flutterModuleInclude = File(settingsDir, "flutter_ui/.android/include_flutter.groovy")
if (!flutterModuleInclude.isFile) {
    throw GradleException(
        "Flutter module bootstrap is missing: \${flutterModuleInclude.absolutePath}. " +
            "Check flutter_ui/pubspec.yaml module metadata and run flutter pub get."
    )
}
apply(from = flutterModuleInclude)
