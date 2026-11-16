#!/usr/bin/env python3
# SPDX-FileCopyrightText: 2026 Vladislav Tomilov
# SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

"""Package the demo's browser distribution as content-addressed release assets."""

import argparse
import hashlib
import json
from pathlib import Path
import re
import subprocess

REPOSITORY = "4wl2d/KINETICKK-demo"
MANIFEST = "kinetickk-web.json"
ASSET_PREFIX = "kinetickk-web-"
VERSION = r"(?:0|[1-9][0-9]{0,8})\.(?:0|[1-9][0-9]{0,8})\.(?:0|[1-9][0-9]{0,8})"
VERSION_SOURCE = Path("build-logic/src/main/kotlin/kinetickk/gradle/GameVersion.kt")
SHA = re.compile(r"[0-9a-f]{40}")
PATH_PART = re.compile(r"[A-Za-z0-9_][A-Za-z0-9._-]*")
MAX_ASSET_SIZE = 25 * 1024 * 1024
MAX_MANIFEST_SIZE = 128 * 1024
MIME = {
    ".html": "text/html; charset=utf-8",
    ".js": "text/javascript; charset=utf-8",
    ".mjs": "text/javascript; charset=utf-8",
    ".wasm": "application/wasm",
    ".css": "text/css; charset=utf-8",
    ".json": "application/json",
    ".png": "image/png",
    ".svg": "image/svg+xml",
    ".woff2": "font/woff2",
    ".ttf": "font/ttf",
    ".otf": "font/otf",
    ".ogg": "audio/ogg",
    ".mp3": "audio/mpeg",
}


def digest(data):
    return hashlib.sha256(data).hexdigest()


def identity(checkout, tag=None):
    source = (checkout / VERSION_SOURCE).read_text(encoding="utf-8")
    version = re.search(rf'^const val GAME_VERSION: String = "({VERSION})"$', source, re.MULTILINE)
    if not version:
        raise ValueError("Demo version must be declared as GAME_VERSION (major.minor.patch)")
    version = version[1]
    if tag is not None and tag != version:
        raise ValueError(f"Release tag {tag!r} does not match demo version {version}")
    revision = subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=checkout, text=True).strip()
    if not SHA.fullmatch(revision):
        raise ValueError("Expected a full Git commit SHA")
    return version, revision


def package(checkout, distribution, output, tag=None):
    tag, revision = identity(checkout, tag)
    if not distribution.is_dir() or distribution.is_symlink():
        raise ValueError("Expected a browser distribution directory")
    legal = [checkout / "LICENSE", checkout / "NOTICE"]
    legal.extend(path for path in (checkout / "LICENSES").rglob("*") if path.is_file())
    for path in legal:
        shipped = distribution / "META-INF" / path.relative_to(checkout)
        if not shipped.is_file() or shipped.read_bytes() != path.read_bytes():
            raise ValueError(f"Browser distribution is missing the exact legal document: {shipped.name}")

    files = {}
    payloads = {}
    for path in sorted(distribution.rglob("*")):
        name = path.relative_to(distribution).as_posix()
        if path.is_symlink():
            raise ValueError(f"Symbolic link in browser distribution: {name}")
        if path.is_dir():
            continue
        suffix = path.suffix
        if any(not PATH_PART.fullmatch(part) for part in name.split("/")) or not path.is_file():
            raise ValueError(f"Unsupported browser asset path: {name}")
        if suffix and not re.fullmatch(r"\.[A-Za-z0-9]+", suffix):
            raise ValueError(f"Unsupported browser asset extension: {name}")
        if not 1 <= path.stat().st_size <= MAX_ASSET_SIZE:
            raise ValueError(f"Browser asset must be nonempty and at most 25 MiB: {name}")
        data = path.read_bytes()
        if suffix == ".map" or (suffix in (".js", ".mjs", ".css") and b"sourceMappingURL=" in data):
            raise ValueError(f"Production source maps are forbidden: {name}")
        if suffix == ".wasm" and not data.startswith(b"\0asm\x01\0\0\0"):
            raise ValueError(f"Invalid Wasm asset: {name}")
        checksum = digest(data)
        asset = f"{ASSET_PREFIX}{checksum}{suffix}"
        files[name] = {"asset": asset, "sha256": checksum, "size": len(data),
                       "contentType": MIME.get(suffix, "text/plain; charset=utf-8")}
        payloads[asset] = data

    if not 1 <= len(files) <= 256:
        raise ValueError("Browser distribution must contain 1–256 files")
    for required in ("index.html", "kinetickk.js"):
        if required not in files:
            raise ValueError(f"Missing browser asset: {required}")
    if not any(name.endswith(".wasm") for name in files):
        raise ValueError("Missing Wasm application")
    document = {"schemaVersion": 1, "tag": tag, "revision": revision, "files": files}
    encoded = (json.dumps(document, sort_keys=True, indent=2) + "\n").encode()
    if len(encoded) > MAX_MANIFEST_SIZE:
        raise ValueError("Web manifest exceeds 128 KiB")

    output.mkdir(parents=True, exist_ok=False)
    for name, data in payloads.items():
        (output / name).write_bytes(data)
    (output / MANIFEST).write_bytes(encoded)
    print(f"Packaged demo {tag} ({revision}) with {len(files)} browser files")
    return document


