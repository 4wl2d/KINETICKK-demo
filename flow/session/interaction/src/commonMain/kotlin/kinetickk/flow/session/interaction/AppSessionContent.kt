// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package kinetickk.flow.session.interaction

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.runtime.CompositionLocalProvider
import kinetickk.foundation.design.Kk
import kinetickk.foundation.design.KkFramePacer
import kinetickk.foundation.design.KkShutter
import kinetickk.foundation.design.LocalAppLanguage
import kinetickk.foundation.design.LocalCrashDiagnostics
import kinetickk.foundation.diagnostics.CrashDiagnostics
import kinetickk.foundation.design.LocalKkFrameRateCap
import kinetickk.foundation.design.LocalKkInterfaceScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import kinetickk.ball.profile.api.DEFAULT_INTERFACE_SCALE_PERCENT
import kinetickk.foundation.design.KkRolePalette
import kinetickk.foundation.design.LocalKkRolePalette
import kinetickk.foundation.design.awaitTimeline
import kinetickk.foundation.design.drawKkShutter
import kinetickk.foundation.design.kkCoveredFrameRateCap
import kinetickk.foundation.design.pacing.KkDisplayPacing
import kinetickk.foundation.design.pacing.LocalKkDisplayPacing
import kinetickk.foundation.design.pacing.kkPacingFrameRateCap
import kinetickk.foundation.common.localization.AppLanguage
import kinetickk.ball.profile.interaction.settings.api.SettingsOutput
import kinetickk.ball.profile.interaction.settings.api.appliedInterfaceScalePercent
import kinetickk.ball.profile.interaction.settings.api.offeredInterfaceScales
import kinetickk.ball.profile.api.ColorVision
import kinetickk.ball.profile.api.PlayerPreferences
import kinetickk.ball.profile.api.ProfileReadPort
import kinetickk.ball.profile.api.ProfileQuery
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import kinetickk.ball.gameplay.api.GameplayQuery
import kinetickk.ball.gameplay.interaction.GameplayPresentation
import kinetickk.ball.profile.api.KeyBindings
import kinetickk.ball.profile.interaction.settings.api.SettingsFeature
import kinetickk.ball.profile.interaction.workshop.api.WorkshopFeature
import kinetickk.ball.profile.interaction.workshop.api.WorkshopTab
import kinetickk.ball.profile.interaction.workshop.api.WorkshopTabRequest
import kinetickk.foundation.design.toBindableKey
import kinetickk.flow.session.api.AppDestination
import kinetickk.flow.session.api.AppSessionPort
import kinetickk.flow.session.api.AppShellProjection
import kinetickk.flow.session.api.SessionAcceptance
import kinetickk.flow.session.api.SessionInteractionPulse
import kinetickk.flow.session.api.SessionLifecycle
import kinetickk.flow.session.api.SessionShortcut
import kinetickk.flow.session.interaction.audio.SessionAudioExecutor
import kinetickk.flow.session.interaction.codex.api.CodexFeature
import kinetickk.flow.session.interaction.codex.api.CodexRunStacks
import kinetickk.flow.session.interaction.home.api.HomeFeature
import kinetickk.flow.session.interaction.home.api.HomeOutput
import kinetickk.flow.session.interaction.profile.api.ProfileUnavailableFeature

/**
 * Shell state that must live as long as the features it addresses. Assembly keeps one beside its
 * features, so a desktop window opened again (its composition rebuilt, the owner kept) never reuses a
 * Workshop request serial the Workshop has already applied.
 */
class AppShellMemory {
    // The tab the shell last asked the Workshop to show; a new serial makes a repeated request apply again.
    internal var workshopRequest: WorkshopTabRequest? by mutableStateOf(null)
}

