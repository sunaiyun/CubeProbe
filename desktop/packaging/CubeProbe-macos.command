#!/bin/bash
set -e
DIR="$(cd "$(dirname "$0")" && pwd)"
if ! command -v python3 >/dev/null 2>&1; then
  echo "CubeProbe 需要 Python 3.10+（含 Tkinter）。"
  echo "请从 https://www.python.org/downloads/macos/ 安装官方 Python，"
  echo "或执行: brew install python python-tk"
  read -n 1 -s -r -p "按任意键退出..."
  exit 1
fi
exec python3 "$DIR/CubeProbe.pyz" "$@"
