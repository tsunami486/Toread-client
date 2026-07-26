import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

dependencies {
    implementation(project(":client:app:shared"))

    implementation(compose.desktop.currentOs)
    implementation(libs.kotlinx.coroutinesSwing)

    implementation(libs.compose.uiToolingPreview)
}

compose.desktop {
    application {
        mainClass = "com.example.toread.DesktopMainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            modules("java.instrument", "java.management", "java.sql", "jdk.unsupported")
            packageName = "com.example.toread"
            packageVersion = "1.0.0"
        }
    }
}

tasks.register("runDesktopApp") {
    group = "application"
    description = "Runs the Toread desktop app without hot reload."
    dependsOn(tasks.named("run"))
}

tasks.register("runDesktopAppHotReload") {
    group = "application"
    description = "Runs the Toread desktop app with Compose Hot Reload enabled."
    dependsOn(tasks.named("hotRun"))
}
