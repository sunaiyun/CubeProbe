方块探针 · CubeProbe 1.0.0（Windows x86 / x64）
=================================================

版本选择
--------
32 位 Windows：双击 CubeProbe-x86.exe
64 位 Windows：双击 CubeProbe-x64.exe（推荐）
32 位 EXE 也能在 64 位 Windows 上运行（WOW64），但 x64 版性能与兼容性更好。

两个 EXE 都是单文件免安装程序，不需要 Python。

兼容范围
--------
使用 Python 3.8.10 + PyInstaller 5.13.2 构建，目标为 Windows 8 / 8.1 / 10 / 11 
（x86 与 x64）。已在 Windows 10 x64 上分别启动两个 EXE 验证。
Windows 7 SP1 未实机验证；Windows 7 需要 UCRT 更新，理论上可能运行，但不作为保证。
Windows XP / Vista 不支持。

SmartScreen / 杀毒软件
---------------------
这是本地构建，没有购买代码签名证书。Windows SmartScreen 可能提示“未知发布者”：
  更多信息 → 仍要运行。

如果双击 EXE 没有反应，通常是系统临时目录不可写或安全软件拦截。
请确认 %TEMP% 可写，或打开“命令提示符”执行：
  set TEMP=%LOCALAPPDATA%\Temp
  set TMP=%LOCALAPPDATA%\Temp
  CubeProbe-x64.exe

如果 EXE 被安全软件误报，可以改用 Python 版：
  1. 安装 Python 3.10+（python.org，勾选 tcl/tk）
  2. 双击 CubeProbe-Python.bat（调用同目录的 CubeProbe.pyz）

功能
----
Java 版：Server List Ping、MOTD 颜色、favicon、玩家样本、SRV
基岩版：RakNet Unconnected Ping、双行 MOTD、人数、模式
本地历史记录、复制状态、重新探测

数据位置
--------
%APPDATA%\CubeProbe\

网络
----
直接 TCP（Java SLP）/ UDP（Bedrock RakNet）连接目标服务器；
Java 默认端口通过 DoH 查询 SRV，失败时回退 25565。

注意
----
本工具是非官方应用，与 Mojang / Microsoft 无关联。
