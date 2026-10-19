"""Build CubeProbe desktop packages for Windows, Linux and macOS.

Run with the bundled Python:
    python build_packages.py
"""

from __future__ import annotations

import hashlib
import os
import shutil
import stat
import tarfile
import zipfile
import zipapp
from pathlib import Path

ROOT = Path(__file__).resolve().parent
PACKAGING = ROOT / "packaging"
DIST = ROOT / "dist"
BUILD = ROOT / "build" / "package-staging"
VERSION = "1.0.0"
APP_NAME = "CubeProbe"


def clean(path: Path):
    if path.exists():
        shutil.rmtree(path)
    path.mkdir(parents=True, exist_ok=True)


def build_pyz() -> Path:
    staging = BUILD / "zipapp"
    clean(staging)
    for name in ("core.py", "gui.py", "cli.py"):
        shutil.copy2(ROOT / name, staging / name)
    shutil.copy2(PACKAGING / "__main__.py", staging / "__main__.py")
    target = DIST / "CubeProbe.pyz"
    if target.exists():
        target.unlink()
    zipapp.create_archive(staging, target=target, interpreter="/usr/bin/env python3",
                          compressed=True)
    os.chmod(target, 0o755)
    return target


def copy_file(source: Path, target: Path, mode: int = 0o644):
    target.parent.mkdir(parents=True, exist_ok=True)
    shutil.copy2(source, target)
    os.chmod(target, mode)


def zip_with_modes(target: Path, root: Path):
    if target.exists():
        target.unlink()
    with zipfile.ZipFile(target, "w", zipfile.ZIP_DEFLATED, compresslevel=9) as archive:
        for path in sorted(root.rglob("*")):
            if path.is_dir():
                continue
            relative = path.relative_to(root).as_posix()
            info = zipfile.ZipInfo(relative)
            info.create_system = 3
            executable = relative.endswith((".command", ".sh", ".pyz")) \
                or "/MacOS/" in relative
            mode = 0o100755 if executable else 0o100644
            info.external_attr = mode << 16
            info.compress_type = zipfile.ZIP_DEFLATED
            with path.open("rb") as handle:
                archive.writestr(info, handle.read())


def write_macos_app(app: Path, pyz: Path, icns: Path):
    macos = app / "Contents" / "MacOS"
    resources = app / "Contents" / "Resources"
    macos.mkdir(parents=True, exist_ok=True)
    resources.mkdir(parents=True, exist_ok=True)
    (app / "Contents" / "Info.plist").write_text(
        """<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
    <key>CFBundleDevelopmentRegion</key><string>zh_CN</string>
    <key>CFBundleDisplayName</key><string>方块探针</string>
    <key>CFBundleExecutable</key><string>CubeProbe</string>
    <key>CFBundleIconFile</key><string>icon.icns</string>
    <key>CFBundleIdentifier</key><string>com.cubeprobe.desktop</string>
    <key>CFBundleInfoDictionaryVersion</key><string>6.0</string>
    <key>CFBundleName</key><string>CubeProbe</string>
    <key>CFBundlePackageType</key><string>APPL</string>
    <key>CFBundleShortVersionString</key><string>1.0.0</string>
    <key>CFBundleVersion</key><string>1</string>
    <key>LSMinimumSystemVersion</key><string>11.0</string>
    <key>NSHighResolutionCapable</key><true/>
    <key>NSLocalNetworkUsageDescription</key><string>用于连接局域网内的 Minecraft 服务器。</string>
</dict>
</plist>
""",
        encoding="utf-8",
    )
    launcher = """#!/bin/bash
set -e
DIR="$(cd "$(dirname "$0")/../Resources" && pwd)"
PYTHON="$(command -v python3 || true)"
if [ -z "$PYTHON" ] && [ -x /usr/local/bin/python3 ]; then PYTHON=/usr/local/bin/python3; fi
if [ -z "$PYTHON" ] && [ -x /opt/homebrew/bin/python3 ]; then PYTHON=/opt/homebrew/bin/python3; fi
if [ -z "$PYTHON" ]; then
  osascript -e 'display dialog "CubeProbe 需要 Python 3.10+（含 Tkinter）。请从 python.org 安装官方 Python，或运行 brew install python python-tk。" buttons {"好"} with title "方块探针"'
  exit 1
fi
exec "$PYTHON" "$DIR/CubeProbe.pyz"
"""
    executable = macos / "CubeProbe"
    executable.write_text(launcher, encoding="utf-8")
    os.chmod(executable, 0o755)
    copy_file(pyz, resources / "CubeProbe.pyz", 0o755)
    copy_file(icns, resources / "icon.icns", 0o644)


