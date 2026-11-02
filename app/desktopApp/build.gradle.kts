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
            packageName = "同阅"
            packageVersion = libs.versions.toread.app.get()
            windows {
                iconFile.set(project.file("src/main/resources/tongyue-icon.ico"))
                // Stable across releases so a later MSI upgrades this one in place instead of installing
                // beside it. 1.0.1 shipped without an upgrade code, so this value only starts paying off
                // from the next version on.
                upgradeUuid = "8B3B1E4C-3D5A-4E2C-9C7B-1A2D3E4F5A6B"
            }
            linux {
                packageName = "tongyue"
                iconFile.set(project.file("src/main/resources/tongyue-icon.png"))
            }
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