def gh(*args):
    return subprocess.check_output(["gh", *args], timeout=180)


def tag_commit(tag):
    reference = json.loads(gh("api", f"repos/{REPOSITORY}/git/ref/tags/{tag}"))["object"]
    while reference["type"] == "tag":
        reference = json.loads(gh("api", f"repos/{REPOSITORY}/git/tags/{reference['sha']}"))["object"]
    if reference["type"] != "commit":
        raise ValueError("The release tag must resolve to a Git commit")
    return reference["sha"]


def assets_for_release(release_id):
    pages = json.loads(gh("api", "--paginate", "--slurp",
                          f"repos/{REPOSITORY}/releases/{release_id}/assets?per_page=100"))
    return {asset["name"]: asset for page in pages for asset in page}


def verify_asset(asset, data):
    if (asset["state"] != "uploaded" or asset["size"] != len(data)
            or asset.get("digest") != f"sha256:{digest(data)}"):
        raise ValueError(f"Release asset is incomplete or has different bytes: {asset['name']}")


def publish(checkout, directory):
    manifest = directory / MANIFEST
    manifest_data = manifest.read_bytes()
    document = json.loads(manifest_data)
    tag, revision = identity(checkout, document["tag"])
    if document["schemaVersion"] != 1 or revision != document["revision"]:
        raise ValueError("Packaged manifest does not describe this checkout")

    payloads = {}
    for name, entry in document["files"].items():
        expected_asset = f"{ASSET_PREFIX}{entry['sha256']}{Path(name).suffix}"
        if entry["asset"] != expected_asset or not PATH_PART.fullmatch(expected_asset):
            raise ValueError(f"Invalid release asset filename for {name}")
        data = (directory / expected_asset).read_bytes()
        if len(data) != entry["size"] or digest(data) != entry["sha256"]:
            raise ValueError(f"Packaged release asset changed: {expected_asset}")
        payloads[expected_asset] = data

    if tag_commit(tag) != revision:
        raise ValueError("Release tag moved after the build")
    release = json.loads(gh("release", "view", tag, "--repo", REPOSITORY,
                            "--json", "databaseId,isImmutable,isPrerelease,tagName"))
    if release["isPrerelease"] or release["tagName"] != tag:
        raise ValueError("Expected the demo's stable release for this tag")
    release_id = release["databaseId"]
    assets = assets_for_release(release_id)

    # Reusing verified assets lets a failed upload resume without replacing published bytes.
    for name, data in payloads.items():
        if name in assets:
            verify_asset(assets[name], data)
    missing = [str(directory / name) for name in payloads if name not in assets]
    if MANIFEST in assets:
        if missing:
            raise ValueError("A ready release is missing previously published browser assets")
        verify_asset(assets[MANIFEST], manifest_data)
        print(f"Verified existing demo web release {tag}")
        return
    if release["isImmutable"]:
        raise ValueError("Attach browser assets to the release draft before publishing an immutable release")
    if missing:
        gh("release", "upload", tag, *missing, "--repo", REPOSITORY)
        assets = assets_for_release(release_id)
    for name, data in payloads.items():
        verify_asset(assets[name], data)

    # The site uses the manifest as the readiness marker, after all assets are verified.
    if tag_commit(tag) != revision:
        raise ValueError("Release tag moved during the asset upload")
    gh("release", "upload", tag, str(manifest), "--repo", REPOSITORY)
    verify_asset(assets_for_release(release_id)[MANIFEST], manifest_data)
    print(f"Verified complete demo web release {tag} ({revision})")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    commands = parser.add_subparsers(dest="command", required=True)
    build = commands.add_parser("package")
    build.add_argument("--root", type=Path, required=True)
    build.add_argument("--output", type=Path, required=True)
    build.add_argument("--tag")
    upload = commands.add_parser("publish")
    upload.add_argument("--directory", type=Path, required=True)
    args = parser.parse_args()
    if args.command == "package":
        package(Path.cwd(), args.root, args.output, args.tag)
    else:
        publish(Path.cwd(), args.directory)
