// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package kinetickk.gradle

import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.gradle.api.GradleException
import org.gradle.testfixtures.ProjectBuilder

class HostPackagingTest {
    @Test
    fun releaseFileVersionGivesTheJavaMajor() {
        assertEquals(21, javaMajorOfRelease(release("21.0.11")))
        assertEquals(17, javaMajorOfRelease(release("17.0.20+8")))
        assertEquals(25, javaMajorOfRelease(release("25")))
        assertEquals(8, javaMajorOfRelease(release("1.8.0_392")))
        assertNull(javaMajorOfRelease("IMPLEMENTOR=\"Eclipse Adoptium\"\nJAVA_RUNTIME_VERSION=\"21.0.11+10-LTS\"\n"))
        assertNull(javaMajorOfRelease(release("unknown")))
    }

    @Test
    fun jdk17To20KeepTheConcurrentG1Options() {
        for (major in DESKTOP_GAME_MIN_JAVA..20) {
            assertEquals(
                listOf("--enable-native-access=ALL-UNNAMED", "-XX:+ExplicitGCInvokesConcurrent", "-XX:MaxGCPauseMillis=4"),
                desktopGameJvmArgs(major),
            )
        }
    }

    @Test
    fun jdk21And22SelectGenerationalZgcExplicitly() {
        for (major in 21..22) {
            assertEquals(
                listOf(
                    "--enable-native-access=ALL-UNNAMED", "-XX:+UseZGC", "-XX:+ZGenerational",
                    "-Xms512m", "-Xmx512m", "-XX:+AlwaysPreTouch",
                ),
                desktopGameJvmArgs(major),
            )
        }
    }

    @Test
    fun jdk23AndLaterGetGenerationalZgcWithoutTheDeprecatedFlag() {
        // 26 and later reject -XX:+ZGenerational and would not start; the packaging JDK must start too.
        for (major in listOf(23, 24, 25, 26, 27, 30, DESKTOP_PACKAGING_JAVA)) {
            val arguments = desktopGameJvmArgs(major)
            assertTrue("-XX:+UseZGC" in arguments)
            assertFalse(arguments.any { "ZGenerational" in it })
            assertTrue(arguments.containsAll(listOf("-Xms512m", "-Xmx512m", "-XX:+AlwaysPreTouch")))
        }
    }

    @Test
    fun everyJdkGetsNativeAccessAndAtMostOneCollector() {
        for (major in DESKTOP_GAME_MIN_JAVA..30) {
            for (collector in DesktopGameCollector.entries) {
                if (collector == DesktopGameCollector.ZGC && major < 21) continue
                val arguments = desktopGameJvmArgs(major, collector)
                assertTrue("--enable-native-access=ALL-UNNAMED" in arguments, "JDK $major $collector")
                assertTrue(arguments.count(::selectsGarbageCollector) <= 1, "JDK $major $collector: $arguments")
            }
        }
        assertEquals(DesktopGameCollector.G1, defaultDesktopGameCollector(20))
        assertEquals(DesktopGameCollector.ZGC, defaultDesktopGameCollector(21))
    }

    @Test
    fun unsupportedCollectorChoiceIsCheckedBeforeTheJvmStarts() {
        // JDK 17-20 would run single-generation ZGC, a collector the game never ships with.
        assertFailsWith<IllegalArgumentException> { desktopGameJvmArgs(17, DesktopGameCollector.ZGC) }
        assertEquals(
            listOf("--enable-native-access=ALL-UNNAMED", "-XX:+ExplicitGCInvokesConcurrent", "-XX:MaxGCPauseMillis=4"),
            desktopGameJvmArgs(21, DesktopGameCollector.G1),
        )
        assertTrue(listOf("-XX:+UseG1GC", "-XX:+UseZGC", "-XX:+UseParallelGC", " -XX:+UseSerialGC").all(::selectsGarbageCollector))
        assertFalse(listOf("-XX:+ZGenerational", "-XX:-UseZGC", "-Xlog:gc*,safepoint", "-XX:+UseStringDeduplication").any(::selectsGarbageCollector))
    }

    @Test
    fun packagingWithoutJmodsStopsWithTheRemediesAndRunsOnceTheyArePresent() {
        val jdk = createTempDirectory("kk-jdk").toFile()
        try {
            val task = ProjectBuilder.builder().build().tasks.register("proguardReleaseJars").get()
            val failure = assertFailsWith<GradleException> { RequireJmodsAction(jdk.path).execute(task) }
            assertTrue("${jdk.path}/jmods/java.base.jmod" in failure.message.orEmpty(), failure.message)
            assertTrue("-Pkinetickk.packagingJdk" in failure.message.orEmpty(), failure.message)
            File(jdk, "jmods").mkdirs()
            File(jdk, "jmods/java.base.jmod").writeText("")
            RequireJmodsAction(jdk.path).execute(task)
        } finally {
            jdk.deleteRecursively()
        }
    }

    private fun release(version: String) =
        "IMPLEMENTOR=\"Eclipse Adoptium\"\nJAVA_RUNTIME_VERSION=\"$version-LTS\"\nJAVA_VERSION=\"$version\"\nOS_ARCH=\"aarch64\"\n"
}
