// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

plugins {
    id("kinetickk.compose-library")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(libs.compose.runtime)
            api(projects.ball.gameplay.api)

            implementation(libs.compose.foundation)
            implementation(libs.compose.ui)
            implementation(projects.foundation.common)
            implementation(projects.foundation.design)
            implementation(projects.ball.content.api)
            implementation(projects.ball.profile.api)
            implementation(projects.ball.gameplay.nucleus)
        }
        desktopTest {
            dependencies {
                implementation(libs.compose.ui.test.junit4)
                // The shipped catalog for the reward card gallery and its style checks (tests only).
                implementation(projects.ball.content.impl)
                val os = when {
                    System.getProperty("os.name").startsWith("Mac") -> "macos"
                    System.getProperty("os.name").startsWith("Windows") -> "windows"
                    else -> "linux"
                }
                val arch = if (System.getProperty("os.arch") in setOf("aarch64", "arm64")) "arm64" else "x64"
                runtimeOnly("org.jetbrains.skiko:skiko-awt-runtime-$os-$arch:${libs.versions.skiko.get()}")
            }
        }
    }
}
