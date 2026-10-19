方块探针 · CubeProbe 1.0.0（Linux）
====================================

依赖
----
Python 3.10+ 与 Tkinter：
  Debian / Ubuntu:  sudo apt install python3 python3-tk
  Fedora:           sudo dnf install python3 python3-tkinter
  Arch:             sudo pacman -S python tk
  openSUSE:         sudo zypper install python3 python3-tk

启动
----
tar -xzf CubeProbe-1.0.0-Linux-x86_64.tar.gz
cd CubeProbe-1.0.0-Linux
chmod +x CubeProbe CubeProbe.pyz
./CubeProbe

也可以把 CubeProbe.desktop 复制到 ~/.local/share/applications/
（请先把其中的路径改成实际解压目录），或从终端直接启动。

命令行（无需图形界面）
----------------------
  python3 CubeProbe.pyz --cli play.example.com
  python3 CubeProbe.pyz --cli play.example.com --json
  python3 CubeProbe.pyz --cli 1.2.3.4:19132

数据位置
--------
~/.config/CubeProbe/

说明
----
应用直接使用 TCP（Java SLP）和 UDP（Bedrock RakNet）查询服务器，
不经过第三方查询 API。SRV 记录通过 DoH 解析。

本工具是非官方应用，与 Mojang / Microsoft 无关联。
