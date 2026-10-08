#!/usr/bin/env python3
"""Inventory or export FoldPod's curated public source (standard library only).

This is a path/type allowlist, not a guarantee that source text contains no secrets.
Review the inventory and source changes before publishing an archive.
"""

import argparse
import os
from pathlib import Path
import stat
import sys
import zipfile


ROOT_FILES = frozenset({
    ".gitignore", "LICENSE", "README.md", "PRIVACY.md", "RELEASE.md",
    "SPOTIFY_RELEASE.md", "build.gradle.kts", "settings.gradle.kts",
    "gradle.properties", "gradlew", "gradlew.bat", "keystore.properties.example",
    "app/build.gradle.kts", "app/proguard-rules.pro",
    "gradle/wrapper/gradle-wrapper.jar", "gradle/wrapper/gradle-wrapper.properties",
    "gradle/gradle-daemon-jvm.properties", "gradle/libs.versions.toml",
    "scripts/export_source.py", "scripts/test_export_source.py",
})
SOURCE_SUFFIXES = frozenset({".kt", ".java", ".xml", ".aidl"})
SOURCE_SETS = frozenset({"main", "test", "androidTest"})
ASSETS = frozenset({
    "app/src/main/assets/licenses/inter_ofl.txt",
    "app/src/main/res/font/inter_variable.ttf",
})
EXCLUDED_DIRS = frozenset({
    ".git", ".idea", ".gradle", "build", "signing", "dist", "cache", "__pycache__",
})
EXCLUDED_NAMES = frozenset({"keystore.properties", "local.properties"})
EXCLUDED_SUFFIXES = frozenset({
    ".jks", ".keystore", ".p12", ".pfx", ".key", ".pem", ".apk", ".aab", ".log",
})
REQUIRED_FILES = frozenset({"LICENSE", "app/src/main/assets/licenses/inter_ofl.txt"})


def allowed(relative):
    """Return whether a relative POSIX path is part of the release allowlist."""
    path = Path(relative)
    if any(part.lower() in EXCLUDED_DIRS or part.lower().startswith(".env") for part in path.parts):
        return False
    if path.name in EXCLUDED_NAMES or path.suffix.lower() in EXCLUDED_SUFFIXES:
        return False
    if relative in ROOT_FILES or relative in ASSETS:
        return True
    return (
        len(path.parts) >= 4 and path.parts[:2] == ("app", "src")
        and path.parts[2] in SOURCE_SETS
        and path.suffix.lower() in SOURCE_SUFFIXES
        and "assets" not in path.parts
    )


def _check_parents(root, path):
    for parent in (path, *path.parents):
        if parent == root:
            return
        if parent.is_symlink():
            raise ValueError(f"Symlink refused: {parent.relative_to(root)}")


def inventory(root):
    """Collect only regular curated files; do not follow symlinks."""
    root = Path(root).resolve()
    selected = set()
    for relative in ROOT_FILES | ASSETS:
        path = root / relative
        _check_parents(root, path)
        if path.exists():
            if not path.is_file():
                raise ValueError(f"Expected regular file: {relative}")
            if allowed(relative):
                selected.add(relative)
    source = root / "app/src"
    _check_parents(root, source)
    if source.exists():
        for directory, dirs, files in os.walk(source, followlinks=False):
            current = Path(directory)
            for name in dirs + files:
                path = current / name
                if path.is_symlink():
                    raise ValueError(f"Symlink refused: {path.relative_to(root)}")
            dirs[:] = [name for name in dirs if name.lower() not in EXCLUDED_DIRS
                       and not name.lower().startswith(".env")]
            for name in files:
                path = current / name
                relative = path.relative_to(root).as_posix()
                if allowed(relative):
                    if not path.is_file():
                        raise ValueError(f"Expected regular file: {relative}")
                    selected.add(relative)
    missing = REQUIRED_FILES - selected
    if missing:
        raise ValueError("Required license missing: " + ", ".join(sorted(missing)))
    return [(relative, (root / relative).stat().st_size) for relative in sorted(selected)]


def export(root, output, entries):
    """Create an explicitly requested new ZIP; existing paths are never replaced."""
    root = Path(root).resolve()
    output = Path(output).absolute()
    if output.suffix.lower() != ".zip":
        raise ValueError("Output must have a .zip extension")
    resolved = output.resolve()
    for tree in (root / "app/src", root / "gradle", root / "scripts"):
        if resolved == tree or tree in resolved.parents:
            raise ValueError("Output must be outside curated source trees")
    if output.exists() or output.is_symlink():
        raise ValueError(f"Output already exists: {output}")
    # Open each input without following a final symlink and recheck its parents.
    # Read before creating the output, so input errors cannot leave a partial ZIP.
    contents = []
    for relative, expected_size in entries:
        if not allowed(relative):
            raise ValueError(f"Unapproved input: {relative}")
        path = root / relative
        _check_parents(root, path)
        descriptor = os.open(path, os.O_RDONLY | os.O_NOFOLLOW | os.O_NONBLOCK)
        with os.fdopen(descriptor, "rb") as handle:
            metadata = os.fstat(handle.fileno())
            if not stat.S_ISREG(metadata.st_mode):
                raise ValueError(f"Expected regular file: {relative}")
            data = handle.read()
        if len(data) != expected_size:
            raise ValueError(f"Input changed since inventory: {relative}; rerun")
        contents.append((relative, data))
    with output.open("xb") as handle:
        with zipfile.ZipFile(handle, "w", compression=zipfile.ZIP_DEFLATED) as archive:
            for relative, data in contents:
                info = zipfile.ZipInfo(relative, date_time=(1980, 1, 1, 0, 0, 0))
                info.create_system = 3
                info.external_attr = (0o100755 if relative == "gradlew" else 0o100644) << 16
                info.compress_type = zipfile.ZIP_DEFLATED
                archive.writestr(info, data)
    return output


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    mode = parser.add_mutually_exclusive_group()
    mode.add_argument("--check", action="store_true", help="inventory only (default)")
    mode.add_argument("--output", type=Path, help="create a new ZIP at this explicit path")
    args = parser.parse_args(argv)
    root = Path(__file__).resolve().parents[1]
    try:
        entries = inventory(root)
        for relative, size in entries:
            print(f"{size:>10}  {relative}")
        print(f"Curated source: {len(entries)} files, {sum(size for _, size in entries)} bytes")
        print("Path allowlist only; review source content for secrets before publication.")
        if args.output is not None:
            print(f"Created: {export(root, args.output, entries)}")
    except (OSError, ValueError) as error:
        print(f"Export refused: {error}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
