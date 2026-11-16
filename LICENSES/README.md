# Third-party notices

Third-party components and assets retain their own copyright and license terms.
The [project license](../LICENSE) covers original demo material as described in
[NOTICE](../NOTICE).

## Build and runtime components

- The Gradle Wrapper is available under the [Apache License 2.0](Apache-2.0.txt).
  The scripts have their own copyright headers and the wrapper JAR includes the
  license in `META-INF/LICENSE`.
- Kotlin, kotlinx libraries, Compose Multiplatform, AndroidX, and Skiko are
  distributed under the [Apache License 2.0](Apache-2.0.txt). See the [version
  catalog](../gradle/libs.versions.toml) for the dependency versions. The
  [Skiko notice](Skiko-NOTICE.txt) credits code from the Android Open Source
  Project. The browser build adapts Skiko's generated `skiko.mjs` script URL
  initializer to resolve the script location at runtime.
- The Skia rendering library, used through Skiko on desktop and web,
  includes its [BSD 3-Clause license](BSD-3-Clause-Skia.txt) and the notices for
  [Expat](skia/expat.txt), [FreeType](skia/freetype.txt),
  [HarfBuzz](skia/harfbuzz.txt), [ICU](skia/icu.txt),
  [libjpeg-turbo](skia/libjpeg-turbo.md) and its [IJG notice](skia/README.ijg), [libpng](skia/libpng.txt),
  [libwebp](skia/libwebp.txt), and [zlib](skia/zlib.txt).
- Skiko's WebAssembly runtime includes code from [Emscripten](wasm/emscripten.txt),
  [musl](wasm/musl.txt), [libc++](wasm/libcxx.txt), and
  [libc++abi](wasm/libcxxabi.txt).
- A packaged desktop build that bundles a Java runtime carries the runtime's
  license and third-party notices in its `legal/` directory. The Eclipse Temurin
  runtime is distributed under GPL-2.0 with the Classpath Exception. Its source
  is available from the [Adoptium releases](https://adoptium.net/temurin/releases/).

## Bundled fonts

The demo uses static weight instances and subsets of the following fonts, each
under the SIL Open Font License 1.1. Their copyright and license texts are in
the [font resources](../foundation/design/src/commonMain/composeResources/files/)
and accompany the compiled assets.

- Martian Mono (`martian-mono-OFL.txt`)
- Sofia Sans Extra Condensed (`sofia-sans-extra-condensed-OFL.txt`)
- Sofia Sans Semi Condensed (`sofia-sans-semi-condensed-OFL.txt`)
- Unbounded (`unbounded-OFL.txt`)

The Skia notices come from [the Skia revision pinned by Skiko 0.150.2](https://github.com/JetBrains/skia/tree/m150-a3f509bf52)
and the library revisions in its `DEPS` file. The WebAssembly runtime notices
come from [Emscripten 4.0.7](https://github.com/emscripten-core/emscripten/tree/4.0.7).

Distributed builds carry `LICENSE`, `NOTICE`, and `LICENSES/` under `META-INF/`
in the desktop application JAR and web distribution. The desktop app image
also provides these documents in its `resources/Legal/` directory. Review the
notices against the resolved artifacts whenever a dependency or font changes.
