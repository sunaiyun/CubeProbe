# 方块探针 · CubeProbe 桌面版

同一套 Python 桌面客户端，覆盖 **Windows / Linux / macOS**，与 Android 版共用协议与深色界面。直接连接 Minecraft Java 版与基岩版服务器，不经过第三方查询 API。

![桌面版 Java 结果](docs/desktop-java.png)

## 功能

- **Java 版**：Server List Ping（1.7+）+ 1.6 legacy 回退；JSON/MOTD 颜色、favicon、玩家样本。
- **基岩版**：RakNet Unconnected Ping；双行 MOTD、人数、版本、游戏模式。
- **SRV**：Java 版未显式填端口时，通过 Cloudflare / AliDNS / Google DoH 解析 `_minecraft._tcp`。
- 历史记录（本机 JSON）、双击重查、Delete 移除、复制状态。
- 深色中文界面，纯 Tkinter；Windows 版和重新打包的 Linux/macOS 版均自带运行时。
- 自带 CLI。

## 平台包

| 平台 | 文件 | 说明 |
| --- | --- | --- |
| Windows x86 / x64 | `CubeProbe-1.0.0-Windows-x86-x64.zip` | 两个独立 EXE，无需 Python |
| Linux x86_64 | `CubeProbe-1.0.0-Linux-x86_64.tar.gz` | 自带 Python 3.12 + Tcl/Tk，无需系统 Python |
| Linux 单文件 | `CubeProbe-1.0.0-Linux-x86_64.run` | 自解压单文件，首次运行解压到 `~/.cache` |
| macOS Universal | `CubeProbe-1.0.0-macOS.zip` | 自带 arm64 + x86_64 Python 运行时，首次启动解压 |
| iOS | `../cube-ios/` Xcode 工程 | 需要 macOS + Xcode 编译签名；没有 IPA |

Windows 两个 EXE 位于合集压缩包，也可单独下载：

- `CubeProbe-x86.exe`：32 位，可在 32/64 位 Windows 上运行
- `CubeProbe-x64.exe`：64 位，推荐用于 64 位 Windows

> Windows EXE 由 Python 3.8.10 + PyInstaller 5.13.2 构建，目标为 Windows 8 / 8.1 / 10 / 11（x86/x64）。已在 Windows 10 x64 上分别启动两个 EXE 验证；Windows 7 SP1 未实机验证。
>
> Linux / macOS 包在 Windows 上组装。已检查 ELF/Mach-O 架构、Tk 文件、启动脚本与压缩包权限，但**未在真实 Linux 或 Mac 上运行**。macOS `.app` 未签名、未公证，首次打开需要右键 → 打开。

## 使用

### Linux（自带运行时）

```bash
tar -xzf CubeProbe-1.0.0-Linux-x86_64.tar.gz
cd CubeProbe-1.0.0-Linux-x86_64
chmod +x CubeProbe
./CubeProbe
```

或单文件：

```bash
chmod +x CubeProbe-1.0.0-Linux-x86_64.run
./CubeProbe-1.0.0-Linux-x86_64.run
```

自带运行时的 `.run` 首次启动会把文件解压到 `~/.cache/CubeProbe/1.0.0`。需要 X11 桌面会话（Wayland 下通过 XWayland）。

### macOS（自带运行时）

解压后双击 `CubeProbe.app`。首次启动会把对应架构的 Python 运行时解压到：

```text
~/Library/Application Support/CubeProbe/runtime-<arch>/
```

如果被 Gatekeeper 拦截：右键 `CubeProbe.app` → 打开。终端方式：

```bash
chmod +x CubeProbe.command
./CubeProbe.command
```

支持 Apple Silicon 与 Intel；`CubeProbe-system-python.command` 是使用系统 Python 的备用入口。

### iOS

用 Xcode 打开 `../ios/CubeProbe.xcodeproj`，选择自己的 Team 后运行；或使用仓库根目录的 GitHub Actions 工作流生成未签名 IPA。详见 `../ios/NO-MAC.md`。

## 构建

### Windows EXE（x86 / x64）

必须分别用对应架构的 Python 构建。本项目使用 Python 3.8.10 + PyInstaller 5.13.2：

```powershell
# 32 位（从 desktop 目录运行；specpath 会让相对资源路径失效，因此资源/版本文件用绝对路径）
py-x86\python.exe -m PyInstaller --noconfirm --clean --onefile --windowed `
  --name CubeProbe-x86 --icon "$PWD\assets\icon.ico" `
  --version-file "$PWD\packaging\version_info.txt" `
  --add-data "$PWD\assets;assets" --distpath windows-build/x86/dist `
  --workpath windows-build/x86/work --specpath windows-build/x86/spec gui.py

# 64 位
py-x64\python.exe -m PyInstaller --noconfirm --clean --onefile --windowed `
  --name CubeProbe-x64 --icon "$PWD\assets\icon.ico" `
  --version-file "$PWD\packaging\version_info.txt" `
  --add-data "$PWD\assets;assets" --distpath windows-build/x64/dist `
  --workpath windows-build/x64/work --specpath windows-build/x64/spec gui.py
```

### 自带运行时的 Linux / macOS 包

从 [python-build-standalone](https://github.com/astral-sh/python-build-standalone/releases/tag/20250808) 下载以下运行时到 `../tools/pbs`：

- `linux-x86_64-stripped.tar.gz`
- `macos-arm64.tar.gz`
- `macos-x86_64.tar.gz`

然后：

```powershell
python build_bundled.py
```

生成：

- `dist/CubeProbe-1.0.0-Linux-x86_64.tar.gz`
- `dist/CubeProbe-1.0.0-Linux-x86_64.run`
- `dist/CubeProbe-1.0.0-macOS.zip`

脚本会直接流式复制运行时 tar，保留 Linux 符号链接与 Unix 权限；macOS `.app` 会在首次启动时按 CPU 架构解压对应运行时。

### 轻量 Python 版 / Windows 合集

```powershell
python build_packages.py
```

生成 Windows 合集、依赖系统 Python 的轻量 Linux/macOS 包、桌面源码和校验文件。

## 目录

```text
core.py              协议与 MOTD 解析
gui.py               Tkinter 深色 GUI
cli.py               命令行查询
assets/              图标
packaging/           轻量包启动脚本
build_packages.py    Windows / 轻量包构建
build_bundled.py     自带运行时 Linux / macOS 构建
windows-build/       Windows x86/x64 PyInstaller 产物
docs/                GUI 截图
```

## 已知限制

- Windows EXE、Linux/macOS 自带运行时包都没有商业代码签名；SmartScreen / Gatekeeper 会提示未知开发者。
- Linux / macOS 包在 Windows 上组装，**未在真实 Linux / Mac 上运行验证**；如启动失败可按 README 在终端运行查看错误。
- 自带运行时的 macOS `.app` 首次启动需要解压约 15 MB 运行时；未签名 app 可能被 Gatekeeper 拦截，需右键打开。
- iOS 只提供 Xcode 工程源码，不提供 IPA；iOS 编译签名必须在 macOS 上完成。
- 基岩版状态包不返回玩家样本。
- 本工具是非官方项目，与 Mojang / Microsoft 无关联。

截图中的服务器来自本地测试服务（`127.0.0.1` / `10.0.2.2`），不是公网服务器。
