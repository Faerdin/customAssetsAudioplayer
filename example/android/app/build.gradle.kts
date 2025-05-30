
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    // The Flutter Gradle Plugin must be applied after the Android and Kotlin Gradle plugins.
    //id("dev.flutter.flutter-gradle-plugin") TODO! TEMP!
}

// Access the kotlin_version defined in the project-level build.gradle.kts
// This line allows you to use 'kotlinVersion' as a variable if needed,
// but the Kotlin plugin itself should pick up the version for its tasks.
val kotlinVersion: String by rootProject.extra // ADD THIS LINE

android {
    namespace = "com.example.example"
    compileSdk = 33
    //ndkVersion = "27.0.12077973"
    //ndkVersion = flutter.ndkVersion
    //ndkVersion = "25.2.9519653" Needed???

    defaultConfig {
        applicationId = "com.example.example"
        minSdk = 33
        targetSdk = 33
        versionCode = project.findProperty("flutter.versionCode")?.toString()?.toInt() ?: 1
        versionName = project.findProperty("flutter.versionName")?.toString() ?: "1.0"
        //versionCode = flutter.versionCode
        //versionName = flutter.versionName

        // This placeholder is typically associated with older Flutter projects that used
        // io.flutter.app.FlutterApplication as the main application class in the
        // AndroidManifest.xml. For newer Flutter projects (generally those created after
        // Flutter 1.12 or that have been migrated), the default main entry point is
        // io.flutter.embedding.android.FlutterActivity, and this specific placeholder
        // for "applicationName" might not be necessary or might even be different if
        // you've customized your AndroidManifest.xml's <application android:name="...">
        // attribute. If your app works without it or uses a different name in the manifest,
        // you might be able to remove this line or adjust it. It doesn't usually cause
        // harm if left in, but it's a relic of older Flutter project templates.
        //manifestPlaceholders["applicationName"] = "io.flutter.app.FlutterApplication"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    kotlinOptions {
        //jvmTarget = JavaVersion.VERSION_1_8.toString()
        jvmTarget = "1.8"
    }
}

dependencies {
    //implementation("io.flutter:flutter_embedding_debug:1.0.0")
    implementation (project (":assets_audio_player")) // or your actual plugin module
    implementation ("org.jetbrains.kotlin:kotlin-stdlib-jdk7:$kotlinVersion")
}

/* TODO! TEMP!
flutter {
    source = "../.."
} */
