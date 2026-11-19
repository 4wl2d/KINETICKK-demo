<!-- SPDX-FileCopyrightText: 2026 Vladislav Tomilov -->
<!-- SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0 -->

<p align="center">
  <img src="docs/assets/readme/hero.svg" alt="KINETICKK Demo, a physics-action roguelite for desktop and the browser" width="100%">
</p>

<p align="center">
  <a href="https://tomilov.tech/game/"><img src="docs/assets/readme/button-play.svg" alt="Play in the browser" height="52"></a>
  <a href="https://github.com/4wl2d/KINETICKK-demo/releases"><img src="docs/assets/readme/button-releases.svg" alt="Demo releases" height="52"></a>
  <a href="docs/DEMO.md"><img src="docs/assets/readme/button-build.svg" alt="Build and release guide" height="52"></a>
</p>

<p align="center">
  <a href="https://github.com/4wl2d/KINETICKK-demo/releases/latest"><img src="https://img.shields.io/github/v/release/4wl2d/KINETICKK-demo?style=flat-square&amp;label=demo&amp;labelColor=151815&amp;color=D8FF3E" alt="Latest public demo release" height="22"></a>
  <a href="https://github.com/4wl2d/KINETICKK-demo/actions/workflows/ci.yml?query=branch%3Amain"><img src="https://img.shields.io/badge/CI-main-E1E0D6?style=flat-square&amp;labelColor=151815" alt="Demo CI runs on main" height="22"></a>
  <a href="LICENSE"><img src="https://img.shields.io/badge/license-PolyForm%20Noncommercial%201.0.0-F1F0E8?style=flat-square&amp;labelColor=151815" alt="License: PolyForm Noncommercial 1.0.0" height="22"></a>
</p>

<h1 align="center">KINETICKK Demo</h1>

KINETICKK is a physics-action roguelite. Your pointer pulls the Core toward it. Steer around hazards, build speed to hit enemies, and pick up items that change how the Core fights.

Play as Circle (Orb), Square (Prism), or Triangle (Shard). Circle is available from the start. Defeat three elites across runs to unlock Square, and land twenty Dash hits to unlock Triangle. There is no run limit. The demo includes all 135 items available through the Architect fight.

Defeating the Architect completes the run. You can play again or return to the menu and spend the Matter you earned in the Workshop. Refit and Honing have three levels per character or weapon. Each of the eight Core Matrix nodes has two ranks. Reborn progression requires the full game.

<p align="center">
  <img src="docs/assets/readme/facts.svg" alt="120 Hz fixed simulation step, 21 Gradle modules, 2 platform hosts (desktop and browser), and 3 playable characters (Orb, Prism, and Shard)" width="100%">
</p>

## How it is built

The browser and desktop hosts share a Kotlin Multiplatform codebase built with Gradle. The browser compiles to Kotlin/Wasm. Desktop runs on the JVM. Both use Compose Multiplatform for the interface.

<p align="center">
  <img src="docs/assets/readme/modules.svg" alt="Module map. The desktop (JVM) and browser (Wasm) hosts depend on app:shared. Under it are the session, gameplay and profile families (impl, interaction, nucleus and api); the content and audio modules (impl and api); and the common and design foundations." width="100%">
</p>

- The [gameplay nucleus](ball/gameplay/nucleus) advances the run in fixed steps at 120 Hz of simulation time. It owns motion, combat, encounters, and rewards. Seeded randomness keeps the same run inputs reproducible.
- The [profile nucleus](ball/profile/nucleus) applies Matter purchases and keeps progression within the demo's implemented tiers. The [session nucleus](flow/session/nucleus) coordinates menus, overlays, and the run lifecycle.
- The [graphics engine](ball/gameplay/interaction) reads the gameplay render model and cues. Compose Canvas draws the world, effects, and HUD, interpolating motion between simulation steps. Menus share the [design primitives](foundation/design). The [audio service](resource/audio) synthesizes music and effects at runtime.

The [app composition](app/shared) connects these pieces to platform input, display, audio, and local storage.

<p align="center">
  <img src="docs/assets/readme/frame.svg" alt="A display frame passes input and elapsed time to the simulation. Fixed steps at 120 Hz produce a render model and an interpolation alpha for the Compose Canvas." width="100%">
</p>

## Privacy

The demo saves preferences and progression locally in your browser or desktop account. It uses separate storage from the full game. The game code sends no gameplay telemetry.

## License

Original demo source, game content, assets, and their copies in distributed demo builds are available under the [PolyForm Noncommercial License 1.0.0](LICENSE). Its terms permit noncommercial use, modification, and redistribution with the required notices. The [NOTICE](NOTICE) explains coverage and attribution. [Third-party components and fonts](LICENSES/README.md) retain their own licenses.
