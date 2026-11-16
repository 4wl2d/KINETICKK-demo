// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    id("kinetickk.compose-library")
    id("org.jetbrains.compose")
}

compose.resources {
    packageOfResClass = "kinetickk.foundation.design.generated.resources"
}

kotlin {
    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        binaries.executable()
    }
    sourceSets {
        commonMain.dependencies {
            api(libs.compose.ui)
            api(projects.foundation.common)
            implementation(libs.compose.resources)
            implementation(libs.compose.foundation)
        }
        desktopTest.dependencies {
            implementation(libs.compose.ui.test.junit4)
            implementation(libs.kotlinx.serialization.json)
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
