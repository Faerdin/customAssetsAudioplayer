/*import org.gradle.kotlin.dsl.DependencyHandlerScope
import java.util.Properties
import kotlin.io.path.exists
import kotlin.io.path.toPath
import kotlin.io.resolve
import kotlin.io.use
*/
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }

    plugins {
        id("com.android.application") version "8.0.0" apply false
        id("com.android.library") version "8.0.0" apply false
        id("org.jetbrains.kotlin.android") version "1.9.22" apply false
        //id("dev.flutter.flutter-plugin-loader") version "1.0.0" apply false
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

include(":app")
/* Eirik 03.05.25: Removed this code as there's no ".flutter-plugins" file used in this project
val flutterProjectRoot = rootProject.projectDir.parentFile.toPath()

val plugins = Properties()
val pluginsFile = File(flutterProjectRoot.toFile(), ".flutter-plugins")
if (pluginsFile.exists()) {
    pluginsFile.reader(Charsets.UTF_8).use { reader ->
        plugins.load(reader)
    }
}

plugins.forEach { name, value ->
    try {
        val path = value.toString()
        val pluginDirectory = flutterProjectRoot.resolve(path).resolve("android").toFile()
        include(":$name")
        project(":$name").projectDir = pluginDirectory
    } catch (e: Exception) {
        println("Could not find external project: $name")
        throw e
    }
}
*/

//    val path = value.toString()
//    val pluginDirectory = flutterProjectRoot.resolve(path).resolve("android").toFile()
//    include(":$name")
//    project(":$name").projectDir = pluginDirectory