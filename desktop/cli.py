"""Command-line interface for CubeProbe (Windows / Linux / macOS)."""

from __future__ import annotations

import argparse
import json
import os
import sys
from typing import List

import core

if os.name == "nt":
    try:
        sys.stdout.reconfigure(encoding="utf-8", errors="replace")
        sys.stderr.reconfigure(encoding="utf-8", errors="replace")
        import ctypes
        ctypes.windll.kernel32.SetConsoleOutputCP(65001)
    except Exception:
        pass

RESET = "\033[0m"
BOLD = "\033[1m"
DIM = "\033[2m"
GREEN = "\033[38;5;114m"
CYAN = "\033[38;5;81m"
GREY = "\033[38;5;245m"
RED = "\033[38;5;203m"


def _supports_color() -> bool:
    return sys.stdout.isatty()


def _paint(text: str, color: str, enabled: bool) -> str:
    return f"{color}{text}{RESET}" if enabled else text


def _print_status(status: core.ServerStatus, color: bool) -> None:
    edition = "Bedrock Edition" if status.edition == "bedrock" else "Java Edition"
    title = f"方块探针  ·  {edition}"
    print(_paint(title, BOLD, color))
    print(f"{status.host}:{status.port}"
          + (f"  (SRV → {status.target})" if status.via_srv else ""))
    print()
    for segment in status.motd_segments:
        color_code = segment.get("color") or (GREEN if color else "")
        text = segment["text"]
        if color:
            # Convert #RRGGBB to a 24-bit ANSI colour.
            try:
                r = int(color_code[1:3], 16)
                g = int(color_code[3:5], 16)
                b = int(color_code[5:7], 16)
                prefix = f"\033[38;2;{r};{g};{b}m"
            except Exception:
                prefix = GREEN
            styles = ""
            if segment.get("bold"):
                styles += "\033[1m"
            if segment.get("italic"):
                styles += "\033[3m"
            if segment.get("underline"):
                styles += "\033[4m"
            if segment.get("strike"):
                styles += "\033[9m"
            print(f"{styles}{prefix}{text}{RESET}", end="")
        else:
            print(text, end="")
    print()
    print()
    print(f"在线玩家  {_paint(str(status.online), GREEN if color else '', color)}"
          + (f" / {status.max_players}" if status.max_players else ""))
    print(f"响应      {status.latency_ms} ms")
    print(f"版本      {status.version_name}")
    if status.protocol > 0:
        print(f"协议      {status.protocol}")
    if status.edition == "java":
        if status.software:
            print(f"服务端    {status.software}")
        if status.secure_chat:
            print("安全聊天  已强制")
        if status.legacy:
            print("Ping 方式  Legacy")
        if status.sample:
            print(f"玩家样本  {'  '.join(status.sample[:24])}")
    else:
        if status.game_mode:
            mode_name = {
                "survival": "生存", "creative": "创造",
                "adventure": "冒险", "spectator": "旁观",
            }.get(status.game_mode.strip().lower(), status.game_mode)
            print(f"游戏模式  {mode_name}")
        if status.sub_motd:
            print(f"副标题    {status.sub_motd}")


def _status_json(status: core.ServerStatus) -> str:
    payload = {
        "edition": status.edition,
        "host": status.host,
        "port": status.port,
        "version": status.version_name,
        "protocol": status.protocol,
        "online": status.online,
        "max": status.max_players,
        "latency_ms": status.latency_ms,
        "motd": status.motd_plain,
        "favicon": status.favicon,
        "sample": status.sample,
        "software": status.software,
        "secure_chat": status.secure_chat,
        "game_mode": status.game_mode,
        "sub_motd": status.sub_motd,
        "server_id": status.server_id,
        "legacy": status.legacy,
        "via_srv": status.via_srv,
        "target": status.target,
    }
    return json.dumps(payload, ensure_ascii=False, indent=2)


def _guess_edition(raw: str) -> str:
    text = raw.strip()
    if text.lower().startswith("minecraft://"):
        text = text.split("://", 1)[1]
    if text.startswith("["):
        end = text.find("]")
        rest = text[end + 1:] if end >= 0 else ""
        return "bedrock" if rest.endswith(":19132") else "java"
    if ":" in text and text.rsplit(":", 1)[-1].strip() == "19132":
        return "bedrock"
    return "java"


def main(argv: List[str] = None) -> int:
    parser = argparse.ArgumentParser(
        prog="cubeprobe",
        description="查询 Minecraft Java / Bedrock 服务器状态",
    )
    parser.add_argument("address", help="服务器地址，例如 play.example.com 或 1.2.3.4:25565")
    parser.add_argument("-e", "--edition", choices=("auto", "java", "bedrock"),
                        default="auto", help="服务器版本，默认根据端口自动判断")
    parser.add_argument("--json", action="store_true", help="输出 JSON")
    parser.add_argument("--no-color", action="store_true", help="关闭 ANSI 颜色")
    args = parser.parse_args(argv)

    edition = _guess_edition(args.address) if args.edition == "auto" else args.edition
    color = not args.no_color and _supports_color()
    try:
        address = core.parse_address(args.address, edition)
        if edition == "bedrock":
            status = core.query_bedrock(address)
        else:
            status = core.query_java(address)
    except core.QueryError as exc:
        if args.json:
            print(json.dumps({"ok": False, "kind": exc.kind, "error": str(exc)},
                             ensure_ascii=False))
        else:
            print(_paint(f"查询失败：{exc}", RED, color), file=sys.stderr)
        return 1
    if args.json:
        print(_status_json(status))
    else:
        _print_status(status, color)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
