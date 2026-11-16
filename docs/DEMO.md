<!-- SPDX-FileCopyrightText: 2026 Vladislav Tomilov -->
<!-- SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0 -->

# Developing the demo

Use JDK 25 and the Gradle wrapper. Native desktop packaging also needs the JDK's jmods. Browser tests use Chrome or Chromium via `CHROME_BIN`. Run these commands from the repository root.

```sh
./gradlew :app:desktop:run
./gradlew :app:web:wasmJsBrowserDevelopmentRun
./gradlew :build-logic:test verifyArchitecture desktopTest
./gradlew wasmJsBrowserTest :app:web:wasmJsBrowserDistribution
./gradlew :app:desktop:createReleaseDistributable
```

## Edition boundary

The demo is a standalone game with its own Git history, build, versioned releases, and save namespace. The public source tree supplies every gameplay definition, behavior, and asset that the demo can use. Keep playable characters, encounter data, item and relic definitions, upgrade tables, saved IDs, and the matching art and text in agreement across those layers.

The public scope is Orb, Prism, Shard, the items available through the Architect fight, and the permanent Matter upgrades listed in the Workshop. The Architect is the run's final encounter. Keep later progression, gated content, higher upgrade effects, original full-edition assets, authoring tools, and store or signing configuration in the full edition.

Purchases look up the next implemented tier. Loading a save and turning purchased levels into gameplay effects use the same tier data. When adjusting an upgrade, check the last tier in all three paths. Verify that editing the save balance, changing an unlock setting, or supplying a level above the last implemented tier leaves the demo within its catalog.

For each update:

1. Review the demo diff for imported definitions, asset dependencies, local paths, and ownership notices. Port the minimum demo behavior as a new change in this repository. Keep project histories independent.
2. Run the source boundary check and the build and test jobs in [Demo CI](../.github/workflows/ci.yml). Review the exact source or asset paths added if the boundary check fails.
3. Test fresh and existing demo saves, a second run after a win or defeat, the victory message, the roster and item pool, and purchases at and beyond the last Workshop tier. Include a save with edited progression in the test.
4. Run the distribution audit. It checks that both builds carry the demo license, notice, third-party terms, and the expected assets.

```sh
python3 tools/check_demo.py
python3 tools/check_demo.py --web-dist app/web/build/dist/wasmJs/productionExecutable
python3 tools/check_demo.py --desktop-dist app/desktop/build/compose/binaries/main-release/app
```

## Browser release

Demo CI packages the browser build with a `kinetickk-web.json` manifest and content-addressed assets. The manifest records the version tag and the exact commit built. The site watches the latest public demo release and loads assets from its versioned path.

When incrementing the release, update `GAME_VERSION` in [GameVersion.kt](../build-logic/src/main/kotlin/kinetickk/gradle/GameVersion.kt) and the version text in [the home-screen translations](../flow/session/interaction/src/commonMain/kotlin/kinetickk/flow/session/interaction/localization/SessionRedesignText.kt).

For an authorized release, push the reviewed source and wait for both CI jobs
on that commit to pass. Tag the tested commit with the version declared by the
build and create a draft GitHub Release. Download the `demo-web-release-<run-id>`
artifact from the successful run. Use your personal GitHub CLI session to
attach the browser assets and publish the draft. The script verifies that the
tag resolves to the built commit and uploads the readiness manifest last.

```sh
gh run download <run-id> --name demo-web-release-<run-id> --dir web-release
python3 .github/scripts/web_release.py publish --directory web-release
gh release edit <version> --draft=false --latest
```

The site can receive a new version through the same manifest contract after
its demo route is deployed.

For 0.6.0, verify that the public release manifest and the site's `/game/release.json` report version 0.6.0. Open the site's `/game/` entry point and check that the deployed game reaches the menu and starts a run.