/** Renders the closed AppSession route projection and translates UI outputs into Session Pulses. */
@Composable
fun AppSessionContent(
    sessionPort: AppSessionPort,
    audioExecutor: SessionAudioExecutor,
    gameplayPresentation: GameplayPresentation,
    homeFeature: HomeFeature,
    settingsFeature: SettingsFeature,
    workshopFeature: WorkshopFeature,
    codexFeature: CodexFeature,
    profileUnavailableFeature: ProfileUnavailableFeature,
    initialLanguage: AppLanguage = AppLanguage.English,
    profileReadPort: ProfileReadPort? = null,
    onLanguageChanged: (AppLanguage) -> Unit = {},
    /** Receives the accepted preferences at start and after every accepted change (display application). */
    onPreferencesChanged: (PlayerPreferences) -> Unit = {},
    /** Touch is the platform's primary input: the smallest interface scale is never applied. */
    touchPrimary: Boolean = false,
    /** Kept by the owner of the features when it outlives this composition (the desktop window). */
    shellMemory: AppShellMemory = remember(sessionPort) { AppShellMemory() },
    /** Closes the game when Home's Quit is pressed (hosts that offer Quit). */
    onQuit: () -> Unit = {},
) {
    val diagnostics = LocalCrashDiagnostics.current
    val state = remember(sessionPort, profileReadPort, shellMemory, diagnostics) {
        // One read of the accepted preferences seeds the language, the palette, the bindings and the display state.
        SessionShellState(sessionPort, shellMemory, diagnostics, profileReadPort?.query(ProfileQuery.GetPreferences)?.preferences,
            initialLanguage)
    }
    // Interface scale: Home and every overlay lay out at a scaled density; the run keeps the raw one.
    val rawDensity = LocalDensity.current
    val windowInfo = LocalWindowInfo.current
    var rootSizeValue by remember { mutableStateOf(windowInfo.containerSize) }
    val storedInterfaceScale = state.preferences?.interfaceScalePercent ?: DEFAULT_INTERFACE_SCALE_PERCENT
    val interfaceScale = remember(storedInterfaceScale, rootSizeValue, rawDensity.density, touchPrimary) {
        // Offered for the window's unscaled size, so the choice never depends on the scale itself.
        val offered = offeredInterfaceScales(
            rootSizeValue.width / rawDensity.density, rootSizeValue.height / rawDensity.density, touchPrimary,
        )
        appliedInterfaceScalePercent(storedInterfaceScale, offered) / 100f
    }
    val menuDensity = remember(rawDensity, interfaceScale) { Density(rawDensity.density * interfaceScale, rawDensity.fontScale) }
    val focusRequester = remember(sessionPort) { FocusRequester() }
    val shellValue = state.shell
    SideEffect(shellValue) {
        if (diagnostics.enabled) diagnostics.context("session.committed") { shellValue.toString() }
    }
    SideEffect(sessionPort, shellValue.base, shellValue.overlay) {
        // Establish fallback routing after an overlay disposes its focused control.
        // Home can focus a semantic action after layout; Gameplay restores its own focus.
        if (shellValue.base == AppDestination.Home && shellValue.overlay == null) {
            focusRequester.requestFocus()
        }
    }
    val languageValue = state.language
    val preferencesValue = state.preferences
    SideEffect(languageValue, onLanguageChanged) { onLanguageChanged(languageValue) }
    SideEffect(preferencesValue, onPreferencesChanged) { preferencesValue?.let(onPreferencesChanged) }

    val normalInputEnabled = shellValue.normalInputEnabled
    // Screen changes: the new destination composes and takes input at once; the previous frame
    // (kept for menus) stays on top until the shutter's ink slab covers the screen.
    // The stored limit as the platform paces it: under the desktop's ceiling a stored Unlimited runs at 240, and under
    // V-Sync a limit that keeps every refresh is 0, display-paced.
    val frameRateCap = kkPacingFrameRateCap(preferencesValue?.frameRateCap ?: 0, LocalKkDisplayPacing.current)
    val transition = rememberSessionTransition(sessionPort, shellValue.base, shellValue.overlay, frameRateCap)
    val snapshot = rememberGraphicsLayer()
    CompositionLocalProvider(
        LocalAppLanguage provides languageValue,
        LocalKkRolePalette provides state.colorVision.rolePalette(),
        LocalKkFrameRateCap provides frameRateCap,
        LocalKkInterfaceScale provides interfaceScale,
        // Home and every overlay lay out at the scaled logical size, like a zoom.
        LocalDensity provides menuDensity,
    ) {
        val roles = LocalKkRolePalette.current
        Box(
            modifier = Modifier
                .fillMaxSize()
                .onSizeChanged { rootSizeValue = it }
                .sessionTransitionLayer(transition, snapshot, roles)
                .focusRequester(focusRequester)
                .sessionShellKeys({ state.shell }, audioExecutor, state::gameplayFirst, state::dispatchKey, state::dispatch)
                .focusable(),
        ) {
            SessionBase(shellValue, normalInputEnabled, homeFeature, gameplayPresentation, rawDensity, onQuit, state::dispatch)
            SessionOverlay(shellValue, normalInputEnabled, settingsFeature, workshopFeature, codexFeature,
                shellMemory.workshopRequest, gameplayPresentation, state::onSettingsOutput, state::dispatch)

            if (shellValue.lifecycle.showsProfileUnavailable()) {
                profileUnavailableFeature.Content()
            }
        }
    }
}

