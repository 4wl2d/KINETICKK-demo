// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

plugins {
    id("kinetickk.kmp-platforms")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(projects.flow.session.api)
            implementation(projects.foundation.common)
            implementation(projects.ball.content.api)
            implementation(projects.ball.profile.api)
            implementation(projects.ball.gameplay.api)
        }
    }
}
