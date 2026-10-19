方块探针 · CubeProbe 1.0.0（macOS）
====================================

依赖
----
需要 Python 3.10+，并且包含 Tk：
  * 推荐从 https://www.python.org/downloads/macos/ 安装官方 Python，
    官方安装包自带 Tk。
  * 或使用 Homebrew：brew install python python-tk

启动
----
解压后：
  1. 双击 CubeProbe.command
     如果系统提示“无法验证开发者”，请右键 → 打开，或到
     系统设置 → 隐私与安全性 → 仍要打开。
  2. 也可以双击 CubeProbe.app（未签名，首次同样需要右键 → 打开）。

如果双击没反应，打开“终端”，进入解压目录后执行：
  chmod +x CubeProbe.command CubeProbe.pyz
  ./CubeProbe.command

命令行
------
  python3 CubeProbe.pyz --cli play.example.com
  python3 CubeProbe.pyz --cli play.example.com --json

数据位置
--------
~/Library/Application Support/CubeProbe/

限制
----
* 这是未签名的本地构建，没有 Apple Developer 签名与公证；
  因此只能以“右键打开”方式运行，不能直接上架 Mac App Store。
* 没有把 Python 运行时打进 .app，所以目标机器必须先安装带 Tk 的 Python 3。

本工具是非官方应用，与 Mojang / Microsoft 无关联。
