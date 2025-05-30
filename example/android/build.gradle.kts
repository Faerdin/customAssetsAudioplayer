
buildscript {
    //val kotlinVersion by extra("1.9.22")
    //val kotlinVersion by extra("1.8.22")
    val kotlinVersion by extra("1.8.20")


    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }

    dependencies {
        classpath("com.android.tools.build:gradle:7.4.2") // Ensure this matches settings.gradle.kts
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:$kotlinVersion")
    }
}
/*
allprojects {
    repositories {
        google()
        mavenCentral()
    }
}*/

/*
val newBuildDir: Directory = rootProject.layout.buildDirectory.dir("../../build").get()
rootProject.layout.buildDirectory.value(newBuildDir)

subprojects {
    val newSubprojectBuildDir: Directory = newBuildDir.dir(project.name)
    project.layout.buildDirectory.value(newSubprojectBuildDir)
}
subprojects {
    project.evaluationDependsOn(":app")
}
*/
tasks.register<Delete>("clean") {
    delete(rootProject.layout.buildDirectory)
}
