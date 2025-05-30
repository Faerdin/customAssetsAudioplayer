import java.util.Properties
import kotlin.io.use

pluginManagement {


    val flutterSdkPath: String by lazy {
        val properties = java.util.Properties()
        // Use rootProject.file() to correctly reference local.properties from the root
        rootDir.resolve("local.properties").inputStream().use { properties.load(it) }
        properties.getProperty("flutter.sdk") ?: error("flutter.sdk not found in local.properties")
    }

    // Set extra property for compatibility, if needed
    settings.extra["flutterSdkPath"] = flutterSdkPath
    println("flutterSdkPath: $flutterSdkPath")

    // This includes the Flutter Gradle build logic, which makes the Flutter plugin available
    includeBuild("$flutterSdkPath/packages/flutter_tools/gradle")


    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }

    plugins {
        //Eirik 15.05.25: Downgraded flutter from 3.29.2 to 3.22.2 due to error in flutter version
        //AGP 8.1.0 is compatible with Flutter 3.22.2
        //id("com.android.library") version "8.9.2"
        id("com.android.library") version "8.1.0"
        id("org.jetbrains.kotlin.android") version "1.9.22"
        //id("dev.flutter.flutter-gradle-plugin")
    }
}

dependencyResolutionManagement {
    //repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositoriesMode.set(RepositoriesMode.PREFER_SETTINGS)
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://download.flutter.io/maven") }
    }
}

include(":android")
