"""Adversarial local fixtures for the public source export boundary."""

from pathlib import Path
import shutil
import subprocess
import sys
import tempfile
import unittest
import zipfile

import export_source


class ExportSourceTest(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.root = Path(self.temporary.name) / "repo"
        self.root.mkdir()
        self.write("LICENSE", "MIT fixture")
        self.write("app/src/main/assets/licenses/inter_ofl.txt", "OFL fixture")

    def write(self, relative, data="fixture"):
        path = self.root / relative
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(data)
        return path

    def test_curated_zip_preserves_source_and_licenses(self):
        expected = {
            "LICENSE", "app/src/main/assets/licenses/inter_ofl.txt",
            "README.md", "keystore.properties.example", "gradlew",
            "gradle/wrapper/gradle-wrapper.jar", "app/build.gradle.kts",
            "app/src/main/AndroidManifest.xml", "app/src/main/java/App.kt",
            "app/src/test/java/AppTest.kt", "app/src/main/res/font/inter_variable.ttf",
            "scripts/export_source.py", "scripts/test_export_source.py",
        }
        for relative in expected:
            if not (self.root / relative).exists():
                self.write(relative)
        entries = export_source.inventory(self.root)
        self.assertEqual(expected, {relative for relative, _ in entries})
        self.assertEqual(sum((self.root / name).stat().st_size for name in expected),
                         sum(size for _, size in entries))
        output = self.root / "dist/source.zip"
        output.parent.mkdir()
        export_source.export(self.root, output, entries)
        with zipfile.ZipFile(output) as archive:
            self.assertEqual(sorted(expected), archive.namelist())
            self.assertEqual(b"MIT fixture", archive.read("LICENSE"))
            self.assertEqual(b"OFL fixture", archive.read("app/src/main/assets/licenses/inter_ofl.txt"))
            self.assertEqual(0o100755, archive.getinfo("gradlew").external_attr >> 16)

    def test_credentials_logs_build_and_unknown_files_are_excluded(self):
        excluded = [
            ".env", ".env.production", "keystore.properties", "local.properties",
            "signing/release.jks", "app/build/debug.apk", ".git/config", ".idea/workspace.xml",
            "app/src/main/java/.env.secret.kt", "app/src/main/java/local.properties",
            "app/src/main/java/.ENV.production.xml", "app/src/main/java/Device.apk",
            "app/src/main/java/Device.aab", "app/src/main/java/token.pfx",
            "app/src/main/java/token.keystore", "app/src/main/java/token.jks",
            "app/src/main/java/key.PEM", "app/src/main/java/token.p12", "app/src/main/java/private.key",
            "app/src/main/java/device.log", "app/src/main/java/device.json",
            "app/src/main/java/build/Generated.kt", "app/src/main/java/cache/Token.kt",
            "app/src/main/java/signing/Keys.kt", "app/src/main/java/dist/Copy.kt",
            "app/src/main/assets/account.txt", "app/src/main/assets/account.xml",
            "app/src/main/res/font/personal.ttf", "app/src/unknown/java/Private.kt",
            "gradle/private.properties", "scripts/private.py", "developer-notes.md",
        ]
        for relative in excluded:
            self.write(relative)
        self.assertEqual(export_source.REQUIRED_FILES,
                         {relative for relative, _ in export_source.inventory(self.root)})

    def test_symlink_file_and_directory_are_refused(self):
        outside = Path(self.temporary.name) / "personal.kt"
        outside.write_text("private")
        link = self.root / "app/src/main/java/Linked.kt"
        link.parent.mkdir(parents=True)
        link.symlink_to(outside)
        with self.assertRaisesRegex(ValueError, "Symlink refused"):
            export_source.inventory(self.root)
        link.unlink()
        link.symlink_to(outside.parent, target_is_directory=True)
        with self.assertRaisesRegex(ValueError, "Symlink refused"):
            export_source.inventory(self.root)

    def test_whitelisted_parent_symlink_is_refused(self):
        outside = Path(self.temporary.name) / "external"
        outside.mkdir()
        (self.root / "gradle").symlink_to(outside, target_is_directory=True)
        with self.assertRaisesRegex(ValueError, "Symlink refused"):
            export_source.inventory(self.root)

    def test_existing_output_is_preserved(self):
        output = self.write("source.zip", "do not overwrite")
        with self.assertRaisesRegex(ValueError, "already exists"):
            export_source.export(self.root, output, export_source.inventory(self.root))
        self.assertEqual("do not overwrite", output.read_text())

    def test_cli_defaults_to_inventory_without_an_archive(self):
        script = self.root / "scripts/export_source.py"
        script.parent.mkdir()
        shutil.copyfile(export_source.__file__, script)
        before = set(self.root.rglob("*"))
        result = subprocess.run([sys.executable, str(script)], cwd=self.root,
                                capture_output=True, text=True, check=False)
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertIn("Curated source: 3 files", result.stdout)
        self.assertNotIn("Created:", result.stdout)
        self.assertEqual(before, set(self.root.rglob("*")))

    def test_output_inside_source_trees_is_refused(self):
        for relative in ("app/src/source.zip", "gradle/source.zip", "scripts/source.zip"):
            with self.subTest(relative=relative):
                with self.assertRaisesRegex(ValueError, "outside curated"):
                    export_source.export(self.root, self.root / relative,
                                         export_source.inventory(self.root))

    def test_missing_license_is_refused(self):
        (self.root / "LICENSE").unlink()
        with self.assertRaisesRegex(ValueError, "Required license missing"):
            export_source.inventory(self.root)

    def test_changed_input_is_refused_before_creating_archive(self):
        entries = export_source.inventory(self.root)
        self.write("LICENSE", "changed size")
        output = self.root / "source.zip"
        with self.assertRaisesRegex(ValueError, "Input changed"):
            export_source.export(self.root, output, entries)
        self.assertFalse(output.exists())


if __name__ == "__main__":
    unittest.main()
