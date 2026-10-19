# 没有 Mac 时如何构建/安装 iOS 版

> **已验证：** 2026-10-19，GitHub Actions 的 macOS runner 成功编译了本工程，产物 bundle id `com.cubeprobe.mc.ios`，arm64，最低 iOS 16.0。
>
> 直接下载未签名 IPA：
> https://github.com/sunaiyun/CubeProbe/releases/download/v1.0.0/CubeProbe-unsigned.ipa
>
> 仓库（含 Actions 记录）：https://github.com/sunaiyun/CubeProbe
>
> 未签名 IPA 不能直接安装，仍需按下面的 Sideloadly 步骤用 Apple ID 重签。

iOS 应用必须在 macOS + Xcode 中编译，且装到真机前必须签名。没有 Mac 时，推荐走“云端 macOS 构建 + Windows 侧签名安装”的组合，不需要本地 Mac。

## 方案 A：GitHub Actions（免费，推荐）

本仓库已包含工作流：`.github/workflows/ios-unsigned-ipa.yml`，iOS 工程位于 `ios/`。

1. 打开仓库的 **Actions** → **Build unsigned iOS IPA** → **Run workflow**。
2. 选择 `main` 分支运行。
3. 等待任务完成，下载 artifact `CubeProbe-unsigned-ipa`，解压得到 `CubeProbe-unsigned.ipa`。

工作流使用 GitHub 提供的 **macOS runner**（相当于一台临时 Mac），不需要你拥有 Mac。它只编译，不签名。Fork 仓库后同样可以直接运行。

## 方案 B：Windows 上安装 IPA（需要 Apple ID）

未签名 IPA 不能直接安装，需要 Windows 上的签名/安装工具：

1. 安装 [Sideloadly](https://sideloadly.io/)（Windows）。
2. 如果 Windows 识别不到 iPhone，先安装 Apple 官网版 iTunes（不要用 Microsoft Store 版），并信任电脑。
3. 连接 iPhone，把 `CubeProbe-unsigned.ipa` 拖入 Sideloadly。
4. 输入 Apple ID，开始安装。若 Apple ID 开启了双重认证，Sideloadly 可能要求输入 Apple 的 App 专用密码（在 appleid.apple.com 生成）。
5. 在 iPhone 上：设置 → 隐私与安全性 → 开发者模式 → 打开；必要时在“VPN 与设备管理”里信任开发者证书。

限制：

- 免费 Apple ID 签名的 App **7 天过期**，到期需重新安装；最多同时保留 3 个自签 App。
- 付费 Apple Developer Program（$99/年）可 1 年有效。
- Apple ID 凭据只在 Sideloadly 本地使用；请使用官方下载的工具。

备选工具：AltServer / AltStore（Windows 版），或使用付费签名服务。

## 方案 C：租一台云端 Mac

如果 GitHub Actions 构建失败需要调试，或想直接用 Xcode：

- MacinCloud、MacStadium、Scaleway Apple Silicon、AWS EC2 Mac 等按小时/按月租用。
- 远程桌面连接后，打开 Xcode，登录 Apple ID，连接/注册设备并运行。
- 适合愿意按小时付费、需要完整 Xcode 调试的场景。

## 没有 Apple ID 怎么办

没有任何 Apple ID（或不愿使用自签）时，**无法把原生 IPA 安装到 iPhone**。可选：

- 借用一个 Apple ID（前提是对方同意并了解 7 天限制）。
- 购买 Apple Developer Program，走 TestFlight / App Store。
- 改用 **PWA/网页版**：无需签名即可在 Safari 添加到主屏幕，但浏览器不能直接做 Minecraft 原始 TCP/UDP 查询，需要额外部署一台中转服务器。如果你接受这种形态，可以继续开发。

## 常见构建问题

- **Xcode 编译错误**：把 Actions 日志中的红色报错发来，我可以继续修 Swift 代码。
- **`No signing certificate` / `requires a development team`**：确认使用了本工程自带的无签名 workflow；不要用 Xcode 的默认 Archive。
- **Sideloadly 安装失败**：确认 IPA 内含 `Payload/CubeProbe.app`，iPhone 已信任电脑、已打开开发者模式，Apple ID 没有开启双重认证兼容问题。
- **7 天后闪退**：免费签名过期，用 Sideloadly 重新安装即可。

> 本工程已通过 GitHub Actions 实际编译成功；上面的 workflow 产物已在 Release 中。若你修改代码，重新运行 workflow 即可生成新的 IPA。真机安装仍未实测，若 Sideloadly 报错请带上截图/错误信息。
