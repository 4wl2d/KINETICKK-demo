// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

import kinetickk.gradle.ARCHITECTURE_EDGE_ELEMENTS_CONFIGURATION_NAME
import kinetickk.gradle.ARCHITECTURE_EDGE_EXPORT_TASK_NAME
import kinetickk.gradle.ExportArchitectureEdgesTask
import kinetickk.gradle.GAME_VERSION
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.plugins.BasePluginExtension
import org.gradle.api.tasks.bundling.AbstractArchiveTask
import org.jetbrains.kotlin.gradle.tasks.Kotlin2JsCompile
import org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask

tasks.withType<AbstractArchiveTask>().configureEach {
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
}

// Leaf names such as `api` and `impl` repeat across module families. Giving every
// family its own Maven group prevents Kotlin MPP metadata from conflating those projects.
group = path
    .removePrefix(":")
    .split(':')
    .dropLast(1)
    .filter(String::isNotBlank)
    .joinToString(separator = ".", prefix = "kinetickk.")
    .removeSuffix(".")
version = GAME_VERSION

// Kotlin/Wasm can incorporate KLIB source identities into JavaScript import names.
val sourceRoot = rootProject.layout.projectDirectory.asFile.canonicalPath
tasks.withType<Kotlin2JsCompile>().configureEach {
    compilerOptions.freeCompilerArgs.add("-Xklib-relative-path-base=$sourceRoot")
}

val sourceProjectPathValue = path
val exportArchitectureEdgesTask = tasks.register<ExportArchitectureEdgesTask>(
    ARCHITECTURE_EDGE_EXPORT_TASK_NAME,
) {
    group = "verification"
    description = "Exports this project's declared project-dependency edges deterministically."
    sourceProjectPath.set(sourceProjectPathValue)
    outputFile.set(layout.buildDirectory.file("reports/architecture/declared-project-edges.tsv"))
}

val architectureEdgeElements = configurations.create(
    ARCHITECTURE_EDGE_ELEMENTS_CONFIGURATION_NAME,
) {
    description = "Consumable deterministic architecture-edge report for this project."
    isCanBeConsumed = true
    isCanBeResolved = false
    isCanBeDeclared = false
}
architectureEdgeElements.outgoing.artifact(
    exportArchitectureEdgesTask.flatMap { it.outputFile },
) {
    builtBy(exportArchitectureEdgesTask)
}

configurations.configureEach {
    val declarationConfiguration = this
    dependencies.withType(ProjectDependency::class.java).configureEach {
        // Kotlin creates resolvable classpaths by copying declared project
        // dependencies. Only the original declarable configuration is architectural input.
        if (
            !declarationConfiguration.isCanBeDeclared ||
            declarationConfiguration.name.endsWith("CompileClasspath") ||
            declarationConfiguration.name.endsWith("RuntimeClasspath")
        ) {
            return@configureEach
        }
        val targetProjectPath = path
        exportArchitectureEdgesTask.configure {
            declaredProjectDependencies.add(
                "$sourceProjectPathValue\t${declarationConfiguration.name}\t$targetProjectPath",
            )
        }
    }
}

pluginManager.withPlugin("base") {
    extensions.configure<BasePluginExtension> {
        archivesName.set(path.removePrefix(":").replace(':', '-'))
    }
}

// The Kotlin compiler is the linter: -Wextra adds its extended checks (redundant code, unused
// values, var that can be val) to the compile that already runs, and IDE import shows them while
// typing. Warnings fail the compile, so none pile up; `-Pkinetickk.allowWarnings=true` lets a
// local experiment compile with them.
val failOnWarnings = providers.gradleProperty("kinetickk.allowWarnings").orNull != "true"
tasks.withType<KotlinCompilationTask<*>>().configureEach {
    compilerOptions {
        allWarningsAsErrors.set(failOnWarnings)
        freeCompilerArgs.addAll(
            "-Wextra",
            // expect/actual classes are a deliberate choice in foundation:design.
            "-Xexpect-actual-classes",
            // A lateinit var that a lambda captures before it is assigned cannot be a val.
            "-Xwarning-level=CAN_BE_VAL_LATEINIT:disabled",
        )
    }
}
