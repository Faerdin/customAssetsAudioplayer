//import com.android.build.api.dsl.Packaging

plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("dev.flutter.flutter-gradle-plugin")
}

group = "com.github.florent37.assetsaudioplayer"
version = "1.0-SNAPSHOT"

android {
    namespace = "com.github.florent37.assets_audio_player"
    compileSdk = 33
    ndkVersion = "27.0.12077973"

    extensions.findByName("flutter")?.let { flutterExtension ->
    }

    defaultConfig {
        minSdk = 33
        multiDexEnabled = true
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    //compileSdkVersion(flutter.compileSdkVersion)
    //ndkVersion = flutter.ndkVersion

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    //Eirik 16.03.25: Added to set Kotlin compilation to same as Java
    kotlinOptions {
        //jvmTarget = JavaVersion.VERSION_1_8.toString()
        jvmTarget = "1.8"
    }

    sourceSets {
        getByName("main") {
            java.srcDirs("src/main/kotlin")
        }
    }

    lint {
        disable += "InvalidPackage"
    }
    //Eirik 23.05.25: Changed for temporary downgrade of AGP
    //packaging {
    packagingOptions {
        resources.excludes += "DebugProbesKt.bin"
    }


    //compileSdkVersion(providers.provider { flutter.compileSdkVersion }.get())
    //ndkVersion = providers.provider { flutter.ndkVersion }.get()
}

dependencies {
    val coroutinesVersion = "1.6.4"
    //val media3Version = "1.6.1"
    val media3Version = "1.1.1"
    val glideVersion = "4.14.2"

    // ADD THIS LINE:
    // Use compileOnly if you expect the consuming app to always provide it at runtime.
    // Use implementation for this test if you want to ensure it's definitely packaged.
    // For plugin development, compileOnly is often preferred for Flutter dependencies.
    //compileOnly("io.flutter:flutter_embedding_debug:1.0.0-edd8546116457bdf1c5bdfb13ecb9463d2bb5ed4")
    // If compileOnly doesn't resolve it for compilation in this specific scenario,
    // try implementation temporarily for the test, but compileOnly is more typical for plugins.
    // implementation("io.flutter:flutter_embedding_debug:1.0.0-<flutter_sdk_version_hash>")

    implementation("org.jetbrains.kotlin:kotlin-stdlib")
    implementation("androidx.multidex:multidex:2.0.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:$coroutinesVersion")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:$coroutinesVersion")
    //implementation("androidx.annotation:annotation:1.9.1") //Eirik 18.09.24: Now same as assets_audio_player_web
    implementation("androidx.annotation:annotation:1.7.0")
    //implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.core:core-ktx:1.10.1")
    implementation("androidx.media3:media3-session:$media3Version")
    implementation("androidx.media3:media3-common:$media3Version")
    implementation("androidx.media3:media3-exoplayer:$media3Version")
    implementation("androidx.media3:media3-exoplayer-hls:$media3Version")
    implementation("androidx.media3:media3-exoplayer-dash:$media3Version")
    implementation("androidx.media3:media3-exoplayer-smoothstreaming:$media3Version")
    implementation("androidx.media3:media3-ui:$media3Version")
    implementation("com.github.bumptech.glide:glide:$glideVersion")
    implementation("androidx.concurrent:concurrent-futures:1.2.0")
    implementation("com.google.guava:listenablefuture:9999.0-empty-to-avoid-conflict-with-guava")
    annotationProcessor("com.github.bumptech.glide:compiler:$glideVersion")
}
