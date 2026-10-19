import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

// webrtc-java ships its native library in a per-OS classifier jar that Gradle can't pick automatically.
val webrtcNativeClassifier: String = run {
    val os = System.getProperty("os.name").lowercase()
    val arch = System.getProperty("os.arch").lowercase()
    val cpu = if (arch == "aarch64" || arch == "arm64") "aarch64" else "x86_64"
    when {
        os.contains("mac") -> "macos-$cpu"
        os.contains("win") -> "windows-x86_64"
        else -> "linux-$cpu"
    }
}

dependencies {
    implementation(project(":shared"))

    implementation(compose.desktop.currentOs)
    implementation(libs.kotlinx.coroutinesSwing)

    implementation(libs.compose.uiToolingPreview)

    runtimeOnly(variantOf(libs.webrtc.java) { classifier(webrtcNativeClassifier) })
}

compose.desktop {
    application {
        mainClass = "com.laarasoft.frontend.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "com.laarasoft.frontend"
            packageVersion = "1.0.0"

            macOS {
                infoPlist {
                    extraKeysRawXml = """
                        <key>NSCameraUsageDescription</key>
                        <string>Leet needs camera access so others can see you in meetings.</string>
                        <key>NSMicrophoneUsageDescription</key>
                        <string>Leet needs microphone access so others can hear you in meetings.</string>
                    """.trimIndent()
                }
            }
        }
    }
}