/**
 * The shell's state for one session port: the shown projection and the accepted preferences it presents (language,
 * colour vision, key bindings, display). Its handlers run outside composition and dispatch Session pulses.
 */
private class SessionShellState(
    private val sessionPort: AppSessionPort,
    private val shellMemory: AppShellMemory,
    private val diagnostics: CrashDiagnostics,
    initialPreferences: PlayerPreferences?,
    initialLanguage: AppLanguage,
) {
    var shell by mutableStateOf(sessionPort.shell())
        private set
    var language by mutableStateOf(initialPreferences?.language ?: initialLanguage)
        private set
    var colorVision by mutableStateOf(initialPreferences?.colorVision ?: ColorVision.DEFAULT)
        private set

    // Keys bound to gameplay actions reach a running run before the session shortcuts.
    private var bindings by mutableStateOf(initialPreferences?.keyBindings ?: KeyBindings.DEFAULT)

    /** The accepted preferences as last read; display options, frame pacing and interface scale follow them. */
    var preferences by mutableStateOf(initialPreferences)
        private set

    fun dispatch(pulse: SessionInteractionPulse): Boolean {
        val traced = diagnostics.enabled
        val before = shell
        if (traced) {
            diagnostics.context("session.before-input") { before.toString() }
            diagnostics.event("session.input", pulse.toString())
        }
        val accepted = sessionPort.accept(pulse) == SessionAcceptance.Accepted
        shell = sessionPort.shell()
        val after = shell
        if (traced) diagnostics.context("session.committed") { after.toString() }
        return accepted
    }

    // An alias or a tabbed Home entry names its tab before Session opens the Workshop (directly, or
    // once a running run has paused); a refusal leaves the previous request in place.
    private fun openWorkshop(tab: WorkshopTab): Boolean {
        val previous = shellMemory.workshopRequest
        shellMemory.workshopRequest = WorkshopTabRequest(tab, (previous?.serial ?: 0) + 1)
        return dispatch(SessionInteractionPulse.OpenOverlay(AppDestination.Workshop)).also { accepted ->
            if (!accepted) shellMemory.workshopRequest = previous
        }
    }

    fun dispatchKey(key: Key): Boolean {
        if (key.isRunModeKey(shell.base)) return dispatch(SessionInteractionPulse.SelectRunModeRequested(shell.runMode.toggled()))
        key.toSessionShortcut()?.let { return dispatch(SessionInteractionPulse.ShortcutObserved(it)) }
        return key.toWorkshopAlias(shell.base)?.let(::openWorkshop) ?: false
    }

    // While a run is on screen without an overlay, a key bound to a gameplay action goes to gameplay
    // first; gameplay consumes it only in the phases its action applies, and the rest bubbles back.
    fun gameplayFirst(key: Key): Boolean =
        shell.base == AppDestination.Gameplay && shell.overlay == null &&
            key.toBindableKey()?.let(bindings::action) != null

    fun onSettingsOutput(output: SettingsOutput) {
        when (output) {
            is SettingsOutput.PreferencesChanged -> {
                val accepted = output.preferences
                language = accepted.language
                colorVision = accepted.colorVision
                bindings = accepted.keyBindings
                preferences = accepted
            }
            SettingsOutput.Back -> output.toSessionPulse()?.let { dispatch(it) }
        }
    }
}

