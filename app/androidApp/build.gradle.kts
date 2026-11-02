import org.gradle.api.tasks.Exec
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

val defaultReleaseSigningPropertiesFile =
    File(System.getProperty("user.home"), ".toread/signing/release.properties")
val releaseSigningPropertiesFile = providers.gradleProperty("toread.releaseSigningProperties")
    .orElse(providers.environmentVariable("TOREAD_RELEASE_SIGNING_PROPERTIES"))
    .orNull
    ?.let { file(it) }
    ?: defaultReleaseSigningPropertiesFile
val releaseSigningProperties = Properties().apply {
    if (releaseSigningPropertiesFile.isFile) {
        releaseSigningPropertiesFile.inputStream().use(::load)
    }
}
val releaseSigningConfigured = releaseSigningPropertiesFile.isFile

fun requiredReleaseSigningProperty(name: String): String =
    releaseSigningProperties.getProperty(name)?.takeIf(String::isNotBlank)
        ?: throw GradleException(
            "Missing '$name' in release signing properties: ${releaseSigningPropertiesFile.absolutePath}",
        )

val releaseSigningStoreFile = if (releaseSigningConfigured) {
    file(requiredReleaseSigningProperty("storeFile"))
} else {
    null
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_11
    }
}
dependencies {
    implementation(project(":app:shared"))

    implementation(libs.androidx.activity.compose)
    implementation(libs.kotlinx.coroutinesCore)

    implementation(libs.compose.uiToolingPreview)
    debugImplementation(libs.compose.uiTooling)
}

android {
    namespace = "com.example.toread"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "cn.toreadapp.reader"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = 2
        versionName = libs.versions.toread.app.get()
    }
    signingConfigs {
        if (releaseSigningConfigured) {
            create("release") {
                storeFile = releaseSigningStoreFile
                storePassword = requiredReleaseSigningProperty("storePassword")
                keyAlias = requiredReleaseSigningProperty("keyAlias")
                keyPassword = requiredReleaseSigningProperty("keyPassword")
            }
        }
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    buildTypes {
        getByName("debug") {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        getByName("release") {
            isMinifyEnabled = false
            if (releaseSigningConfigured) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

val validateReleaseSigning by tasks.registering {
    group = "verification"
    description = "Fails release builds unless the external Toread signing configuration is complete."
    inputs.property("releaseSigningPropertiesPath", releaseSigningPropertiesFile.absolutePath)
    inputs.property("releaseSigningStorePath", releaseSigningStoreFile?.absolutePath.orEmpty())

    doLast {
        val propertiesPath = inputs.properties.getValue("releaseSigningPropertiesPath").toString()
        val storePath = inputs.properties.getValue("releaseSigningStorePath").toString()
        if (!File(propertiesPath).isFile) {
            throw GradleException(
                "Release signing properties not found: $propertiesPath. " +
                    "Run client/app/androidApp/scripts/Initialize-ReleaseSigning.ps1 once.",
            )
        }
        if (storePath.isBlank() || !File(storePath).isFile) {
            throw GradleException("Release keystore not found: $storePath")
        }
    }
}

tasks.matching { it.name == "preReleaseBuild" }.configureEach {
    dependsOn(validateReleaseSigning)
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
            "${android.defaultConfig.applicationId}.debug/${android.namespace}.MainActivity",
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
