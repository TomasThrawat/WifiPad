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
include(":controller-app")
include(":receiver-app")

// Flutter generates this bridge project when pub dependencies are resolved.
val flutterModuleInclude = file("flutter_ui/.android/include_flutter.groovy")
if (!flutterModuleInclude.isFile) {
    throw GradleException("Flutter module is not bootstrapped. Run flutter pub get in flutter_ui.")
}
apply(from = flutterModuleInclude)
