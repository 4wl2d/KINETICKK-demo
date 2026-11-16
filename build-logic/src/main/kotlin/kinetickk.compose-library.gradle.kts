// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

import kinetickk.gradle.configureSkikoWasmRuntime
import org.gradle.api.artifacts.VersionCatalogsExtension

plugins {
    id("kinetickk.kmp-platforms")
    id("org.jetbrains.kotlin.plugin.compose")
}

composeCompiler {
    includeSourceInformation.set(false)
    includeTraceMarkers.set(false)
}

val libraries = extensions.getByType<VersionCatalogsExtension>().named("libs")
configureSkikoWasmRuntime(libraries.findVersion("skiko").get().requiredVersion)
