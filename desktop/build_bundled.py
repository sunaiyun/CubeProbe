"""Build self-contained Linux and macOS packages with bundled Python + Tk.

Runtime sources (not shipped in the source zip):
  <workspace>/tools/pbs/linux-x86_64-stripped.tar.gz
  <workspace>/tools/pbs/macos-arm64.tar.gz
  <workspace>/tools/pbs/macos-x86_64.tar.gz

The Linux archive streams the runtime tar directly so symlinks and Unix modes
survive. The macOS .app extracts the architecture-specific runtime on first
launch, preserving symlinks via /usr/bin/tar.
"""

from __future__ import annotations

import io
import os
import shutil
import tarfile
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parent
WORKSPACE = ROOT.parent
PBS = WORKSPACE / "tools" / "pbs"
DIST = ROOT / "dist"
STAGE = ROOT / "build" / "bundled"
VERSION = "1.0.0"
APP_NAME = "CubeProbe"

LINUX_RUNTIME = PBS / "linux-x86_64-stripped.tar.gz"
MAC_ARM_RUNTIME = PBS / "macos-arm64.tar.gz"
MAC_X86_RUNTIME = PBS / "macos-x86_64.tar.gz"

LINUX_PREFIX = f"{APP_NAME}-{VERSION}-Linux-x86_64"
MAC_PREFIX = f"{APP_NAME}-{VERSION}-macOS"

THIRD_PARTY = """第三方运行时
============
Python 3.12.11 (PSF License)
Tcl/Tk 8.6 (BSD-style license)
python-build-standalone 20250808 (astral-sh/python-build-standalone)

Python: https://www.python.org/
Tcl/Tk: https://www.tcl.tk/
Build: https://github.com/astral-sh/python-build-standalone
"""

LINUX_README = """方块探针 · CubeProbe 1.0.0（Linux x86_64 自带运行时）
============================================================

这是一个自带 Python 3.12 + Tcl/Tk 的绿色包，不需要系统安装 Python 或 Tk。

启动
----
tar -xzf CubeProbe-1.0.0-Linux-x86_64.tar.gz
cd CubeProbe-1.0.0-Linux-x86_64
./CubeProbe

如果下载后没有可执行权限：
  chmod +x CubeProbe

命令行
------
  ./CubeProbe --cli play.example.com
  ./CubeProbe --cli play.example.com --json

单文件版本
----------
同目录的 CubeProbe-1.0.0-Linux-x86_64.run 是一个自解压单文件版本：
  chmod +x CubeProbe-1.0.0-Linux-x86_64.run
  ./CubeProbe-1.0.0-Linux-x86_64.run
首次运行会解压到 ~/.cache/CubeProbe/1.0.0，之后直接启动。

支持范围
--------
- Linux x86_64（glibc 桌面发行版）
- 需要可用的 X11 桌面会话（Tk 使用 X11/XWayland）
- 未在真实 Linux 机器上实机验证；本包由 Windows 组装，已检查 ELF/Tk 文件结构。
- 如果图形界面无法启动，可在终端运行 ./CubeProbe 查看错误信息。

本工具是非官方应用，与 Mojang / Microsoft 无关联。
"""

LINUX_LAUNCHER = """#!/bin/sh
set -e
HERE=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
RUNTIME="$HERE/runtime"
export TCL_LIBRARY="$RUNTIME/lib/tcl8.6"
export TK_LIBRARY="$RUNTIME/lib/tk8.6"
export LD_LIBRARY_PATH="$RUNTIME/lib${LD_LIBRARY_PATH:+:$LD_LIBRARY_PATH}"
if [ "$1" = "--cli" ]; then
  shift
  exec "$RUNTIME/bin/python3" "$HERE/app/cli.py" "$@"
fi
exec "$RUNTIME/bin/python3" "$HERE/app/gui.py" "$@"
"""

LINUX_DESKTOP = """[Desktop Entry]
Type=Application
Name=方块探针
Comment=Minecraft Java / Bedrock 服务器查询
Encoding=UTF-8
Terminal=false
Categories=Network;Game;Utility;
Exec=sh -c 'cd "$(dirname "$1")" && exec ./CubeProbe' dummy %k
Icon=applications-games
"""

