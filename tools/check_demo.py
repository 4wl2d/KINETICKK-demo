#!/usr/bin/env python3
# SPDX-FileCopyrightText: 2026 Vladislav Tomilov
# SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

"""Check the public source inventory and files packaged by the demo builds."""

import argparse
import hashlib
import os
from pathlib import Path, PurePosixPath
import re
import subprocess
import sys
from zipfile import BadZipFile, ZipFile


ROOT = Path(__file__).resolve().parents[1]
ASSET_LIST = "tools/demo-assets.sha256"
LICENSE_FILES = {
    "LICENSE", "NOTICE", "LICENSES/README.md", "LICENSES/Apache-2.0.txt",
    "LICENSES/BSD-3-Clause-Skia.txt", "LICENSES/Skiko-NOTICE.txt",
    "LICENSES/skia/README.ijg", "LICENSES/skia/expat.txt", "LICENSES/skia/freetype.txt",
    "LICENSES/skia/harfbuzz.txt", "LICENSES/skia/icu.txt", "LICENSES/skia/libjpeg-turbo.md",
    "LICENSES/skia/libpng.txt", "LICENSES/skia/libwebp.txt", "LICENSES/skia/zlib.txt",
    "LICENSES/wasm/emscripten.txt", "LICENSES/wasm/libcxx.txt", "LICENSES/wasm/libcxxabi.txt",
    "LICENSES/wasm/musl.txt",
}
SOURCE_ROOTS = (
    "app/desktop|app/shared|app/web|ball/content/(?:api|impl)|"
    "ball/(?:profile|gameplay)/(?:api|impl|interaction|nucleus)|"
    "flow/session/(?:api|impl|interaction|nucleus)|foundation/(?:common|design)|"
    "resource/audio/(?:api|impl)"
)
SOURCE_SETS = "commonMain|commonTest|desktopMain|desktopTest|wasmJsMain|wasmJsTest|main|test"
PACKAGE_SEGMENTS = r"(?:[A-Za-z][A-Za-z0-9_]*/)*"
SOURCE_PATHS = tuple(re.compile(pattern) for pattern in (
    r"(?:README(?:\.ru)?\.md|AGENTS\.md)",
    r"\.(?:gitattributes|gitignore)",
    r"docs/(?:DEVELOPMENT|DEMO|ASSETS)\.md",
    r"\.github/(?:workflows/ci\.yml|scripts/(?:test_)?web_release\.py)",
    r"tools/(?:check_demo\.py|demo-assets\.sha256)",
    r"(?:settings\.gradle\.kts|build\.gradle\.kts|gradle\.properties|gradlew(?:\.bat)?)",
    r"gradle/(?:libs\.versions\.toml|wrapper/gradle-wrapper\.properties)",
    r"build-logic/(?:build\.gradle\.kts|settings\.gradle\.kts|source-structure-baseline\.tsv)",
    rf"build-logic/src/(?:main|test)/kotlin/{PACKAGE_SEGMENTS}[A-Za-z][A-Za-z0-9_.-]*\.kt(?:s)?",
    rf"(?:{SOURCE_ROOTS})/build\.gradle\.kts",
    rf"(?:{SOURCE_ROOTS})/src/(?:{SOURCE_SETS})/kotlin/{PACKAGE_SEGMENTS}[A-Za-z][A-Za-z0-9_.]*\.kt",
    r"app/desktop/release\.pro",
    r"app/web/src/wasmJsMain/resources/(?:index\.html|boot\.js)",
))
ASSET_PATHS = re.compile(
    r"gradle/wrapper/gradle-wrapper\.jar|"
    r"foundation/design/src/commonMain/composeResources/(?:font/[a-z_]+\.ttf|files/[a-z-]+-OFL\.txt)"
)
PRIVATE_CONTENT = {
    "private remote": re.compile(rb"github\.com[/:]4wl2d/KINETICKK(?=$|[./'\"\s])", re.I),
    "private license identifier": re.compile(rb"LicenseRef-" + rb"KINETICKK-Proprietary"),
    "local user path": re.compile(rb"(?:/Users/|/home/)4wl2d(?:/|\b)"),
    "internal graphics tooling reference": re.compile(rb"(?:tools/)(?:vfx|readme|fonts)/"),
    "credential material": re.compile(
        rb"-----BEGIN (?:RSA |DSA |EC |OPENSSH |ENCRYPTED )?PRIVATE KEY-----|"
        rb"\b(?:gh[pousr]_[A-Za-z0-9]{36}|github_pat_[A-Za-z0-9_]{60,})\b"
    ),
}
PRIVATE_PATH = re.compile(
    r"(?:^|/)(?:debug|diagnostics|internalTools|rebirth|store)(?:/|$)|"
    r"(?:^|/)(?:PixelFxAtlas|PixelUiAtlas|DebugMode|[A-Za-z]*(?:Rebirth|Reborn)[A-Za-z]*)\.(?:kt|class)$",
    re.I,
)
DEMO_DIAGNOSTICS_SOURCE = re.compile(
    r"foundation/common/src/commonMain/kotlin/kinetickk/foundation/diagnostics/"
    r"(?:CrashDiagnostics|DiagnosticsBuild)\.kt"
)
DEMO_DIAGNOSTICS_CLASSES = re.compile(
    r"kinetickk/foundation/diagnostics/(?:CrashDiagnostics(?:\$[A-Za-z]+)*|DiagnosticsBuildKt|"
    r"CrashDiagnosticsKt|PerformanceDiagnostics(?:\$[A-Za-z]+)*|DiagnosticPhase)\.class"
)
COMPOSE_PREFIX = "composeResources/kinetickk.foundation.design.generated.resources/"
WEB_WASM = re.compile(r"(?:[0-9a-f]{20}|kinetickk|skiko)\.wasm")
WEB_ROOT = "app/web/src/wasmJsMain/resources/"
WASM_HEADER = b"\x00asm\x01\x00\x00\x00"
ABSOLUTE_GAME_IMPORT = re.compile(rb"File '<kinetickk[\w.:-]*>(?:[A-Za-z]:)?[/\\]")
REVIEWED_JAR_RESOURCES = {
    ".gif", ".ico", ".icns", ".jpeg", ".jpg", ".ogg", ".otf", ".png", ".svg", ".ttf", ".wav", ".webp",
}
FORBIDDEN_JAR_RESOURCES = {".jar", ".kt", ".kts", ".map", ".zip"}


