// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

import kinetickk.gradle.DESKTOP_GAME_MIN_JAVA
import kinetickk.gradle.DESKTOP_PACKAGING_JAVA
import kinetickk.gradle.collectDesktopLegalDocuments
import kinetickk.gradle.desktopGameJvmArgs
import kinetickk.gradle.javaMajorOfRelease
import kinetickk.gradle.packageLegalDocuments
import kinetickk.gradle.RequireJmodsAction
import org.gradle.api.JavaVersion
import org.jetbrains.compose.desktop.application.tasks.AbstractJLinkTask
import org.jetbrains.compose.desktop.application.tasks.AbstractProguardTask
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("kinetickk.base")
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
}

val currentJavaHome = providers.systemProperty("java.home").get()
fun hasJmods(javaHome: String): Boolean = file("$javaHome/jmods/java.base.jmod").isFile
// JEP 493 JDK distributions, Temurin 25 among them, ship jmods as a separate download. Compose's
// ProGuard task reads them and jlink links the packaged runtime from them; without them in the
// current JDK, use an installed Java 25 toolchain that has them. Gradle answers that request with
// the JDK it runs on whenever that is a Java 25 itself, so a jmod-less JDK 25 needs its jmods or
// -Pkinetickk.packagingJdk. Configuration still succeeds; the packaging tasks stop with that advice.
val packagingJavaHome = providers.gradleProperty("kinetickk.packagingJdk").orNull
    ?: currentJavaHome.takeIf(::hasJmods)
    ?: runCatching {
        javaToolchains.launcherFor {
            languageVersion.set(JavaLanguageVersion.of(DESKTOP_PACKAGING_JAVA))
        }.get().metadata.installationPath.asFile.absolutePath
    }.getOrNull()?.takeIf(::hasJmods)
    ?: currentJavaHome
// `run` starts the packaging JDK and the packaged app ships a runtime linked from it, so its version decides which
// collector options the game can use. Read through a provider, the release file is a configuration-cache input.
val packagingJavaMajor = providers.fileContents(layout.projectDirectory.file("$packagingJavaHome/release")).asText
    .orNull?.let(::javaMajorOfRelease) ?: DESKTOP_GAME_MIN_JAVA

composeCompiler {
    includeSourceInformation.set(false)
    includeTraceMarkers.set(false)
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
        freeCompilerArgs.add("-Xjdk-release=17")
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

dependencies {
    implementation(compose.desktop.currentOs)
}

val desktopLegalDocuments = collectDesktopLegalDocuments()

compose.desktop {
    application {
        javaHome = packagingJavaHome
        mainClass = "kinetickk.app.desktop.DesktopMainKt"
        jvmArgs += desktopGameJvmArgs(packagingJavaMajor)

        buildTypes.release.proguard {
            // ProGuard 7.10 adds support for the project's Kotlin 2.4 compiler metadata.
            version.set("7.10.0")
            isEnabled.set(true)
            optimize.set(true)
            obfuscate.set(true)
            joinOutputJars.set(true)
            configurationFiles.from(project.file("release.pro"))
        }

        nativeDistributions {
            packageName = "KINETICKK Demo"
            licenseFile.set(rootProject.layout.projectDirectory.file("LICENSE"))
            appResourcesRootDir.fileProvider(desktopLegalDocuments.map { it.destinationDir })
            macOS.bundleID = "kinetickk.demo.desktop"
            // java.desktop covers AWT, Skiko, fonts and audio; Preferences persists the profile.
            modules("java.desktop", "java.prefs", "jdk.unsupported")
            packageVersion = project.version.toString()
            macOS {
                // jpackage forbids major zero. This packaging-only mapping keeps 0.x game
                // versions intact while retaining upgrade order when the game reaches 1.x.
                val version = project.version.toString()
                val packageMajor = version.substringBefore('.').toInt() + 1
                packageVersion = "$packageMajor.${version.substringAfter('.')}"
            }
            description = "KINETICKK demo"
            vendor = "Vladislav Tomilov"
            copyright = "Copyright (c) 2026 Vladislav Tomilov. All rights reserved."
            macOS.appCategory = "public.app-category.action-games"
        }
    }
}

tasks.withType<AbstractProguardTask>().configureEach { doFirst(RequireJmodsAction(packagingJavaHome)) }
tasks.withType<AbstractJLinkTask>().configureEach { doFirst(RequireJmodsAction(packagingJavaHome)) }

packageLegalDocuments()