RUN_HEADER = """#!/bin/sh
set -e
DEST="${XDG_CACHE_HOME:-$HOME/.cache}/CubeProbe/1.0.0"
LAUNCHER="$DEST/CubeProbe-1.0.0-Linux-x86_64/CubeProbe"
if [ ! -x "$LAUNCHER" ]; then
  mkdir -p "$DEST"
  SKIP=$(awk '/^__CUBEPROBE_PAYLOAD__$/ { print NR + 1; exit }' "$0")
  tail -n +"$SKIP" "$0" | tar -xzf - -C "$DEST"
fi
exec "$LAUNCHER" "$@"
__CUBEPROBE_PAYLOAD__
"""

MAC_LAUNCHER = """#!/bin/bash
set -e
CONTENTS="$(cd "$(dirname "$0")/.." && pwd)"
RES="$CONTENTS/Resources"
ARCH="$(/usr/bin/uname -m)"
case "$ARCH" in
  arm64)
    RUNTIME="$RES/runtime-arm64.tar.gz"
    CACHE="$HOME/Library/Application Support/CubeProbe/runtime-arm64"
    ;;
  x86_64)
    RUNTIME="$RES/runtime-x86_64.tar.gz"
    CACHE="$HOME/Library/Application Support/CubeProbe/runtime-x86_64"
    ;;
  *)
    /usr/bin/osascript -e 'display dialog "CubeProbe 暂不支持此 Mac 架构。" buttons {"好"} with title "方块探针"'
    exit 1
    ;;
esac
PY="$CACHE/python/bin/python3"
if [ ! -x "$PY" ]; then
  /bin/mkdir -p "$CACHE"
  /usr/bin/tar -xzf "$RUNTIME" -C "$CACHE"
  /usr/bin/xattr -dr com.apple.quarantine "$CACHE" 2>/dev/null || true
  /usr/bin/codesign --force --deep --sign - "$CACHE/python" >/dev/null 2>&1 || true
fi
if [ ! -x "$PY" ]; then
  /usr/bin/osascript -e 'display dialog "Python 运行时解压失败，请从终端运行 CubeProbe.command 查看错误。" buttons {"好"} with title "方块探针"'
  exit 1
fi
export TCL_LIBRARY="$CACHE/python/lib/tcl8.6"
export TK_LIBRARY="$CACHE/python/lib/tk8.6"
if [ "$1" = "--cli" ]; then
  shift
  exec "$PY" "$RES/app/cli.py" "$@"
fi
exec "$PY" "$RES/app/gui.py" "$@"
"""

MAC_COMMAND = """#!/bin/bash
set -e
HERE="$(cd "$(dirname "$0")" && pwd)"
exec "$HERE/CubeProbe.app/Contents/MacOS/CubeProbe" "$@"
"""

MAC_SYSTEM_PYTHON = """#!/bin/bash
set -e
HERE="$(cd "$(dirname "$0")" && pwd)"
if ! command -v python3 >/dev/null 2>&1; then
  echo "未找到 python3。请安装带 Tk 的 Python 3，或使用 CubeProbe.app。"
  read -n 1 -s -r -p "按任意键退出..."
  exit 1
fi
exec python3 "$HERE/CubeProbe.app/Contents/Resources/app/gui.py" "$@"
"""

