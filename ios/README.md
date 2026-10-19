# 方块探针 · CubeProbe iOS

与 Windows / Linux / macOS / Android 版同一套设计语言的 iOS 客户端。SwiftUI + Network.framework，直接连接 Minecraft Java 版与基岩版服务器。

> **没有 Mac？** 不需要本地 Mac：本工程已通过 GitHub Actions 的云端 macOS runner 实际编译成功。
>
> 直接下载未签名 IPA：[CubeProbe-unsigned.ipa](https://github.com/sunaiyun/CubeProbe/releases/download/v1.0.0/CubeProbe-unsigned.ipa)
>
> 在 Windows 用 Sideloadly + Apple ID 签名安装；免费 Apple ID 7 天有效。详见 [NO-MAC.md](NO-MAC.md)。

## 直接下载

- 仓库：https://github.com/sunaiyun/CubeProbe
- Release：https://github.com/sunaiyun/CubeProbe/releases/tag/v1.0.0
- 构建记录：https://github.com/sunaiyun/CubeProbe/actions

IPA 未签名、不含 `embedded.mobileprovision`，不能直接安装；必须用 Sideloadly / AltStore 重签。

## 系统要求

- macOS 13 或更新版本
- Xcode 15 或更新版本（建议 16）
- iOS 16.0+
- 真机运行需要 Apple Developer Team（免费 Apple ID 也可做 7 天签名）

## 打开与运行

1. 双击 `CubeProbe.xcodeproj`。
2. 在 Xcode 左侧选择 `CubeProbe` target → `Signing & Capabilities`：
   - 勾选 `Automatically manage signing`
   - 选择你的 Team
   - 如果 Bundle Identifier 冲突，改成 `com.yourname.cubeprobe`
3. 选择 iPhone 模拟器或真机，按 ⌘R。

命令行构建模拟器版本（不签名）：

```bash
xcodebuild -project CubeProbe.xcodeproj \
  -scheme CubeProbe \
  -sdk iphonesimulator \
  -configuration Debug \
  CODE_SIGNING_ALLOWED=NO \
  build
```

## 功能

- Java 版：Server List Ping（1.7+）与 1.6 legacy 回退。
- 基岩版：RakNet Unconnected Ping。
- 彩色 MOTD（JSON 聊天组件、§ 颜色代码）、favicon、玩家样本。
- Java 版默认端口的 DoH SRV 解析（Cloudflare / AliDNS / Google）。
- 本机历史记录（UserDefaults，最近 8 条）、重新查询、复制、分享。
- 深色中文界面；iPhone 竖屏。

## 目录

```text
CubeProbe.xcodeproj/      可直接打开的 Xcode 工程与 shared scheme
CubeProbe/Sources/
  CubeProbeApp.swift      App 入口
  ContentView.swift       SwiftUI 界面
  Models.swift            状态模型、地址解析、颜色
  MOTDRenderer.swift      MOTD JSON / § 解析
  MinecraftClient.swift   Java SLP / Bedrock RakNet / SRV
  ServerViewModel.swift   查询状态、历史、剪贴板
CubeProbe/Resources/      Assets.xcassets 与 1024 应用图标
```

## 重要说明

- **已通过 GitHub Actions 的 macOS runner 编译成功，并生成未签名 IPA。** 真机安装需要用 Sideloadly / AltStore 重新签名；签名后未在真机实际测试。
- iOS 的原始 TCP / UDP 连接不需要额外权限；访问局域网服务器时 iOS 可能会弹出“本地网络”权限提示，文案已配置。
- 连接公网服务器时不需要本地网络权限。查询直接由 App 发起，不经过第三方 API。
- 基岩版状态包不返回玩家列表，所以基岩版不显示玩家样本。
- 本工具是非官方应用，与 Mojang / Microsoft 无关联。

## 设计说明

深色海军蓝背景、薄荷绿（Java）/ 青色（基岩版）强调色，与桌面版和 Android 版保持一致。结果卡包含身份、MOTD、三项指标、容量条、详情与玩家样本；历史记录与操作按钮在下方。
