// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

plugins {
    id("kinetickk.kmp-platforms")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(projects.ball.content.api)
            api(projects.ball.profile.api)
            api(projects.foundation.common)
        }
        commonTest.dependencies {
            implementation(projects.ball.content.impl)
        }
    }
}