def source_paths():
    args = ["git", "ls-files", "--cached", "--others", "--exclude-standard", "-z"]
    entries = subprocess.check_output(args, cwd=ROOT).split(b"\x00")
    return sorted({os.fsdecode(entry) for entry in entries if entry})


def check_markers(name, data):
    for label, marker in PRIVATE_CONTENT.items():
        if marker.search(data):
            raise ValueError(f"{name}: contains {label}")


def check_compiled_markers(name, data):
    check_markers(name, data)
    if ABSOLUTE_GAME_IMPORT.search(data):
        raise ValueError(f"{name}: absolute Kotlin/Wasm import path")
    for path in {str(ROOT) + os.sep, ROOT.as_posix() + "/", ROOT.as_uri() + "/"}:
        if any(path.encode(encoding) in data for encoding in ("utf-8", "utf-16le")):
            raise ValueError(f"{name}: contains the absolute build checkout path")


def require_file(path):
    if path.is_symlink() or path.resolve() != path or not path.is_file():
        raise ValueError(f"{path}: expected a regular file at its declared path")


def load_approved_assets():
    path = ROOT / ASSET_LIST
    require_file(path)
    assets = {}
    for line in path.read_text(encoding="utf-8").splitlines():
        sha256, name = line.split("  ", 1)
        if not re.fullmatch(r"[0-9a-f]{64}", sha256) or not ASSET_PATHS.fullmatch(name) or name in assets:
            raise ValueError(f"{ASSET_LIST}: invalid or duplicate asset {name}")
        assets[name] = sha256
    if "gradle/wrapper/gradle-wrapper.jar" not in assets:
        raise ValueError(f"{ASSET_LIST}: Gradle wrapper checksum is missing")
    return assets


def check_source():
    assets = load_approved_assets()
    names = source_paths()
    required = {"README.md", ASSET_LIST, *LICENSE_FILES, *assets}
    missing = required.difference(names)
    if missing:
        raise ValueError(f"source inventory is missing {', '.join(sorted(missing))}")
    failures = []
    for name in names:
        path = ROOT / name
        try:
            require_file(path)
            if name in assets:
                if hashlib.sha256(path.read_bytes()).hexdigest() != assets[name]:
                    raise ValueError(f"{name}: approved asset checksum changed")
            else:
                check_source_path(name)
                data = path.read_bytes()
                if b"\0" in data:
                    raise ValueError(f"{name}: binary file outside the approved asset inventory")
                data.decode("utf-8")
                check_markers(name, data)
                if name.endswith("/DiagnosticsBuild.kt") and not re.search(
                    rb"^const val DIAGNOSTICS_ENABLED: Boolean = false$", data, re.M
                ):
                    raise ValueError(f"{name}: diagnostics must stay disabled")
        except (OSError, ValueError) as error:
            failures.append(str(error))
    if failures:
        raise ValueError("\n".join(failures))
    print(f"Checked {len(names)} source files and {len(assets)} approved assets.")
    return assets


def check_source_path(name):
    if PRIVATE_PATH.search(name) and not DEMO_DIAGNOSTICS_SOURCE.fullmatch(name):
        raise ValueError(f"{name}: private feature or development path")
    if name not in LICENSE_FILES and not any(pattern.fullmatch(name) for pattern in SOURCE_PATHS):
        raise ValueError(f"{name}: outside the demo source inventory")