/** The base destination: Home or the running run. Each overlay supplies its own opaque background. */
@Composable
private fun SessionBase(
    shell: AppShellProjection,
    normalInputEnabled: Boolean,
    homeFeature: HomeFeature,
    gameplayPresentation: GameplayPresentation,
    rawDensity: Density,
    onQuit: () -> Unit,
    dispatch: (SessionInteractionPulse) -> Boolean,
) {
    val covered = shell.overlay != null && normalInputEnabled
    val frameRateCap = LocalKkFrameRateCap.current
    val baseFrameRateCap = if (covered) kkCoveredFrameRateCap(frameRateCap) else frameRateCap
    // A hidden base never paces against the display: its loops keep the plain covered cap.
    val baseDisplay = if (covered) COVERED_DISPLAY_PACING else LocalKkDisplayPacing.current
    val inputEnabled = normalInputEnabled && shell.overlay == null
    Box(Modifier.fillMaxSize().drawWithContent { if (!covered) drawContent() }) {
        CompositionLocalProvider(LocalKkFrameRateCap provides baseFrameRateCap, LocalKkDisplayPacing provides baseDisplay) {
            when (shell.base) {
                AppDestination.Home -> homeFeature.Content(
                    inputEnabled = inputEnabled,
                    runMode = shell.runMode,
                    onOutput = { output -> if (output == HomeOutput.Quit) onQuit() else output.toSessionPulse()?.let { dispatch(it) } },
                )
                // The run keeps the raw density: the interface scale never changes the visible arena.
                AppDestination.Gameplay -> CompositionLocalProvider(LocalDensity provides rawDensity) {
                    gameplayPresentation.Content(
                        inputEnabled = inputEnabled,
                        onOutput = { output -> dispatch(output.toSessionPulse()) },
                    )
                }
                AppDestination.Settings,
                AppDestination.Workshop,
                AppDestination.Codex,
                -> error("Only Home and Gameplay may be base destinations")
            }
        }
    }
}

private val COVERED_DISPLAY_PACING = KkDisplayPacing()

/** The overlay feature over the base, while normal input is enabled. */
@Composable
private fun SessionOverlay(
    shell: AppShellProjection,
    normalInputEnabled: Boolean,
    settingsFeature: SettingsFeature,
    workshopFeature: WorkshopFeature,
    codexFeature: CodexFeature,
    workshopRequest: WorkshopTabRequest?,
    gameplayPresentation: GameplayPresentation,
    onSettingsOutput: (SettingsOutput) -> Unit,
    dispatch: (SessionInteractionPulse) -> Boolean,
) {
    when (shell.overlay.takeIf { normalInputEnabled }) {
        null -> Unit
        AppDestination.Settings -> settingsFeature.Content(
            routeToken = shell.routeToken,
            onOutput = onSettingsOutput,
        )
        AppDestination.Workshop -> workshopFeature.Content(
            routeToken = shell.routeToken,
            tabRequest = workshopRequest,
            characterSelectionAvailable = shell.characterSelectionAvailable,
            activeRunWeapon = activeGameplayWeapon(shell, gameplayPresentation),
            onOutput = { output -> dispatch(output.toSessionPulse()) },
        )
        AppDestination.Codex -> codexFeature.Content(
            runStacks = currentRunStacks(shell, gameplayPresentation),
            onOutput = { output -> dispatch(output.toSessionPulse()) },
        )
        AppDestination.Home,
        AppDestination.Gameplay,
        -> error("Base destinations cannot be overlays")
    }
}

/** Remembers the shown routes and drives their transition at the selected frame cap. */
@Composable
private fun rememberSessionTransition(
    sessionPort: AppSessionPort,
    base: AppDestination,
    overlay: AppDestination?,
    frameRateCap: Int,
): SessionTransitionState {
    val transition = remember(sessionPort) { SessionTransitionState() }
    SideEffect(base, overlay) {
        transition.show(base, overlay)
    }
    val generation = transition.id
    LaunchedEffect(transition, generation) {
        transition.animate(generation, frameRateCap)
    }
    return transition
}