def create_windows(win_x86: Path, win_x64: Path, pyz: Path):
    root = BUILD / "windows" / f"{APP_NAME}-{VERSION}-Windows-x86-x64"
    clean(root)
    copy_file(win_x86, root / "CubeProbe-x86.exe", 0o644)
    copy_file(win_x64, root / "CubeProbe-x64.exe", 0o644)
    copy_file(pyz, root / "CubeProbe.pyz", 0o644)
    copy_file(PACKAGING / "CubeProbe-Python.bat", root / "CubeProbe-Python.bat", 0o644)
    copy_file(PACKAGING / "README-Windows.txt", root / "README.txt", 0o644)
    target = DIST / f"{APP_NAME}-{VERSION}-Windows-x86-x64.zip"
    zip_with_modes(target, root)
    return target


def create_linux(pyz: Path):
    root = BUILD / "linux" / f"{APP_NAME}-{VERSION}-Linux"
    clean(root)
    copy_file(pyz, root / "CubeProbe.pyz", 0o755)
    copy_file(PACKAGING / "CubeProbe-linux.sh", root / "CubeProbe", 0o755)
    copy_file(PACKAGING / "CubeProbe.desktop", root / "CubeProbe.desktop", 0o755)
    copy_file(PACKAGING / "README-Linux.txt", root / "README.txt", 0o644)
    target = DIST / f"{APP_NAME}-{VERSION}-Linux-x86_64.tar.gz"
    if target.exists():
        target.unlink()
    with tarfile.open(target, "w:gz") as archive:
        archive.add(root, arcname=root.name, filter=lambda info: _tar_modes(info))
    return target


def _tar_modes(info: tarfile.TarInfo) -> tarfile.TarInfo:
    if info.name.endswith(("CubeProbe", "CubeProbe.pyz", "CubeProbe.desktop")):
        info.mode = 0o755
    elif info.isdir():
        info.mode = 0o755
    else:
        info.mode = 0o644
    return info


def create_macos(pyz: Path, icns: Path):
    root = BUILD / "macos" / f"{APP_NAME}-{VERSION}-macOS"
    clean(root)
    copy_file(pyz, root / "CubeProbe.pyz", 0o755)
    copy_file(PACKAGING / "CubeProbe-macos.command", root / "CubeProbe.command", 0o755)
    copy_file(PACKAGING / "README-macOS.txt", root / "README.txt", 0o644)
    write_macos_app(root / "CubeProbe.app", pyz, icns)
    target = DIST / f"{APP_NAME}-{VERSION}-macOS.zip"
    zip_with_modes(target, root)
    return target


def create_source():
    target = DIST / f"{APP_NAME}-Desktop-{VERSION}-source.zip"
    if target.exists():
        target.unlink()
    include = [ROOT / "README.md", ROOT / ".gitignore", ROOT / "core.py",
               ROOT / "gui.py", ROOT / "cli.py", ROOT / "build_packages.py"]
    include += list(PACKAGING.rglob("*"))
    include += list((ROOT / "docs").rglob("*.png"))
    include += [ROOT / "assets" / "icon.png", ROOT / "assets" / "icon.ico",
                ROOT / "assets" / "icon.icns"]
    with zipfile.ZipFile(target, "w", zipfile.ZIP_DEFLATED, compresslevel=9) as archive:
        for path in include:
            if path.is_file() and "packaging/build" not in path.as_posix():
                archive.write(path, (Path(f"{APP_NAME}-Desktop-{VERSION}") /
                                     path.relative_to(ROOT)).as_posix())
    return target


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1 << 20), b""):
            digest.update(chunk)
    return digest.hexdigest()


def main():
    DIST.mkdir(parents=True, exist_ok=True)
    clean(BUILD)
    pyz = build_pyz()
    win_x86 = ROOT / "windows-build" / "x86" / "dist" / "CubeProbe-x86.exe"
    win_x64 = ROOT / "windows-build" / "x64" / "dist" / "CubeProbe-x64.exe"
    for executable in (win_x86, win_x64):
        if not executable.exists():
            raise SystemExit(
                f"Missing Windows build: {executable}\n"
                "Build it with the 32-bit/64-bit Python 3.8 runtimes and PyInstaller 5.13.2.")
        versioned = DIST / f"{APP_NAME}-{VERSION}-Windows-{executable.stem.split('-')[-1]}.exe"
        copy_file(executable, versioned, 0o644)
    packages = [
        create_windows(win_x86, win_x64, pyz),
        create_linux(pyz),
        create_macos(pyz, ROOT / "assets" / "icon.icns"),
        create_source(),
    ]
    lines = []
    for package in packages:
        lines.append(f"{sha256(package)}  {package.name}")
    for executable in (win_x86, win_x64):
        versioned = DIST / f"{APP_NAME}-{VERSION}-Windows-{executable.stem.split('-')[-1]}.exe"
        lines.append(f"{sha256(versioned)}  {versioned.name}")
    (DIST / "SHA256SUMS.txt").write_text("\n".join(lines) + "\n", encoding="utf-8")
    for package in packages:
        print(f"{package.name}: {package.stat().st_size / 1024 / 1024:.2f} MB")
    print("Done:", DIST)


if __name__ == "__main__":
    main()
