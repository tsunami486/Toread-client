import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.net.URI

@CacheableTask
abstract class GenerateToreadBuildConfig : DefaultTask() {
    @get:Input
    abstract val serverBaseUrl: Property<String>

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @TaskAction
    fun generate() {
        val normalizedServerBaseUrl = normalizeServerBaseUrl(serverBaseUrl.get())
        val escapedServerBaseUrl = normalizedServerBaseUrl
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\$", "\\\$")
            .replace("\r", "\\r")
            .replace("\n", "\\n")
            .replace("\t", "\\t")
        val packageDirectory = outputDirectory.get().asFile
            .resolve("com/example/toread/config")
        packageDirectory.mkdirs()
        packageDirectory.resolve("GeneratedToreadBuildConfig.kt").writeText(
            """
            package com.example.toread.config

            // Build metadata is embedded in the client binary and must never contain credentials.
            internal object GeneratedToreadBuildConfig {
                const val serverBaseUrl: String = "$escapedServerBaseUrl"
            }
            """.trimIndent() + "\n",
        )
    }

    private fun normalizeServerBaseUrl(value: String): String {
        val normalized = value.trim().trimEnd('/')
        if (normalized.isBlank()) return ""

        val uri = runCatching { URI(normalized) }.getOrElse {
            throw GradleException("toread.serverBaseUrl must be a valid HTTP(S) URL")
        }
        require(uri.scheme?.lowercase() in setOf("http", "https") && !uri.host.isNullOrBlank()) {
            "toread.serverBaseUrl must be an absolute HTTP(S) URL"
        }
        require(uri.port == -1 || uri.port in 1..65535) {
            "toread.serverBaseUrl must use a valid TCP port"
        }
        require(uri.userInfo == null && uri.query == null && uri.fragment == null) {
            "toread.serverBaseUrl must not contain credentials, a query, or a fragment"
        }
        return normalized
    }
}

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.sqldelight)
}

val generatedToreadBuildConfigDirectory =
    layout.buildDirectory.dir("generated/sources/toreadBuildConfig/commonMain/kotlin")
val configuredServerBaseUrl = providers
    .gradleProperty("toread.serverBaseUrl")
    .orElse(providers.environmentVariable("TOREAD_SERVER_BASE_URL"))
    .orElse("")

val generateToreadBuildConfig = tasks.register<GenerateToreadBuildConfig>("generateToreadBuildConfig") {
    description = "Generates non-secret Toread client build configuration."
    serverBaseUrl.set(configuredServerBaseUrl)
    outputDirectory.set(generatedToreadBuildConfigDirectory)
}

kotlin {
    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }

    jvm()
    
    androidLibrary {
       namespace = "com.example.toread.app.shared"
       compileSdk = libs.versions.android.compileSdk.get().toInt()
       minSdk = libs.versions.android.minSdk.get().toInt()
    
       compilerOptions {
           jvmTarget = JvmTarget.JVM_11
       }
       androidResources {
           enable = true
       }
       withHostTest {
           isIncludeAndroidResources = true
       }
    }
    
    sourceSets {
        val desktopMain by creating {
            dependsOn(getByName("commonMain"))
        }
        getByName("jvmMain").dependsOn(desktopMain)

        commonMain {
            kotlin.srcDir(generateToreadBuildConfig)
        }
        androidMain.dependencies {
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.sqldelight.androidDriver)
            implementation(libs.ktor.clientAndroid)
        }
        desktopMain.dependencies {
            implementation(libs.sqldelight.sqliteDriver)
            implementation(libs.ktor.clientCio)
            implementation(libs.pdfbox)
        }
        commonMain.dependencies {
            implementation(libs.compose.runtime)
            implementation(libs.compose.animation)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.ui)
            implementation(libs.compose.components.resources)
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.androidx.lifecycle.runtimeCompose)
            implementation(libs.kotlinx.coroutinesCore)
            implementation(libs.kotlinx.serializationJson)
            implementation(libs.sqldelight.runtime)
            implementation(libs.ktor.clientCore)
            implementation(libs.ktor.clientContentNegotiation)
            implementation(libs.ktor.clientSerializationKotlinxJson)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
        jvmTest.dependencies {
            runtimeOnly(compose.desktop.currentOs)
        }
    }
}

dependencies {
    androidRuntimeClasspath(libs.compose.uiTooling)
}

sqldelight {
    databases {
        create("ToreadDatabase") {
            packageName.set("com.example.toread.database")
        }
    }
}
