# 第三方组件与许可证说明

本仓库源码采用 MIT License。发布包中包含的运行时、构建工具和系统框架仍受各自许可证约束。

## Android

- Android SDK / AndroidX / Material Components：Apache License 2.0
- 本项目未引入 Google Play Services 或广告 SDK

## 桌面端

发布包可能包含以下组件：

- **Python 3.8 / 3.12**：Python Software Foundation License
- **Tcl/Tk 8.6**：BSD-style license
- **PyInstaller**：GPLv2 with a special exception for bootloaders（仅用于构建 Windows EXE）
- **python-build-standalone**：用于构建 Linux/macOS 自带运行时；其构建脚本与产物遵循上游许可证
- **OpenSSL**：Apache License 2.0
- **zlib / libffi / XZ / SQLite** 等 Python 依赖：各自的开源许可证

发布包内的 `THIRD_PARTY.txt` 还包含运行时来源说明。

## iOS

- SwiftUI、Foundation、Network.framework 等来自 Apple SDK，受 Apple Developer Program 与 Xcode 许可约束
- 本项目未使用第三方 Swift Package
- iOS IPA 未签名；Apple 签名、开发者账号与 TestFlight/App Store 规则由 Apple 规定

## Minecraft

Minecraft、Mojang 与 Microsoft 是其各自权利人的商标。本项目是非官方工具，不包含 Minecraft 客户端/服务端代码或 Mojang 美术资源，与 Mojang / Microsoft 无关联。
