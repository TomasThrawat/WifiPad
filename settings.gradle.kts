pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        val storageUrl = System.getenv("FLUTTER_STORAGE_BASE_URL") ?: "https://storage.googleapis.com"
        maven("$storageUrl/download.flutter.io")
    }
}
rootProject.name = "WifiPad"

// Flutter's source-module plugin expects one host project per Gradle build.
// CI selects either native app directory and maps it to the conventional :app path.
val selectedHostApp = providers.gradleProperty("wifipad.hostApp")
    .orElse("controller-app")
    .get()
if (selectedHostApp !in setOf("controller-app", "receiver-app")) {
    throw GradleException("Unsupported WifiPad host app: $selectedHostApp")
}
include(":app")
project(":app").projectDir = file(selectedHostApp)

// Flutter generates this bridge project when pub dependencies are resolved.
val flutterModuleInclude = File(settingsDir, "flutter_ui/.android/include_flutter.groovy")
if (!flutterModuleInclude.isFile) {
    throw GradleException(
        "Flutter module bootstrap is missing: \${flutterModuleInclude.absolutePath}. " +
            "Check flutter_ui/pubspec.yaml module metadata and run flutter pub get."
    )
}
apply(from = flutterModuleInclude)

