// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package kinetickk.app.shared

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.key
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.window.ApplicationScope
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import java.awt.Dimension
import java.awt.EventQueue
import java.awt.Window as AwtWindow
import java.beans.PropertyChangeListener
import java.util.concurrent.CompletableFuture
import kinetickk.foundation.diagnostics.CrashDiagnostics

/**
 * The desktop application. The composition owner (Profile, Gameplay, Session and audio) lives above
 * the window, so a window opened again for a decoration or V-Sync change keeps the session and the
 * run; only the window's own composition is rebuilt. [hostEffects] compose once, outside the window.
 */
fun runKinetickkDesktop(
    diagnostics: CrashDiagnostics = CrashDiagnostics.None,
    windowBoundary: @Composable (@Composable () -> Unit) -> Unit = { it() },
    hostEffects: @Composable ApplicationScope.() -> Unit = {},
) {
    val host = DesktopDisplayHost()
    // Profile loads synchronously here, so the stored display preferences shape the first window.
    val owner = AppCompositionOwner(diagnostics = diagnostics, displayHost = host, quitAvailable = true)
    runDesktopApplication(host, owner, windowBoundary, hostEffects = hostEffects)
}

/**
 * The desktop application around an [owner] built for [host]: pacing, the window and its recreation. The owner
 * closes on the EDT when the application ends.
 */
internal fun runDesktopApplication(
    host: DesktopDisplayHost,
    owner: AppCompositionOwner,
    windowBoundary: @Composable (@Composable () -> Unit) -> Unit = { it() },
    hostEffects: @Composable ApplicationScope.() -> Unit = {},
) {
    useDesktopOwner(owner) {
        val initial = owner.preferences()
        // Before the first window: Skiko reads the V-Sync setting when it creates a window's redrawer.
        val pacing = DesktopFramePacing.apply(preferenceVsync = initial.vsync)
        host.start(initial.toHostDisplayRequest(), pacing)
        application(exitProcessOnExit = false) {
            windowBoundary {
                key(host.generation) {
                    Window(
                        onCloseRequest = ::exitApplication,
                        state = host.windowState,
                        title = "KINETICKK Demo",
                        undecorated = host.undecorated,
                        resizable = host.resizable,
                    ) {
                        window.minimumSize = Dimension(MINIMUM_WIDTH_DP, MINIMUM_HEIGHT_DP)
                        AttachDesktopWindow(host, window)
                        // Leaving fullscreen: the windowed preset follows once the window has left it.
                        LaunchedEffect(host.leavingFullscreen) {
                            while (host.leavingFullscreen) withFrameNanos { host.settle() }
                        }
                        // Home's Quit takes the window's close path: the owner closes after the application ends.
                        owner.Content(onQuit = ::exitApplication)
                    }
                }
                hostEffects()
            }
        }
    }
}

/**
 * The shown window's screen decides the refresh rate the frame loops pace against: a screen change reads it again,
 * and when V-Sync is on and the screen reports no rate, a short focused burst of unpaced frames measures it.
 */
@Composable
private fun AttachDesktopWindow(host: DesktopDisplayHost, window: AwtWindow) {
    DisposableEffect(host, window) {
        host.attach(window)
        val listener = PropertyChangeListener { host.refreshConfigurationChanged() }
        host.refreshConfigurationChanged()
        window.addPropertyChangeListener("graphicsConfiguration", listener)
        onDispose {
            window.removePropertyChangeListener("graphicsConfiguration", listener)
            host.detach(window)
        }
    }
    val focused = LocalWindowInfo.current.isWindowFocused
    LaunchedEffect(window, host.cadenceRevision, host.windowState.placement, focused) {
        // Fullscreen and back can switch to or from the rate cached before fullscreen.
        host.refreshPacing()
        if (focused && host.refreshCadenceWanted()) host.measureRefreshCadence()
    }
}

internal fun useDesktopOwner(owner: AutoCloseable, run: () -> Unit) {
    AutoCloseable {
        if (EventQueue.isDispatchThread()) {
            owner.close()
        } else {
            // Close writes profile diagnostics into the same recorder as the running game.
            val completion = CompletableFuture<Throwable?>()
            EventQueue.invokeLater { completion.complete(runCatching { owner.close() }.exceptionOrNull()) }
            // Wait for the save and preserve the original failure for use's suppression rules.
            completion.join()?.let { throw it }
        }
    }.use { run() }
}

private const val MINIMUM_WIDTH_DP = 720
private const val MINIMUM_HEIGHT_DP = 540
