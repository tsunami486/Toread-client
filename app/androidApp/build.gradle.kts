import org.gradle.api.tasks.Exec
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_11
    }
}
dependencies {
    implementation(project(":client:app:shared"))

    implementation(libs.androidx.activity.compose)
    implementation(libs.kotlinx.coroutinesCore)

    implementation(libs.compose.uiToolingPreview)
    debugImplementation(libs.compose.uiTooling)
}

android {
    namespace = "com.example.toread"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.example.toread"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = 2
        versionName = "1.0.1"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

tasks.register<Exec>("runAndroidDebug") {
    group = "application"
    description = "Installs and launches the Toread Android debug app on a connected device or emulator."
    dependsOn("installDebug")

    doFirst {
        val adbName = if (System.getProperty("os.name").startsWith("Windows", ignoreCase = true)) {
            "adb.exe"
        } else {
            "adb"
        }
        val adbExecutable = androidSdkDirectory().resolve("platform-tools").resolve(adbName)
        commandLine(
            adbExecutable.absolutePath,
            "shell",
            "am",
            "start",
            "-n",
            "${android.defaultConfig.applicationId}/.MainActivity",
        )
    }
}

fun androidSdkDirectory(): File {
    val localPropertiesFile = rootProject.file("local.properties")
    val localSdkDir = if (localPropertiesFile.isFile) {
        val properties = Properties()
        localPropertiesFile.inputStream().use(properties::load)
        properties.getProperty("sdk.dir")
    } else {
        null
    }
    val sdkDir = localSdkDir
        ?: System.getenv("ANDROID_HOME")
        ?: System.getenv("ANDROID_SDK_ROOT")
        ?: error("Android SDK not found. Set sdk.dir in local.properties or ANDROID_HOME/ANDROID_SDK_ROOT.")
    return file(sdkDir)
}
