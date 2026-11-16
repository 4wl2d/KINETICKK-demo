// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package kinetickk.gradle

import java.io.File
import java.io.Serializable
import org.gradle.api.Action
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.tasks.Copy
import org.gradle.api.tasks.Sync

/** The documents every distributed build carries: the license, the notices, the third-party license texts. */
private val legalDocumentPaths = listOf(
    "LICENSE",
    "NOTICE",
    "LICENSES/**",
)

internal fun Project.packageLegalDocuments() {
    val repositoryRoot = rootDir
    tasks.withType(Copy::class.java).configureEach {
        if (name.endsWith("processResources", ignoreCase = true)) {
            from(repositoryRoot) {
                include(legalDocumentPaths)
                into("META-INF")
            }
        }
    }
}

/** Loose notices for the native app image, in addition to the application JAR. */
internal fun Project.collectDesktopLegalDocuments() = tasks.register("collectDesktopLegalDocuments", Sync::class.java) {
    from(rootDir) {
        include(legalDocumentPaths)
        into("common/Legal")
    }
    into(layout.buildDirectory.dir("desktop-app-resources"))
}

/** The garbage collector the desktop game runs on. */
enum class DesktopGameCollector {
    G1,
    ZGC,
}

/** The oldest JDK the desktop game supports. Its options start on every newer JDK; use it when the JDK is unknown. */
const val DESKTOP_GAME_MIN_JAVA: Int = 17

/**
 * The packaged app ships a runtime linked from this JDK when Gradle runs on a JDK without jmods.
 */
const val DESKTOP_PACKAGING_JAVA: Int = 25

/**
 * Stops a desktop packaging task whose JDK at [javaHome] has no jmods, which ProGuard reads and jlink links from,
 * with the remedies instead of ProGuard's unreadable-library error.
 */
class RequireJmodsAction(private val javaHome: String) : Action<Task>, Serializable {
    override fun execute(task: Task) {
        if (File(javaHome, "jmods/java.base.jmod").isFile) return
        throw GradleException(
            "${task.path} needs a JDK with jmods, but $javaHome/jmods/java.base.jmod is missing. Unpack the JDK's jmods " +
                "archive (Temurin publishes it separately) into $javaHome/jmods, run Gradle on a JDK that has them, or " +
                "pass -Pkinetickk.packagingJdk=/absolute/path/to/jdk.",
        )
    }
}

/** Generational ZGC where the JDK has it (21 and later), G1 before that. */
fun defaultDesktopGameCollector(javaMajor: Int): DesktopGameCollector =
    if (javaMajor >= GENERATIONAL_ZGC_JAVA) DesktopGameCollector.ZGC else DesktopGameCollector.G1

/**
 * JVM options for the desktop run task and the packaged app on JDK [javaMajor].
 * - [DesktopGameCollector.ZGC] (the default from JDK 21) runs generational ZGC: `System.gc()` becomes a concurrent
 *   cycle and pauses stay far below a millisecond. `-XX:+ZGenerational` selects the generational mode on JDK 21 and
 *   22; JDK 23 made that mode the default and deprecated the flag, 24 made it the only mode and ignores the flag
 *   with a warning, and 26 rejects it, so the JVM would not start. It is left out from 23 on. The 512 MB heap is
 *   fixed and touched at startup, so the collector never resizes it during play (the live set is about 20-40 MB).
 * - [DesktopGameCollector.G1] (JDK 17-20, which reject the generational flag and would not start) keeps G1. There
 *   `-XX:+ExplicitGCInvokesConcurrent` turns the call from a 20-35 ms full collection into a concurrent cycle, and
 *   the 4 ms pause target keeps ordinary pauses short as well.
 *
 * At most one option selects a collector.
 */
fun desktopGameJvmArgs(
    javaMajor: Int,
    collector: DesktopGameCollector = defaultDesktopGameCollector(javaMajor),
): List<String> = when (collector) {
    DesktopGameCollector.G1 -> listOf(NATIVE_ACCESS, "-XX:+ExplicitGCInvokesConcurrent", "-XX:MaxGCPauseMillis=4")
    DesktopGameCollector.ZGC -> {
        require(javaMajor >= GENERATIONAL_ZGC_JAVA) {
            "Generational ZGC needs JDK $GENERATIONAL_ZGC_JAVA or newer; JDK $javaMajor would run single-generation ZGC"
        }
        listOfNotNull(
            NATIVE_ACCESS,
            "-XX:+UseZGC",
            "-XX:+ZGenerational".takeIf { javaMajor < GENERATIONAL_ZGC_DEFAULT_JAVA },
            "-Xms512m",
            "-Xmx512m",
            "-XX:+AlwaysPreTouch",
        )
    }
}

/** True when [argument] selects a garbage collector; a JVM given two such options refuses to start. */
fun selectsGarbageCollector(argument: String): Boolean = COLLECTOR_OPTION.matches(argument.trim())

/**
 * The major version a JDK's `release` file names (`JAVA_VERSION="21.0.11"` is 21, `"1.8.0_392"` is 8), or null when
 * the file has no readable version.
 */
fun javaMajorOfRelease(text: String): Int? {
    val version = text.lineSequence().map(String::trim).firstOrNull { it.startsWith("JAVA_VERSION=") }
        ?.substringAfter('=')?.trim()?.removeSurrounding("\"") ?: return null
    val parts = version.split('.', '+', '-', '_')
    val first = parts.first().toIntOrNull() ?: return null
    return if (first == 1) parts.getOrNull(1)?.toIntOrNull() else first
}

private const val NATIVE_ACCESS = "--enable-native-access=ALL-UNNAMED"
private const val GENERATIONAL_ZGC_JAVA = 21
private const val GENERATIONAL_ZGC_DEFAULT_JAVA = 23
private val COLLECTOR_OPTION = Regex("-XX:\\+Use(Serial|Parallel|G1|Z|Shenandoah|Epsilon)GC")
