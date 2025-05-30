/*plugins {
    id("com.android.library") //apply false
    id("org.jetbrains.kotlin.android") //apply false
    id("dev.flutter.flutter-gradle-plugin") //apply false
}
*/
/*
buildscript {
    repositories {
        google()
        mavenCentral()
    }
    dependencies {
        classpath("com.android.tools.build:gradle:8.4.2")
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:1.9.22")
    }
}
*/

//tasks.register("clean", Delete::class) {
    //java.nio.file.Files.delete(rootProject.buildDir.toPath())
//    delete(rootProject.buildDir)
tasks.register<Delete>("clean") {
    delete(layout.buildDirectory)
}