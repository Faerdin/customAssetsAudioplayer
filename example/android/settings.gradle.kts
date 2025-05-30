//import kotlin.io.path.name
import org.gradle.api.initialization.resolve.RepositoriesMode
import org.gradle.api.plugins.ExtensionAware
import org.gradle.api.initialization.Settings // Crucial for using 'settings' to set extra properties
import java.io.File
import java.util.Properties

// This top-level variable will be set from within pluginManagement for use elsewhere
// We declare it here, but it will be assigned later.
// Note: It might be better to solely rely on settings.extra if this feels awkward.
// lateinit var currentProjectFlutterSdkPath: String

pluginManagement {
    // Define the function directly inside the pluginManagement block
    fun resolveFlutterSdkPathForPluginManagement(settings: Settings): String { // Pass 'settings'
        val properties = java.util.Properties()

        // Use settings.settingsDir to correctly locate local.properties relative to settings.gradle.kts
        val settingsDirectory = settings.settingsDir
        // Path resolution logic, assuming settings.gradle.kts is in 'example/android'
        //val scriptLocationParentDir = File(".").absoluteFile.parentFile // e.g., example/android
        var localPropertiesFile = File(settingsDirectory, "local.properties")

        if (!localPropertiesFile.exists()) {
            // Check in the parent of the settings directory (e.g., project root if settings is in android/)
            val projectRoot = settingsDirectory.parentFile
            if (projectRoot != null) {
                localPropertiesFile = File(projectRoot, "local.properties")
                if (!localPropertiesFile.exists()) {
                    // Specific fallback for Flutter plugin example structure:
                    // If settings.gradle.kts is in example/android,
                    // local.properties might be in example/ (projectRoot) or assets_audio_player/ (projectRoot.parentFile)
                    val pluginRoot = projectRoot.parentFile
                    if (pluginRoot != null) {
                         localPropertiesFile = File(pluginRoot, "local.properties")
                    }
                }
            }
        }

        if (localPropertiesFile.exists()) {
            try {
                localPropertiesFile.inputStream().use { properties.load(it) }
            } catch (e: Exception) {
                throw IllegalStateException("Failed to read $localPropertiesFile: ${e.message}", e)
            }
        }

        val sdkPath = properties.getProperty("flutter.sdk")
        requireNotNull(sdkPath) {
            """
            flutter.sdk not set in local.properties.
            Resolution attempt starting from: ${settingsDirectory.absolutePath}
            Tried:
            1. ${File(settingsDirectory, "local.properties").absolutePath}
            2. ${settingsDirectory.parentFile?.resolve("local.properties")?.absolutePath ?: "N/A (no parent for settingsDir)"}
            3. ${settingsDirectory.parentFile?.parentFile?.resolve("local.properties")?.absolutePath ?: "N/A (no grandparent for settingsDir)"}
            Please ensure flutter.sdk is defined in a discoverable local.properties.
            """.trimIndent()
        }
        return sdkPath
    }

    // 'settings' here is the PluginManagementSpec's settings, which has access to settingsDir
    val sdkPathForIncludeBuild = resolveFlutterSdkPathForPluginManagement(settings)

    // Now, make this path available to the outer 'settings' scope via extra properties
    // The 'settings' object here is the PluginManagementSpec's settings,
    // which should allow setting extra properties on the root Settings object.
    (settings as ExtensionAware).extensions.extraProperties["flutterSdkPath"] = sdkPathForIncludeBuild
    // If you had the lateinit var above, you could try:
    // if (::currentProjectFlutterSdkPath.isInitialized.not()) { // Check needed if script re-evaluated
    //     (settings.rootProject.extraProperties.get("currentProjectFlutterSdkPath") as? String)?.let {
    //          // This is getting complicated, settings.extra is better
    //     }
    // }
    // currentProjectFlutterSdkPath = sdkPathForIncludeBuild // This won't work due to scope

    includeBuild(File(sdkPathForIncludeBuild, "packages/flutter_tools/gradle"))

    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }

    plugins {
        id("com.android.application") version "7.4.2" apply false
        id("com.android.library") version "7.4.2" apply false
        id("org.jetbrains.kotlin.android") version "1.8.20" apply false
    }
}
// ... rest of your file (dependencyResolutionManagement, etc.) using settings.extra["flutterSdkPath"]
dependencyResolutionManagement {
    //repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS) // Good practice
    repositoriesMode.set(RepositoriesMode.PREFER_SETTINGS) // TODO! For testing now
    repositories {
        google()        // For Android libraries
        mavenCentral()  // For general libraries

        // Attempt to add the repository Flutter uses
        maven {
            name = "FlutterEngine"
            // Retrieve from extra properties, which should have been set by pluginManagement
            val sdkRepoPath = settings.extra["flutterSdkPath"] as String?
            requireNotNull(sdkRepoPath) { "flutterSdkPath not found in settings.extra for dependencyResolutionManagement!" }
            url = uri("$sdkRepoPath/bin/cache/artifacts/engine/")
        }
    }
}

rootProject.name = "assets_audio_player_example"
include(":app")
// This is the crucial part for including your plugin:
include(":assets_audio_player")
project(":assets_audio_player").projectDir = File("../../android")
