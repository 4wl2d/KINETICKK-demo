// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package kinetickk.app.shared

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.awt.ComposeWindow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.WindowState
import kinetickk.foundation.design.pacing.KkDisplayPacing
import java.awt.DisplayMode
import java.awt.GraphicsEnvironment
import java.awt.Rectangle
import java.awt.Toolkit
import java.awt.Window
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull

/**
 * The desktop display host. One [windowState] serves every window it opens, so position and size
 * survive a recreation. A decoration (Borderless) or V-Sync change needs a new window: it bumps
 * [generation], which the application keys its window on. Fullscreen and window sizes change the
 * window in place. Requests apply as diffs against the last one, so a window the player resized or
 * maximized keeps that until a size or mode is chosen again. Leaving fullscreen, the platform puts
 * back the bounds it saved on the way in, so the windowed preset waits until the shown window has
 * left fullscreen and the window calls [settle]. The host never writes Profile.
 */
class DesktopDisplayHost internal constructor(
    private val screens: DesktopScreens,
    private val setSystemProperty: (String, String) -> Unit,
    private val shownFullscreen: (Window?) -> Boolean,
) : PlatformDisplayHost {
    constructor() : this(
        AwtDesktopScreens,
        { key, value -> System.setProperty(key, value) },
        { window -> (window as? ComposeWindow)?.placement == WindowPlacement.Fullscreen },
    )

    val windowState: WindowState = WindowState(size = DpSize(DEFAULT_WIDTH_DP.dp, DEFAULT_HEIGHT_DP.dp))

    /** Changes when the window must be opened again (decoration or V-Sync). */
    var generation: Int by mutableIntStateOf(0)
        private set

    /** Whether the next window opens without a title bar (Borderless). */
    var undecorated: Boolean by mutableStateOf(false)
        private set

    /**
     * Whether the next window can be resized at its edges. A Borderless window covers its screen;
     * resizable and undecorated, Compose would lay an edge resizer over the game's outer 8 dp.
     */
    val resizable: Boolean get() = !undecorated

    private var pacing = DesktopPacing(vsync = true, overridden = false)
    private var vsync = true
    private var last: HostDisplayRequest? = null
    private var window: Window? = null
    private var beforeFullscreen: DesktopScreen? = null
    private var measuredCadence: DesktopCadence? = null
    private var cadenceRevisionValue by mutableIntStateOf(0)

    // The refresh rate the frame loops pace against; snapshot state, so the composition follows a new screen.
    private var pacingRefreshHz by mutableIntStateOf(0)

    internal val cadenceRevision: Int get() = cadenceRevisionValue

    /** The windowed preset chosen while the shown window was fullscreen; [settle] places it. */
    private var pendingWindowed: HostDisplayRequest? by mutableStateOf(null)

    /** Whether a windowed preset waits for the shown window to leave fullscreen. */
    val leavingFullscreen: Boolean get() = pendingWindowed != null

    /** Before the first window: places it for the stored [request] under the decided [pacing]. */
    fun start(request: HostDisplayRequest, pacing: DesktopPacing) {
        this.pacing = pacing
        vsync = pacing.vsync
        last = request
        undecorated = request.windowMode == HostWindowMode.BORDERLESS
        place(request)
        refreshPacing()
    }

    /** The shown window: its screen decides the offered sizes, the refresh readout and Borderless bounds. */
    fun attach(window: Window) {
        this.window = window
        refreshPacing()
    }

    fun detach(window: Window) {
        if (this.window !== window) return
        this.window = null
        measuredCadence = null
        refreshPacing()
    }

    override fun support(): HostDisplaySupport {
        val screen = screens.screen(window)
        return HostDisplaySupport(
            windowModes = true,
            usableWidthDp = screen?.usable?.width ?: 0,
            usableHeightDp = screen?.usable?.height ?: 0,
            vsync = !pacing.overridden,
            refreshReadout = true,
            monitorRefreshHz = screen?.refreshHz ?: 0,
            hardwareKeyboard = true,
            touchPrimary = false,
            maxFrameRateCap = MAX_FRAME_RATE_CAP,
        )
    }

    /**
     * V-Sync as applied, the refresh rate the frame loops pace against ([refreshPacing]), the desktop ceiling and the
     * desktop's precise frame timer, which lets the gameplay loop pace by the display or on the exact V-Sync-off grid.
     */
    override fun framePacing(): KkDisplayPacing = KkDisplayPacing(
        vsync = vsync,
        refreshHz = pacingRefreshHz,
        maxFrameRateCap = MAX_FRAME_RATE_CAP,
        timer = DesktopFrameTimer.Shared,
    )

    override fun diagnosticRefresh(): HostRefreshReading = diagnosticRefresh(screens.screen(window))

    private fun diagnosticRefresh(screen: DesktopScreen?): HostRefreshReading {
        if (screen == null) return HostRefreshReading(vsync = vsync)
        if (screen.refreshHz > 0) return HostRefreshReading(screen.refreshHz, HostRefreshSource.DISPLAY_MODE, vsync)
        val cached = beforeFullscreen
        if (cached != null && cached.deviceId == screen.deviceId &&
            (windowState.placement == WindowPlacement.Fullscreen || shownFullscreen(window))
        ) return HostRefreshReading(cached.refreshHz, HostRefreshSource.PRE_FULLSCREEN, vsync)
        val cadence = measuredCadence
        if (vsync && cadence != null && cadence.screen == screen && cadence.placement == windowState.placement) {
            return HostRefreshReading(cadence.hz, HostRefreshSource.VSYNC_CADENCE)
        }
        return HostRefreshReading(vsync = vsync)
    }

    /**
     * Reads the refresh rate the frame loops pace against again, as [diagnosticRefresh] reports it for the window's
     * screen: the display mode, the rate cached before fullscreen, then the measured V-Sync cadence. Called on attach,
     * on screen and placement changes, after a cadence measurement and after applying a request.
     */
    internal fun refreshPacing() {
        pacingRefreshHz = diagnosticRefresh(screens.screen(window)).hz
    }

    /** The window moved to another screen or its screen changed mode. */
    internal fun refreshConfigurationChanged() {
        invalidateMeasuredCadence()
        captureBeforeFullscreen()
        refreshPacing()
    }

    /** Whether the refresh rate has to be measured from frame cadence: V-Sync is on and the screen reports no rate. */
    internal fun refreshCadenceWanted(): Boolean = refreshCadenceWanted(screens.screen(window))

    private fun refreshCadenceWanted(screen: DesktopScreen?): Boolean =
        vsync && screen != null && diagnosticRefresh(screen).hz <= 0

    /** A short, focused-window sample of unpaced frame-clock callbacks when AWT cannot report a rate. */
    internal suspend fun measureRefreshCadence() {
        val attached = window ?: return
        val placement = windowState.placement
        if (measuredCadence?.placement != placement) {
            measuredCadence = null
            refreshPacing()
        }
        val screen = screens.screen(attached) ?: return
        if (!attached.isFocused || !refreshCadenceWanted(screen)) return
        val revision = cadenceRevision
        val hz = measureVsyncCadence() ?: return
        if (window === attached && attached.isFocused && vsync && windowState.placement == placement &&
            cadenceRevision == revision && screens.screen(attached) == screen
        ) {
            measuredCadence = DesktopCadence(screen, placement, hz)
            refreshPacing()
        }
    }

    private fun invalidateMeasuredCadence() {
        measuredCadence = null
        cadenceRevisionValue++
    }

    private fun captureBeforeFullscreen() {
        if (shownFullscreen(window)) return
        val screen = screens.screen(window) ?: return
        if (screen.refreshHz > 0) beforeFullscreen = screen
    }

    override fun apply(request: HostDisplayRequest, force: Boolean) {
        val previous = last
        if (!force && request == previous) return
        last = request
        var reopen = false
        if (!pacing.overridden && request.vsync != vsync) {
            // Skiko reads the property when the next window creates its redrawer.
            vsync = request.vsync
            invalidateMeasuredCadence()
            setSystemProperty(DesktopFramePacing.SKIKO_VSYNC_PROPERTY, request.vsync.toString())
            reopen = true
        }
        val modeChanged = previous?.windowMode != request.windowMode
        if (modeChanged) invalidateMeasuredCadence()
        val sizeChanged = previous == null || previous.windowWidthDp != request.windowWidthDp ||
            previous.windowHeightDp != request.windowHeightDp
        if (force || modeChanged || sizeChanged && request.windowMode == HostWindowMode.WINDOWED) place(request)
        val nextUndecorated = request.windowMode == HostWindowMode.BORDERLESS
        if (nextUndecorated != undecorated) {
            // Decoration cannot change on a shown frame: the window opens again with the new value.
            undecorated = nextUndecorated
            reopen = true
        }
        if (reopen) generation++
        refreshPacing()
    }

    /**
     * On a frame of the shown window: once it has left fullscreen (the platform has restored its
     * saved bounds by then), the pending windowed preset applies, centred.
     */
    fun settle() {
        val pending = pendingWindowed ?: return
        if (shownFullscreen(window)) return
        pendingWindowed = null
        placeWindowed(pending)
    }

    override val liveWindow: HostLiveWindow?
        get() {
            val size = windowState.size.takeIf { it.isSpecified } ?: return null
            val mode = when {
                windowState.placement == WindowPlacement.Fullscreen -> HostWindowMode.FULLSCREEN
                undecorated -> HostWindowMode.BORDERLESS
                else -> HostWindowMode.WINDOWED
            }
            return HostLiveWindow(mode, size.width.value.roundToInt(), size.height.value.roundToInt())
        }

    private fun place(request: HostDisplayRequest) {
        pendingWindowed = null
        when (request.windowMode) {
            HostWindowMode.WINDOWED -> if (shownFullscreen(window)) {
                // Compose applies size and position before placement, so they would land while the
                // window is still fullscreen and the platform's restore would then replace them.
                windowState.placement = WindowPlacement.Floating
                pendingWindowed = request
            } else {
                placeWindowed(request)
            }
            HostWindowMode.BORDERLESS -> {
                windowState.placement = WindowPlacement.Floating
                screens.screen(window)?.bounds?.let { bounds ->
                    windowState.position = WindowPosition(bounds.x.dp, bounds.y.dp)
                    windowState.size = DpSize(bounds.width.dp, bounds.height.dp)
                }
            }
            HostWindowMode.FULLSCREEN -> {
                captureBeforeFullscreen()
                // Native fullscreen on macOS, GraphicsDevice fullscreen elsewhere.
                windowState.placement = WindowPlacement.Fullscreen
            }
        }
    }

    private fun placeWindowed(request: HostDisplayRequest) {
        val usable = screens.screen(window)?.usable
        windowState.placement = WindowPlacement.Floating
        windowState.size = DpSize(
            (usable?.width?.let { minOf(it, request.windowWidthDp) } ?: request.windowWidthDp).dp,
            (usable?.height?.let { minOf(it, request.windowHeightDp) } ?: request.windowHeightDp).dp,
        )
        windowState.position = WindowPosition(Alignment.Center)
    }

    internal companion object {
        private const val DEFAULT_WIDTH_DP = 1280
        private const val DEFAULT_HEIGHT_DP = 800

        /** The desktop's top frame-rate limit: Settings offers no Unlimited and a stored Unlimited runs at this rate. */
        const val MAX_FRAME_RATE_CAP = 240
    }
}