/** Screen changes: records the shown menu frames, then draws the shutter or the fade from ink over the next screen. */
private fun Modifier.sessionTransitionLayer(
    transition: SessionTransitionState,
    snapshot: GraphicsLayer,
    roles: KkRolePalette,
): Modifier = this
    .drawWithContent {
        if (transition.recording) {
            snapshot.record { this@drawWithContent.drawContent() }
            drawLayer(snapshot)
            transition.recorded()
        } else {
            drawContent()
            // An unrecorded screen (the running run) lets go of the previous screen's frame once the shutter is over.
            if (transition.releasesSnapshot()) snapshot.record(IntSize.Zero) {}
        }
        val elapsed = transition.elapsedMs
        when (transition.kind) {
            SessionTransitionKind.NONE -> Unit
            SessionTransitionKind.SHUTTER -> {
                if (elapsed < KkShutter.SWAP_MS) drawLayer(snapshot)
                drawKkShutter(elapsed, roles)
            }
            SessionTransitionKind.CROSSFADE ->
                drawRect(Kk.Ink, alpha = (1f - elapsed / SessionTransitionKind.CROSSFADE.durationMs).coerceIn(0f, 1f))
        }
    }

/**
 * Shell keys: Codex and Settings own their keys, a focused control owns Enter, a key bound to a gameplay
 * action reaches a running run first, and the shell keys left become Session shortcuts.
 */
private fun Modifier.sessionShellKeys(
    shell: () -> AppShellProjection,
    audioExecutor: SessionAudioExecutor,
    gameplayFirst: (Key) -> Boolean,
    dispatchKey: (Key) -> Boolean,
    dispatch: (SessionInteractionPulse) -> Boolean,
): Modifier = this
    .onPreviewKeyEvent { event ->
        // Codex owns text entry, slot activation and its two-step Escape.
        if (shell().overlay == AppDestination.Codex) return@onPreviewKeyEvent false
        // Settings owns slider keys and numeric editing (including Ctrl/Cmd+A).
        if (shell().overlay == AppDestination.Settings) return@onPreviewKeyEvent false
        // Let a focused semantic control own Enter. When focus remains on
        // this root, the bubble handler below preserves the global shortcut.
        if (event.key == Key.Enter) return@onPreviewKeyEvent false
        if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
        if (gameplayFirst(event.key)) return@onPreviewKeyEvent false
        if (!event.key.isShellKey(shell().base)) return@onPreviewKeyEvent false
        audioExecutor.ensureUnlocked()
        dispatchKey(event.key)
    }
    .onKeyEvent { event ->
        if (shell().overlay == AppDestination.Codex) return@onKeyEvent true
        if (shell().overlay == AppDestination.Settings) {
            if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
            if (!event.key.isShellKey(shell().base)) return@onKeyEvent false
            audioExecutor.ensureUnlocked()
            return@onKeyEvent dispatchKey(event.key)
        }
        // A bound key the running run did not consume (its action does not apply now)
        // keeps its session meaning: C still opens the Codex while paused.
        if (event.type == KeyEventType.KeyDown && gameplayFirst(event.key)) {
            if (!event.key.isShellKey(shell().base)) return@onKeyEvent false
            audioExecutor.ensureUnlocked()
            return@onKeyEvent dispatchKey(event.key)
        }
        if (event.key != Key.Enter || event.type != KeyEventType.KeyDown) {
            return@onKeyEvent false
        }
        audioExecutor.ensureUnlocked()
        dispatch(SessionInteractionPulse.ShortcutObserved(SessionShortcut.ENTER))
    }

/** Screen-change styles (Motion board): the shutter, or a 150 ms fade from ink. */
internal enum class SessionTransitionKind(val durationMs: Float) {
    NONE(0f),
    SHUTTER(KkShutter.TOTAL_MS.toFloat()),
    CROSSFADE(150f),
}

/**
 * The in-run screen is not recorded every frame (it is the hot path), so leaving it fades the
 * next screen in; menus keep their last frame for the shutter to cover.
 */
internal fun sessionRecordsSnapshot(base: AppDestination, overlay: AppDestination?): Boolean =
    base != AppDestination.Gameplay || overlay != null

/** Chooses how the shell moves from the shown destinations to the next ones. */
internal fun sessionTransitionKind(
    previousBase: AppDestination?,
    previousOverlay: AppDestination?,
    nextBase: AppDestination,
    nextOverlay: AppDestination?,
    snapshotAvailable: Boolean,
): SessionTransitionKind = when {
    previousBase == null || (previousBase == nextBase && previousOverlay == nextOverlay) -> SessionTransitionKind.NONE
    snapshotAvailable -> SessionTransitionKind.SHUTTER
    else -> SessionTransitionKind.CROSSFADE
}