def shared_resources(assets):
    packages = {f"META-INF/{name}": ROOT / name for name in LICENSE_FILES}
    for name in assets:
        if "/composeResources/" in name:
            target = COMPOSE_PREFIX + name.split("/composeResources/", 1)[1]
            packages[target] = ROOT / name
    return packages


def check_resources(expected, actual, read):
    missing = expected.keys() - actual
    if missing:
        raise ValueError(f"distribution is missing {', '.join(sorted(missing))}")
    for name, source in expected.items():
        if read(name) != source.read_bytes():
            raise ValueError(f"{name}: packaged bytes differ from {source.relative_to(ROOT)}")


def check_web(directory, expected):
    allowed = dict(expected)
    for relative in ("index.html", "boot.js"):
        allowed[relative] = ROOT / WEB_ROOT / relative
    files = {}
    for path in directory.rglob("*"):
        if path.is_dir() and not path.is_symlink():
            continue
        require_file(path)
        files[path.relative_to(directory).as_posix()] = path
    if "kinetickk.js" not in files:
        raise ValueError("distribution requires kinetickk.js")
    check_resources(allowed, files.keys(), lambda name: files[name].read_bytes())
    wasm = 0
    for name in files.keys() - allowed.keys():
        if name != "kinetickk.js" and not WEB_WASM.fullmatch(name):
            raise ValueError(f"{name}: outside the demo web distribution inventory")
        data = files[name].read_bytes()
        check_compiled_markers(name, data)
        if name == "kinetickk.js":
            if b"sourceMappingURL=" in data:
                raise ValueError(f"{name}: production source map reference")
        else:
            if not data.startswith(WASM_HEADER) or len(data) <= len(WASM_HEADER):
                raise ValueError(f"{name}: invalid WebAssembly header or empty payload")
            wasm += 1
    if not wasm:
        raise ValueError("distribution requires WebAssembly")
    print(f"Checked {len(files)} web files, including {wasm} WebAssembly modules.")


def check_desktop(directory, expected):
    jars = sorted(directory.rglob("app-desktop-*.jar"))
    if len(jars) != 1:
        raise ValueError("desktop distribution requires exactly one app-desktop release JAR")
    require_file(jars[0])
    loose_notices = sorted(p for p in directory.rglob("Legal") if p.is_dir())
    if len(loose_notices) != 1:
        raise ValueError("desktop distribution requires exactly one Legal directory")
    legal_dir = loose_notices[0]
    legal_paths = {p.relative_to(legal_dir).as_posix(): p for p in legal_dir.rglob("*") if not p.is_dir()}
    if legal_paths.keys() != LICENSE_FILES:
        raise ValueError("desktop Legal directory differs from the source notice inventory")
    for name, target in legal_paths.items():
        require_file(target)
        if target.read_bytes() != (ROOT / name).read_bytes():
            raise ValueError(f"Legal/{name}: packaged bytes differ from the source notice")
    with ZipFile(jars[0]) as archive:
        names = [entry.filename for entry in archive.infolist() if not entry.is_dir()]
        if len(names) != len(set(names)):
            raise ValueError("desktop JAR has duplicate members")
        check_resources(expected, set(names), archive.read)
        app_classes = 0
        for name in names:
            path = PurePosixPath(name)
            if path.is_absolute() or ".." in path.parts or "\\" in name:
                raise ValueError(f"{name}: unsafe JAR path")
            if path.suffix.lower() in FORBIDDEN_JAR_RESOURCES:
                raise ValueError(f"{name}: bundled sources, maps or archive")
            is_asset = name.startswith("composeResources/") or path.suffix.lower() in REVIEWED_JAR_RESOURCES
            if is_asset and name not in expected:
                raise ValueError(f"{name}: unapproved packaged resource")
            if name.startswith("META-INF/LICENSES/") and name not in expected:
                raise ValueError(f"{name}: unapproved bundled notice")
            if name.startswith("META-INF/docs/"):
                raise ValueError(f"{name}: unexpected project document in the runtime JAR")
            if name.startswith("kinetickk/"):
                is_private_path = PRIVATE_PATH.search(name) and not DEMO_DIAGNOSTICS_CLASSES.fullmatch(name)
                if is_private_path or path.suffix != ".class":
                    raise ValueError(f"{name}: unapproved application entry")
                check_compiled_markers(name, archive.read(name))
                app_classes += 1
    if not app_classes:
        raise ValueError("desktop JAR contains no application classes")
    print(f"Checked desktop notices, JAR resources and {app_classes} application class entries.")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--web-dist", type=Path)
    parser.add_argument("--desktop-dist", type=Path)
    args = parser.parse_args()
    try:
        expected = shared_resources(check_source())
        if args.web_dist:
            check_web(args.web_dist.resolve(), expected)
        if args.desktop_dist:
            check_desktop(args.desktop_dist.resolve(), expected)
    except (OSError, ValueError, BadZipFile, subprocess.CalledProcessError) as error:
        print(error, file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
