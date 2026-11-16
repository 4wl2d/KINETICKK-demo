// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

plugins {
    id("kinetickk.compose-library")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(projects.foundation.common)
            implementation(libs.compose.foundation)
            implementation(projects.foundation.design)
            implementation(libs.compose.runtime)
            implementation(projects.resource.audio.api)
            implementation(projects.resource.audio.impl)
            implementation(projects.ball.content.api)
            implementation(projects.ball.content.impl)
            implementation(projects.ball.profile.api)
            implementation(projects.ball.profile.interaction)
            implementation(projects.ball.profile.impl)
            implementation(projects.ball.gameplay.impl)
            implementation(projects.flow.session.interaction)
            implementation(projects.flow.session.impl)
        }
        commonTest.dependencies {
            implementation(projects.foundation.common)
            implementation(projects.ball.gameplay.api)
            implementation(projects.ball.gameplay.interaction)
            implementation(projects.ball.gameplay.nucleus)
            implementation(projects.flow.session.api)
        }
        desktopTest.dependencies {
            implementation(libs.compose.foundation)
            implementation(libs.compose.ui.test.junit4)
            val os = when {
                System.getProperty("os.name").startsWith("Mac") -> "macos"
                System.getProperty("os.name").startsWith("Windows") -> "windows"
                else -> "linux"
            }
            val arch = if (System.getProperty("os.arch") in setOf("aarch64", "arm64")) "arm64" else "x64"
            runtimeOnly("org.jetbrains.skiko:skiko-awt-runtime-$os-$arch:${libs.versions.skiko.get()}")
        }
        // The web display host reads the browser's fullscreen API.
        wasmJsMain.dependencies {
            implementation(libs.kotlinx.browser)
        }
        wasmJsTest.dependencies {
            implementation(libs.kotlinx.browser)
        }
    }
}
