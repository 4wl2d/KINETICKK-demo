// SPDX-FileCopyrightText: 2026 Vladislav Tomilov
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package kinetickk.ball.profile.interaction.localization

import kinetickk.foundation.common.localization.TextResource

internal enum class ProfileText : TextResource {
    PreviousPage,
    NextPage,
    SettingsTitle,
    SettingsGame,
    SettingsSound,
    SettingsGraphics,
    SettingsInterface,
    SettingsDisplay,
    SettingsControls,
    RunStatisticsSide,
    LeftSide,
    RightSide,
    VolumePercent,
    Language,
    Sfx,
    Music,
    MasterVolume,
    SimulationSpeed,
    TextSize,
    ScreenShake,
    Particles,
    DamageNumbers,
    DamageNumberSize,
    DamageNumberFormat,
    DamageColorTiers,
    On,
    Off,
    Low,
    Normal,
    High,
    Small,
    Large,
    Huge,
    Compact,
    Full,
    MaximumSynchrony,
    CoreIntegrity,
    KineticMatter;

    override val english: String get() = EnglishProfileText[this]
    override val russian: String get() = RussianProfileText[this]
}