/** One screen in AWT user-space units (dp in Compose Desktop): its bounds, the part outside bars and docks, its refresh rate. */
internal data class DesktopScreen(val bounds: Rectangle, val usable: Rectangle, val refreshHz: Int, val deviceId: String = "")

/** The screen a window is on, or the default screen before a window exists; null when headless. */
internal fun interface DesktopScreens {
    fun screen(window: Window?): DesktopScreen?
}

internal object AwtDesktopScreens : DesktopScreens {
    override fun screen(window: Window?): DesktopScreen? {
        if (GraphicsEnvironment.isHeadless()) return null
        val configuration = window?.graphicsConfiguration
            ?: GraphicsEnvironment.getLocalGraphicsEnvironment().defaultScreenDevice.defaultConfiguration
        val bounds = configuration.bounds
        val insets = Toolkit.getDefaultToolkit().getScreenInsets(configuration)
        val usable = Rectangle(
            bounds.x + insets.left,
            bounds.y + insets.top,
            bounds.width - insets.left - insets.right,
            bounds.height - insets.top - insets.bottom,
        )
        val refresh = configuration.device.displayMode?.refreshRate ?: DisplayMode.REFRESH_RATE_UNKNOWN
        return DesktopScreen(bounds, usable, refresh.coerceAtLeast(0), configuration.device.getIDstring())
    }
}

private data class DesktopCadence(val screen: DesktopScreen, val placement: WindowPlacement, val hz: Int)

private suspend fun measureVsyncCadence(): Int? = withTimeoutOrNull(2_000) {
    // Let the window's placement settle before requesting a finite burst of callbacks.
    delay(250)
    val intervals = LongArray(31)
    var previous = withFrameNanos { it }
    for (index in intervals.indices) {
        val next = withFrameNanos { it }
        intervals[index] = next - previous
        previous = next
    }
    estimateDesktopCadenceHz(intervals)
}

/** Estimates callback cadence only when at least three quarters of the samples cluster around the median. */
internal fun estimateDesktopCadenceHz(intervals: LongArray): Int? {
    if (intervals.isEmpty()) return null
    intervals.sort()
    val median = intervals[intervals.size / 2]
    if (median !in 1_000_000L..50_000_000L) return null
    val tolerance = median / 10
    val clustered = intervals.count { it in (median - tolerance)..(median + tolerance) }
    if (clustered * 4 < intervals.size * 3) return null
    return (1_000_000_000.0 / median).roundToInt()
}