/**
 * Draw-side transition state. [recording] and the recorded frame status are read and written on the
 * draw/apply thread only; [kind], [elapsedMs] and [id] are snapshot state so the overlay redraws.
 */
internal class SessionTransitionState {
    private enum class RecordedFrame { EMPTY, CURRENT, PREVIOUS }

    private var shownBase: AppDestination? = null
    private var shownOverlay: AppDestination? = null
    private var recordedFrame = RecordedFrame.EMPTY
    var kind by mutableStateOf(SessionTransitionKind.NONE)
        private set
    var elapsedMs by mutableFloatStateOf(0f)
        private set
    var id by mutableIntStateOf(0)
        private set

    /** True while the shown screen's frames are recorded (menus, not during a shutter). */
    val recording: Boolean
        get() = kind != SessionTransitionKind.SHUTTER && shownBase?.let { sessionRecordsSnapshot(it, shownOverlay) } == true

    fun show(base: AppDestination, overlay: AppDestination?) {
        if (base == shownBase && overlay == shownOverlay) return
        val previousBase = shownBase
        // A change during a running shutter has no fresh frame of the shown screen: fade instead.
        val snapshotAvailable = previousBase != null && sessionRecordsSnapshot(previousBase, shownOverlay) &&
            recordedFrame == RecordedFrame.CURRENT && kind != SessionTransitionKind.SHUTTER
        val next = sessionTransitionKind(previousBase, shownOverlay, base, overlay, snapshotAvailable)
        shownBase = base
        shownOverlay = overlay
        // The layer still owns its picture, but only a new recording will match the current route.
        if (recordedFrame == RecordedFrame.CURRENT) recordedFrame = RecordedFrame.PREVIOUS
        if (next != SessionTransitionKind.NONE) {
            kind = next
            elapsedMs = 0f
            id++
        }
    }

    fun recorded() {
        recordedFrame = RecordedFrame.CURRENT
    }

    /** True once after a transition into an unrecorded screen ended: the snapshot's picture is no longer needed. */
    fun releasesSnapshot(): Boolean {
        if (kind != SessionTransitionKind.NONE || recording || recordedFrame == RecordedFrame.EMPTY) return false
        recordedFrame = RecordedFrame.EMPTY
        return true
    }

    fun finish() {
        kind = SessionTransitionKind.NONE
    }

    suspend fun animate(generation: Int, frameRateCap: Int) {
        if (generation != id || kind == SessionTransitionKind.NONE) return
        KkFramePacer(frameRateCap).awaitTimeline((kind.durationMs * 1_000_000f).toLong()) { elapsed ->
            // A new route can be applied after this frame callback, before the effect is cancelled.
            if (generation != id) return
            elapsedMs = elapsed / 1_000_000f
        }
        if (generation == id) finish()
    }
}

/** Presentation projection of the accepted Color vision preference onto the role palette. */
internal fun ColorVision.rolePalette(): KkRolePalette = when (this) {
    ColorVision.DEFAULT -> KkRolePalette.Default
    ColorVision.PROTAN -> KkRolePalette.Protan
    ColorVision.DEUTAN -> KkRolePalette.Deutan
    ColorVision.TRITAN -> KkRolePalette.Tritan
    ColorVision.MONO -> KkRolePalette.Mono
}

internal fun SessionLifecycle.showsProfileUnavailable(): Boolean =
    this == SessionLifecycle.BOOTSTRAP_UNAVAILABLE

internal fun activeGameplayWeapon(
    shell: AppShellProjection,
    gameplayPresentation: GameplayPresentation,
) = if (shell.base == AppDestination.Gameplay) {
    gameplayPresentation.activePresentation()?.query(GameplayQuery.GetActiveWeapon)?.weapon
} else {
    null
}

internal fun currentRunStacks(
    shell: AppShellProjection,
    gameplayPresentation: GameplayPresentation,
): CodexRunStacks = if (shell.base == AppDestination.Gameplay) {
    val build = gameplayPresentation.activePresentation()?.query(GameplayQuery.GetBuildSummary)
    CodexRunStacks(
        itemStacks = build?.itemStacks ?: kinetickk.foundation.collections.immutableListOf(),
        build = build,
    )
} else {
    CodexRunStacks()
}
