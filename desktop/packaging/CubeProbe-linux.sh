#!/usr/bin/env bash
set -e
DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
if ! command -v python3 >/dev/null 2>&1; then
  echo "CubeProbe 需要 Python 3.10+（含 Tkinter）。"
  echo "Debian/Ubuntu: sudo apt install python3 python3-tk"
  echo "Fedora: sudo dnf install python3 python3-tkinter"
  exit 1
fi
exec python3 "$DIR/CubeProbe.pyz" "$@"
