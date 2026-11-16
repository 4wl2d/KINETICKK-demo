// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

plugins {
    id("kinetickk.kmp-platforms")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(projects.foundation.common)
            implementation(projects.ball.content.api)
            implementation(projects.ball.profile.api)
            implementation(projects.ball.gameplay.api)
        }
        commonTest.dependencies {
            implementation(projects.ball.content.impl)
        }
    }
}