MAC_README = """方块探针 · CubeProbe 1.0.0（macOS 自带运行时）
====================================================

这是一个自带 Python 3.12 + Tcl/Tk 的 macOS 应用，支持 Apple Silicon 与 Intel：
不需要安装 Python。

启动
----
解压后双击 CubeProbe.app。
首次启动会把对应架构的 Python 运行时解压到：
  ~/Library/Application Support/CubeProbe/runtime-<arch>/
之后启动会直接使用缓存。

未签名说明
----------
本应用没有 Apple Developer 签名与公证。首次打开如果被 Gatekeeper 拦截：
  右键 CubeProbe.app → 打开
或到“系统设置 → 隐私与安全性”选择“仍要打开”。

终端启动/查看错误
------------------
  chmod +x CubeProbe.command
  ./CubeProbe.command

如果自带运行时在你机器上无法使用，还有一个使用系统 Python 的备用入口：
  ./CubeProbe-system-python.command
（需要安装带 Tk 的 Python 3。）

支持范围
--------
- macOS 11 或更新版本
- Apple Silicon（arm64）与 Intel（x86_64）
- 未在真实 Mac 上实机验证；运行时为预构建的 CPython + Tk，本包在 Windows 上组装并检查了 Mach-O/Tk 文件结构。

本工具是非官方应用，与 Mojang / Microsoft 无关联。
"""


def _skip_runtime_member(name: str) -> bool:
    if name in ("python", "python/"):
        return False
    skip_prefixes = (
        "python/lib/python3.12/site-packages",
        "python/lib/python3.12/ensurepip",
        "python/lib/python3.12/idlelib",
        "python/lib/python3.12/test",
        "python/lib/python3.12/turtledemo",
        "python/lib/python3.12/config-3.12",
        "python/lib/tk8.6/demos",
        "python/include",
        "python/lib/pkgconfig",
        "python/share/man",
        "python/lib/itcl4.2.4",
        "python/lib/tix8.4.3",
        "python/lib/thread2.8.7",
        "python/lib/tdbc",
    )
    if any(name.startswith(prefix) for prefix in skip_prefixes):
        return True
    if name.endswith((".a", ".lib")):
        return True
    return False


def _add_file(tar: tarfile.TarFile, path: Path, arcname: str, mode: int = 0o644):
    info = tar.gettarinfo(str(path), arcname=arcname)
    info.mode = mode
    info.uid = info.gid = 0
    info.uname = info.gname = ""
    with path.open("rb") as handle:
        tar.addfile(info, handle)


def _add_text(tar: tarfile.TarFile, text: str, arcname: str, mode: int = 0o644):
    data = text.encode("utf-8")
    info = tarfile.TarInfo(arcname)
    info.size = len(data)
    info.mode = mode
    info.uid = info.gid = 0
    info.uname = info.gname = ""
    tar.addfile(info, io.BytesIO(data))


def _add_app(tar: tarfile.TarFile, prefix: str):
    for name in ("core.py", "gui.py", "cli.py"):
        _add_file(tar, ROOT / name, f"{prefix}/app/{name}")
    for asset in ("icon.png", "icon.ico", "icon.icns"):
        path = ROOT / "assets" / asset
        if path.exists():
            _add_file(tar, path, f"{prefix}/app/assets/{asset}")


def _add_linux_runtime(tar: tarfile.TarFile, prefix: str):
    with tarfile.open(LINUX_RUNTIME, "r:gz") as runtime:
        for info in runtime.getmembers():
            if _skip_runtime_member(info.name):
                continue
            if not info.name.startswith("python/"):
                continue
            relative = info.name[len("python/"):]
            arcname = f"{prefix}/runtime/{relative}"
            info.name = arcname
            info.uid = info.gid = 0
            info.uname = info.gname = ""
            if info.isfile():
                source = runtime.extractfile(info)
                tar.addfile(info, source)
            else:
                tar.addfile(info)


