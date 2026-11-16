# SPDX-FileCopyrightText: 2026 Vladislav Tomilov
# SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

import json
from pathlib import Path
import subprocess
import tempfile
import unittest
from unittest.mock import patch

import web_release as release


class WebReleaseTest(unittest.TestCase):
    def setUp(self):
        temp = tempfile.TemporaryDirectory()
        self.addCleanup(temp.cleanup)
        root = Path(temp.name)
        self.checkout = root / "checkout"
        self.checkout.mkdir()
        subprocess.run(["git", "init", "-q", "-b", "main"], cwd=self.checkout, check=True)
        subprocess.run(["git", "-c", "user.name=Test", "-c", "user.email=test@example.invalid",
                        "-c", "commit.gpgsign=false", "-c", "core.hooksPath=/dev/null",
                        "commit", "--allow-empty", "-qm", "Fixture"], cwd=self.checkout, check=True)
        self.revision = subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=self.checkout,
                                                text=True).strip()
        version = self.checkout / release.VERSION_SOURCE
        version.parent.mkdir(parents=True)
        version.write_text('const val GAME_VERSION: String = "0.6.0"\n', encoding="utf-8")
        self.distribution = root / "distribution"
        self.output = root / "assets"
        for name, data in {
            "index.html": b'<script src="kinetickk.js"></script>',
            "kinetickk.js": b'fetch("app.wasm")',
            "app.wasm": b"\0asm\x01\0\0\0",
            "resources/game.ttf": b"Font fixture",
        }.items():
            self.write(self.distribution / name, data)
        for name, data in {
            "LICENSE": b"PolyForm Noncommercial License 1.0.0",
            "NOTICE": b"Copyright 2026",
            "LICENSES/Third-Party.txt": b"Third-party license fixture",
        }.items():
            self.write(self.checkout / name, data)
            self.write(self.distribution / "META-INF" / name, data)

    @staticmethod
    def write(path, data):
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_bytes(data)

    def package(self):
        return release.package(self.checkout, self.distribution, self.output, "0.6.0")

    def test_packages_the_demo_version_and_preserves_file_bytes(self):
        manifest = self.package()
        self.assertEqual(manifest["tag"], "0.6.0")
        self.assertEqual(manifest["revision"], self.revision)
        self.assertEqual(manifest["files"]["app.wasm"]["contentType"], "application/wasm")
        self.assertEqual(manifest["files"]["resources/game.ttf"]["contentType"], "font/ttf")
        for path, entry in manifest["files"].items():
            data = (self.output / entry["asset"]).read_bytes()
            self.assertEqual(data, (self.distribution / path).read_bytes())
            self.assertEqual(release.digest(data), entry["sha256"])

    def test_previous_version_cannot_label_new_build(self):
        with self.assertRaisesRegex(ValueError, "does not match demo version 0.6.0"):
            release.package(self.checkout, self.distribution, self.output, "0.2.0")
        self.assertFalse(self.output.exists())

    def test_requires_exact_demo_license_and_all_third_party_notices(self):
        shipped_license = self.distribution / "META-INF/LICENSE"
        shipped_license.write_bytes(b"Unrelated license")
        with self.assertRaisesRegex(ValueError, "exact legal document: LICENSE"):
            self.package()
        shipped_license.write_bytes((self.checkout / "LICENSE").read_bytes())
        (self.distribution / "META-INF/LICENSES/Third-Party.txt").unlink()
        with self.assertRaisesRegex(ValueError, "exact legal document: Third-Party.txt"):
            self.package()

    def test_rejects_source_maps_invalid_wasm_and_symlinks(self):
        source_map = self.distribution / "app.wasm.map"
        source_map.write_bytes(b"{}")
        with self.assertRaisesRegex(ValueError, "source maps are forbidden"):
            self.package()
        source_map.unlink()
        wasm = self.distribution / "app.wasm"
        wasm.write_bytes(b"invalid")
        with self.assertRaisesRegex(ValueError, "Invalid Wasm"):
            self.package()
        wasm.unlink()
        wasm.symlink_to(self.checkout / "LICENSE")
        with self.assertRaisesRegex(ValueError, "Symbolic link"):
            self.package()

    def test_resume_verifies_remote_assets_and_attaches_manifest_last(self):
        document = self.package()
        first = next(iter(document["files"].values()))["asset"]
        assets = {first: self.asset_metadata(first)}
        calls = []

        def gh(*args):
            if args == ("api", "repos/4wl2d/KINETICKK-demo/git/ref/tags/0.6.0"):
                return json.dumps({"object": {"type": "tag", "sha": "a" * 40}})
            if args == ("api", f"repos/4wl2d/KINETICKK-demo/git/tags/{'a' * 40}"):
                return json.dumps({"object": {"type": "commit", "sha": self.revision}})
            if args[:2] == ("release", "view"):
                return json.dumps({"databaseId": 7, "isPrerelease": False,
                                   "isImmutable": False, "tagName": "0.6.0"})
            if args[:2] == ("release", "upload"):
                self.assertEqual(args[-2:], ("--repo", "4wl2d/KINETICKK-demo"))
                names = [Path(path).name for path in args[3:-2]]
                calls.append(names)
                assets.update({name: self.asset_metadata(name) for name in names})
                return b""
            raise AssertionError(f"Unexpected command: {args}")

        with patch.object(release, "gh", side_effect=gh), \
                patch.object(release, "assets_for_release", side_effect=lambda _: assets.copy()):
            release.publish(self.checkout, self.output)
            release.publish(self.checkout, self.output)
            del assets[first]
            with self.assertRaisesRegex(ValueError, "missing previously published browser assets"):
                release.publish(self.checkout, self.output)

        self.assertEqual(len(calls), 2)
        self.assertEqual(calls[-1], [release.MANIFEST])
        self.assertNotIn(first, calls[0])

    def test_moved_tag_stops_before_uploading_any_bytes(self):
        self.package()
        moved = {"object": {"type": "commit", "sha": "e" * 40}}
        with patch.object(release, "gh", return_value=json.dumps(moved)) as gh:
            with self.assertRaisesRegex(ValueError, "tag moved"):
                release.publish(self.checkout, self.output)
            gh.assert_called_once_with("api", "repos/4wl2d/KINETICKK-demo/git/ref/tags/0.6.0")

    def test_different_existing_payload_prevents_manifest_upload(self):
        document = self.package()
        first = next(iter(document["files"].values()))["asset"]
        bad_asset = {**self.asset_metadata(first), "digest": "wrong"}
        remote = [json.dumps({"object": {"type": "commit", "sha": self.revision}}),
                  json.dumps({"databaseId": 7, "isPrerelease": False,
                              "isImmutable": False, "tagName": "0.6.0"})]
        with patch.object(release, "gh", side_effect=remote) as gh, \
                patch.object(release, "assets_for_release", return_value={first: bad_asset}):
            with self.assertRaisesRegex(ValueError, "different bytes"):
                release.publish(self.checkout, self.output)
            self.assertEqual(gh.call_count, 2)

    def asset_metadata(self, name):
        data = (self.output / name).read_bytes()
        return {"name": name, "state": "uploaded", "size": len(data), "digest": f"sha256:{release.digest(data)}"}


if __name__ == "__main__":
    unittest.main()