def build_linux() -> tuple[Path, Path]:
    target = DIST / f"{APP_NAME}-{VERSION}-Linux-x86_64.tar.gz"
    fallback = DIST / f"{APP_NAME}-{VERSION}-Linux-Python-x86_64.tar.gz"
    if target.exists() and not fallback.exists():
        shutil.copy2(target, fallback)
    temp = target.with_suffix(".tar.gz.tmp")
    if temp.exists():
        temp.unlink()
    with tarfile.open(temp, "w:gz", compresslevel=6) as tar:
        _add_text(tar, LINUX_README, f"{LINUX_PREFIX}/README.txt")
        _add_text(tar, THIRD_PARTY, f"{LINUX_PREFIX}/THIRD_PARTY.txt")
        _add_text(tar, LINUX_LAUNCHER, f"{LINUX_PREFIX}/CubeProbe", 0o755)
        _add_text(tar, LINUX_DESKTOP, f"{LINUX_PREFIX}/CubeProbe.desktop", 0o755)
        _add_app(tar, LINUX_PREFIX)
        _add_linux_runtime(tar, LINUX_PREFIX)
    temp.replace(target)

    run_target = DIST / f"{APP_NAME}-{VERSION}-Linux-x86_64.run"
    with run_target.open("wb") as output:
        output.write(RUN_HEADER.encode("utf-8"))
        with target.open("rb") as payload:
            shutil.copyfileobj(payload, output)
    return target, run_target


def build_macos() -> Path:
    target = DIST / f"{APP_NAME}-{VERSION}-macOS.zip"
    fallback = DIST / f"{APP_NAME}-{VERSION}-macOS-Python.zip"
    if target.exists() and not fallback.exists():
        shutil.copy2(target, fallback)

    stage = STAGE / MAC_PREFIX
    if stage.exists():
        shutil.rmtree(stage)
    app = stage / "CubeProbe.app"
    contents = app / "Contents"
    macos = contents / "MacOS"
    resources = contents / "Resources"
    appdir = resources / "app"
    macos.mkdir(parents=True, exist_ok=True)
    (appdir / "assets").mkdir(parents=True, exist_ok=True)
    for name in ("core.py", "gui.py", "cli.py"):
        shutil.copy2(ROOT / name, appdir / name)
    for asset in ("icon.png", "icon.ico"):
        path = ROOT / "assets" / asset
        if path.exists():
            shutil.copy2(path, appdir / "assets" / asset)
    shutil.copy2(MAC_ARM_RUNTIME, resources / "runtime-arm64.tar.gz")
    shutil.copy2(MAC_X86_RUNTIME, resources / "runtime-x86_64.tar.gz")
    shutil.copy2(ROOT / "assets" / "icon.icns", resources / "icon.icns")

    (contents / "Info.plist").write_text(
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
    (contents / "PkgInfo").write_text("APPL????", encoding="ascii")
    launcher = macos / "CubeProbe"
    launcher.write_text(MAC_LAUNCHER, encoding="utf-8")
    os.chmod(launcher, 0o755)
    (stage / "CubeProbe.command").write_text(MAC_COMMAND, encoding="utf-8")
    (stage / "CubeProbe-system-python.command").write_text(MAC_SYSTEM_PYTHON, encoding="utf-8")
    (stage / "README.txt").write_text(MAC_README, encoding="utf-8")
    (stage / "THIRD_PARTY.txt").write_text(THIRD_PARTY, encoding="utf-8")

    # zip paths: executable modes for .command and the app's Mach-O-directory launcher.
    if target.exists():
        target.unlink()
    with zipfile.ZipFile(target, "w", zipfile.ZIP_DEFLATED, compresslevel=9) as archive:
        for path in sorted(stage.rglob("*")):
            if path.is_dir():
                continue
            relative = path.relative_to(stage).as_posix()
            info = zipfile.ZipInfo(relative)
            info.create_system = 3
            executable = relative.endswith(".command") or "/MacOS/" in relative
            info.external_attr = (0o100755 if executable else 0o100644) << 16
            info.compress_type = zipfile.ZIP_DEFLATED
            info.date_time = (2026, 1, 1, 0, 0, 0)
            archive.writestr(info, path.read_bytes())
    return target


def main():
    DIST.mkdir(parents=True, exist_ok=True)
    for runtime in (LINUX_RUNTIME, MAC_ARM_RUNTIME, MAC_X86_RUNTIME):
        if not runtime.exists():
            raise SystemExit(f"Missing runtime: {runtime}")
    linux, run = build_linux()
    mac = build_macos()
    for path in (linux, run, mac):
        print(f"{path.name}: {path.stat().st_size / 1024 / 1024:.2f} MB")


if __name__ == "__main__":
    main()
