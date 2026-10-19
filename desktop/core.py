"""Shared Minecraft server query engine for the desktop clients.

Implements the same protocols as the Android app:
  * Java Edition: Server List Ping (1.7+) with legacy 1.6 fallback
  * Bedrock Edition: RakNet unconnected ping
  * Java SRV discovery through DNS-over-HTTPS

No third-party runtime dependencies: stdlib only (Tkinter is used by gui.py).
"""

from __future__ import annotations

import base64
import json
import random
import socket
import struct
import time
import urllib.parse
import urllib.request
from dataclasses import dataclass, field
from typing import Any, Iterable, List, Optional, Tuple

JAVA_PORT = 25565
BEDROCK_PORT = 19132
RAKNET_MAGIC = bytes(
    (0x00, 0xFF, 0xFF, 0x00, 0xFE, 0xFE, 0xFE, 0xFE,
     0xFD, 0xFD, 0xFD, 0xFD, 0x12, 0x34, 0x56, 0x78)
)


class QueryError(Exception):
    """Typed failure so UIs can explain what went wrong."""

    def __init__(self, kind: str, message: str, cause: Optional[BaseException] = None):
        super().__init__(message)
        self.kind = kind
        self.cause = cause


@dataclass
class ServerAddress:
    host: str
    port: int
    explicit_port: bool

    def display(self) -> str:
        rendered = f"[{self.host}]" if ":" in self.host else self.host
        return f"{rendered}:{self.port}"

    def is_ip_literal(self) -> bool:
        if ":" in self.host:
            return True
        return all(ch.isdigit() or ch == "." for ch in self.host) and bool(self.host)


@dataclass
class ServerStatus:
    edition: str
    host: str
    port: int
    version_name: str
    protocol: int
    online: int
    max_players: int
    latency_ms: int
    description: Any = None
    motd_plain: str = ""
    motd_segments: List[dict] = field(default_factory=list)
    favicon: str = ""
    sample: List[str] = field(default_factory=list)
    software: str = ""
    secure_chat: bool = False
    game_mode: str = ""
    sub_motd: str = ""
    server_id: str = ""
    legacy: bool = False
    via_srv: bool = False
    target: str = ""


# --------------------------------------------------------------------------- #
# Address parsing                                                             #
# --------------------------------------------------------------------------- #

def parse_address(raw: str, edition: str) -> ServerAddress:
    default = BEDROCK_PORT if edition == "bedrock" else JAVA_PORT
    if raw is None:
        raise QueryError("invalid", "empty address")
    text = raw.strip()
    if not text:
        raise QueryError("invalid", "empty address")
    lowered = text.lower()
    for scheme in ("minecraft://", "mc://", "https://", "http://"):
        if lowered.startswith(scheme):
            text = text[len(scheme):]
            break
    if "/" in text:
        text = text.split("/", 1)[0]
    text = text.strip()
    if not text or len(text) > 260:
        raise QueryError("invalid", "bad address")

    explicit = False
    port = default
    if text.startswith("["):
        end = text.find("]")
        if end < 0:
            raise QueryError("invalid", "bad IPv6")
        host = text[1:end]
        rest = text[end + 1:]
        if rest.startswith(":"):
            port = _parse_port(rest[1:])
            explicit = True
        elif rest:
            raise QueryError("invalid", "bad IPv6 suffix")
    else:
        colon_count = text.count(":")
        if colon_count == 1:
            head, tail = text.rsplit(":", 1)
            if not tail or not tail.isdigit():
                raise QueryError("invalid", "bad port")
            host, port, explicit = head, _parse_port(tail), True
        elif colon_count > 1:
            host = text
        else:
            host = text

    host = host.strip()
    if host.endswith("."):
        host = host[:-1]
    if not host or len(host) > 253:
        raise QueryError("invalid", "bad host")
    for ch in host:
        if ch.isspace() or ch in "/\\@?#":
            raise QueryError("invalid", "bad host char")
    if ":" not in host:
        try:
            host = host.encode("idna").decode("ascii")
        except UnicodeError as exc:
            raise QueryError("invalid", "bad IDN", exc)
    return ServerAddress(host, port, explicit)


def _parse_port(value: str) -> int:
    if not value or not value.isdigit() or len(value) > 5:
        raise QueryError("invalid", "bad port")
    port = int(value)
    if port < 1 or port > 65535:
        raise QueryError("invalid", "port out of range")
    return port


# --------------------------------------------------------------------------- #
# SRV via DNS-over-HTTPS                                                      #
# --------------------------------------------------------------------------- #

def resolve_srv(host: str) -> Optional[Tuple[str, int]]:
    if not host or ":" in host:
        return None
    query = f"_minecraft._tcp.{host}"
    encoded = urllib.parse.quote(query, safe="")
    endpoints = (
        f"https://cloudflare-dns.com/dns-query?name={encoded}&type=SRV",
        f"https://dns.alidns.com/resolve?name={encoded}&type=SRV",
        f"https://dns.google/resolve?name={encoded}&type=SRV",
    )
    for endpoint in endpoints:
        record = _srv_from_endpoint(endpoint)
        if record:
            return record
    return None


def _srv_from_endpoint(endpoint: str) -> Optional[Tuple[str, int]]:
    try:
        request = urllib.request.Request(
            endpoint,
            headers={
                "Accept": "application/dns-json",
                "User-Agent": "CubeProbe/1.0 (+desktop)",
            },
        )
        with urllib.request.urlopen(request, timeout=1.4) as response:
            if response.status != 200:
                return None
            body = response.read(65536).decode("utf-8", "replace")
        root = json.loads(body)
        answers = root.get("Answer") or root.get("Answers")
        if not isinstance(answers, list):
            return None
        candidates = []
        for answer in answers:
            if not isinstance(answer, dict) or answer.get("type") != 33:
                continue
            data = str(answer.get("data", "")).strip().split()
            if len(data) < 4:
                continue
            try:
                priority, weight, port = int(data[0]), int(data[1]), int(data[2])
            except ValueError:
                continue
            target = data[3].rstrip(".")
            if not (1 <= port <= 65535) or not target:
                continue
            candidates.append((priority, max(1, weight), target, port))
        if not candidates:
            return None
        min_priority = min(item[0] for item in candidates)
        preferred = [item for item in candidates if item[0] == min_priority]
        total = sum(item[1] for item in preferred)
        roll = random.randint(0, total - 1) if total else 0
        for _, weight, target, port in preferred:
            roll -= weight
            if roll < 0:
                return target, port
        return preferred[-1][2], preferred[-1][3]
    except Exception:
        return None


# --------------------------------------------------------------------------- #
# Java Edition                                                                #
# --------------------------------------------------------------------------- #

def _write_varint(value: int) -> bytes:
    value &= 0xFFFFFFFF
    out = bytearray()
    while True:
        part = value & 0x7F
        value >>= 7
        if value:
            out.append(part | 0x80)
        else:
            out.append(part)
            return bytes(out)


def _read_exact(stream: socket.socket, count: int) -> bytes:
    data = b""
    while len(data) < count:
        chunk = stream.recv(count - len(data))
        if not chunk:
            raise EOFError("connection closed")
        data += chunk
    return data


def _read_varint(stream: socket.socket) -> int:
    value = 0
    position = 0
    while True:
        current = _read_exact(stream, 1)[0]
        value |= (current & 0x7F) << position
        if not current & 0x80:
            return value
        position += 7
        if position >= 35:
            raise QueryError("protocol", "varint too long")


def query_java(address: ServerAddress, timeout_ms: int = 4200) -> ServerStatus:
    target_host, target_port = address.host, address.port
    via_srv = False
    if not address.explicit_port and not address.is_ip_literal():
        record = resolve_srv(address.host)
        if record:
            target_host, target_port = record
            via_srv = True
    try:
        return _java_modern(address, target_host, target_port, via_srv, timeout_ms)
    except QueryError as exc:
        if exc.kind in ("unknown_host", "network", "invalid"):
            raise
        return _java_legacy(address, target_host, target_port, via_srv, timeout_ms)


def _java_modern(address: ServerAddress, target_host: str, target_port: int,
                 via_srv: bool, timeout_ms: int) -> ServerStatus:
    started = time.monotonic()
    try:
        with socket.create_connection((target_host, target_port), timeout=timeout_ms / 1000) as sock:
            sock.settimeout(timeout_ms / 1000)
            host_bytes = address.host.encode("utf-8")
            handshake = (_write_varint(0) + _write_varint(-1)
                         + _write_varint(len(host_bytes)) + host_bytes
                         + struct.pack(">H", target_port) + _write_varint(1))
            sock.sendall(_write_varint(len(handshake)) + handshake)
            sock.sendall(_write_varint(1) + b"\x00")

            length = _read_varint(sock)
            if length <= 0 or length > 2 * 1024 * 1024:
                raise QueryError("protocol", "bad packet length")
            payload = _read_exact(sock, length)
            cursor = 0

            def take_varint() -> int:
                nonlocal cursor
                value = 0
                position = 0
                while True:
                    if cursor >= len(payload):
                        raise QueryError("protocol", "truncated packet")
                    current = payload[cursor]
                    cursor += 1
                    value |= (current & 0x7F) << position
                    if not current & 0x80:
                        return value
                    position += 7
                    if position >= 35:
                        raise QueryError("protocol", "varint too long")

            packet_id = take_varint()
            if packet_id != 0:
                raise QueryError("protocol", "unexpected packet id")
            text_length = take_varint()
            if text_length < 0 or text_length > len(payload) - cursor:
                raise QueryError("protocol", "bad string length")
            raw_json = payload[cursor:cursor + text_length].decode("utf-8", "replace")
            status = _parse_java_status(address, target_host, target_port, via_srv, raw_json)
            status.latency_ms = max(1, round((time.monotonic() - started) * 1000))
            return status
    except QueryError:
        raise
    except socket.timeout as exc:
        raise QueryError("timeout", "Java SLP timeout", exc)
    except ConnectionRefusedError as exc:
        raise QueryError("refused", "connection refused", exc)
    except socket.gaierror as exc:
        raise QueryError("unknown_host", "unknown host", exc)
    except (ConnectionResetError, BrokenPipeError, EOFError, OSError) as exc:
        raise QueryError("protocol", f"connection failed: {exc}", exc)


def _java_legacy(address: ServerAddress, target_host: str, target_port: int,
                 via_srv: bool, timeout_ms: int) -> ServerStatus:
    started = time.monotonic()
    try:
        with socket.create_connection((target_host, target_port), timeout=timeout_ms / 1000) as sock:
            sock.settimeout(timeout_ms / 1000)
            sock.sendall(b"\xfe\x01")
            first = _read_exact(sock, 1)
            if first != b"\xff":
                raise QueryError("protocol", "not a legacy response")
            length = struct.unpack(">H", _read_exact(sock, 2))[0]
            if length <= 0 or length > 32767:
                raise QueryError("protocol", "bad legacy length")
            text = _read_exact(sock, length * 2).decode("utf-16-be", "replace")
            fields = text.split("\x00")
            motd = fields[3] if len(fields) > 3 else text
            version = fields[2] if len(fields) > 2 else "旧版服务器"
            protocol = _to_int(fields[1], -1) if len(fields) > 1 else -1
            online = _to_int(fields[4], 0) if len(fields) > 4 else 0
            maximum = _to_int(fields[5], 0) if len(fields) > 5 else 0
            return _make_status("java", address, target_host, target_port, via_srv,
                                version, protocol, online, maximum, motd,
                                started, legacy=True)
    except QueryError:
        raise
    except socket.timeout as exc:
        raise QueryError("timeout", "legacy ping timeout", exc)
    except socket.gaierror as exc:
        raise QueryError("unknown_host", "unknown host", exc)
    except ConnectionRefusedError as exc:
        raise QueryError("refused", "connection refused", exc)
    except OSError as exc:
        raise QueryError("protocol", f"legacy ping failed: {exc}", exc)


def _parse_java_status(address: ServerAddress, target_host: str, target_port: int,
                       via_srv: bool, raw_json: str) -> ServerStatus:
    try:
        root = json.loads(raw_json)
    except ValueError as exc:
        raise QueryError("protocol", "invalid status json", exc)
    version = root.get("version") or {}
    players = root.get("players") or {}
    version_name = str(version.get("name") or "未知版本")
    protocol = _to_int(version.get("protocol"), -1)
    online = max(0, _to_int(players.get("online"), 0))
    maximum = max(0, _to_int(players.get("max"), 0))
    sample = []
    for item in (players.get("sample") or [])[:80]:
        if isinstance(item, dict) and str(item.get("name") or "").strip():
            sample.append(str(item["name"]))
        elif isinstance(item, str) and item.strip():
            sample.append(item)
    description = root.get("description")
    favicon = str(root.get("favicon") or "")
    software = ""
    mod_info = root.get("modinfo") or root.get("modInfo")
    if isinstance(mod_info, dict):
        software = str(mod_info.get("type") or "Modded")
    if root.get("forgeData") is not None:
        software = "Forge"
    secure = bool(root.get("enforcesSecureChat"))
    status = _make_status("java", address, target_host, target_port, via_srv,
                          version_name, protocol, online, maximum, description,
                          None)
    status.favicon = favicon
    status.sample = sample
    status.software = software
    status.secure_chat = secure
    return status


# --------------------------------------------------------------------------- #
# Bedrock Edition                                                             #
# --------------------------------------------------------------------------- #

def query_bedrock(address: ServerAddress, timeout_ms: int = 4000) -> ServerStatus:
    started = time.monotonic()
    try:
        infos = socket.getaddrinfo(address.host, address.port, type=socket.SOCK_DGRAM)
    except socket.gaierror as exc:
        raise QueryError("unknown_host", "unknown host", exc)
    wait = max(0.7, min(1.2, timeout_ms / 3000))
    packet = b"\x01" + struct.pack(">Q", int(time.time() * 1000)) \
        + RAKNET_MAGIC + struct.pack(">Q", random.getrandbits(64))
    last_error: Optional[BaseException] = None
    try:
        with socket.socket(socket.AF_INET, socket.SOCK_DGRAM) as sock:
            sock.settimeout(wait)
            for _ in range(3):
                sock.sendto(packet, infos[0][4])
                deadline = time.monotonic() + wait
                while True:
                    remaining = deadline - time.monotonic()
                    if remaining <= 0:
                        break
                    sock.settimeout(remaining)
                    try:
                        data, _ = sock.recvfrom(4096)
                    except socket.timeout:
                        break
                    status = _parse_bedrock(address, data, started)
                    if status:
                        return status
        raise QueryError("timeout", "no bedrock pong")
    except QueryError:
        raise
    except ConnectionRefusedError as exc:
        raise QueryError("refused", "port unreachable", exc)
    except OSError as exc:
        raise QueryError("protocol", f"bedrock ping failed: {exc}", exc)


def _parse_bedrock(address: ServerAddress, data: bytes, started: float) -> Optional[ServerStatus]:
    if len(data) < 35 or data[0] != 0x1C:
        return None
    if data[17:33] != RAKNET_MAGIC:
        return None
    text_length = struct.unpack(">H", data[33:35])[0]
    if text_length <= 0 or text_length > len(data) - 35:
        return None
    text = data[35:35 + text_length].decode("utf-8", "replace")
    fields = text.split(";")
    if len(fields) < 6:
        return None
    motd = fields[1] if len(fields) > 1 else ""
    protocol = _to_int(fields[2], -1) if len(fields) > 2 else -1
    version = fields[3] if len(fields) > 3 else ""
    online = max(0, _to_int(fields[4], 0)) if len(fields) > 4 else 0
    maximum = max(0, _to_int(fields[5], 0)) if len(fields) > 5 else 0
    server_id = fields[6] if len(fields) > 6 else ""
    sub_motd = fields[7] if len(fields) > 7 else ""
    game_mode = fields[8] if len(fields) > 8 else ""
    status = _make_status("bedrock", address, address.host, address.port, False,
                          version, protocol, online, maximum, motd, started)
    status.sub_motd = sub_motd
    status.game_mode = game_mode
    status.server_id = server_id
    return status


def _make_status(edition: str, address: ServerAddress, target_host: str, target_port: int,
                 via_srv: bool, version: str, protocol: int, online: int, maximum: int,
                 description: Any, started: Optional[float]) -> ServerStatus:
    latency = max(1, round((time.monotonic() - started) * 1000)) if started else 1
    segments = motd_segments(description)
    plain = "".join(seg["text"] for seg in segments).strip()
    if not plain:
        plain = "A Minecraft Server" if edition == "java" else "Minecraft Bedrock Server"
        segments = [{"text": plain, "color": None, "bold": False, "italic": False,
                     "underline": False, "strike": False}]
    return ServerStatus(
        edition=edition, host=address.host, port=target_port,
        version_name=version or "未知版本", protocol=protocol,
        online=online, max_players=maximum, latency_ms=latency,
        description=description, motd_plain=plain, motd_segments=segments,
        via_srv=via_srv,
        target=f"{target_host}:{target_port}" if via_srv else "",
    )


# --------------------------------------------------------------------------- #
# MOTD colour handling                                                        #
# --------------------------------------------------------------------------- #

# Tuned brighter for the dark surface, mirroring the Android palette.
_LEGACY_HEX = (
    "#9AA7B4", "#7C8CFF", "#4ADE80", "#2DD4BF", "#FB7185", "#C084FC",
    "#FBBF24", "#A3B0BF", "#7C8C99", "#60A5FA", "#4ADE80", "#67E8F9",
    "#F87171", "#F0ABFC", "#FDE047", "#F8FAFC",
)
_NAMED = {
    "black": "#9AA7B4", "dark_blue": "#7C8CFF", "dark_green": "#4ADE80",
    "dark_aqua": "#2DD4BF", "dark_red": "#FB7185", "dark_purple": "#C084FC",
    "gold": "#FBBF24", "gray": "#A3B0BF", "dark_gray": "#7C8C99",
    "blue": "#60A5FA", "green": "#4ADE80", "aqua": "#67E8F9",
    "red": "#F87171", "light_purple": "#F0ABFC", "yellow": "#FDE047",
    "white": "#F8FAFC",
}
_STYLE_KEYS = ("color", "bold", "italic", "underline", "strike")


def _default_style() -> dict:
    return {"color": None, "bold": False, "italic": False,
            "underline": False, "strike": False}


def motd_segments(description: Any) -> List[dict]:
    segments: List[dict] = []
    _append_description(segments, description, _default_style(), 0)
    merged: List[dict] = []
    for segment in segments:
        if merged and all(merged[-1][key] == segment[key] for key in _STYLE_KEYS):
            merged[-1]["text"] += segment["text"]
        else:
            merged.append(dict(segment))
    return merged


def _append_description(out: List[dict], node: Any, base: dict, depth: int) -> None:
    if node is None or depth > 16:
        return
    if isinstance(node, str):
        _append_styled_text(out, node, base)
        return
    if isinstance(node, list):
        for child in node:
            _append_description(out, child, base, depth + 1)
        return
    if not isinstance(node, dict):
        _append_styled_text(out, str(node), base)
        return
    style = dict(base)
    if "color" in node:
        color = str(node.get("color") or "")
        if color.lower() == "reset" or not color:
            style["color"] = None
        else:
            parsed = _parse_color(color)
            if parsed:
                style["color"] = parsed
    for key, flag in (("bold", "bold"), ("italic", "italic"),
                      ("underlined", "underline"), ("strikethrough", "strike")):
        if key in node:
            style[flag] = bool(node.get(key))
    text = node.get("text")
    if isinstance(text, str) and text:
        _append_styled_text(out, text, style)
    extra = node.get("extra")
    if isinstance(extra, list):
        for child in extra:
            _append_description(out, child, style, depth + 1)


def _append_styled_text(out: List[dict], text: str, base: dict) -> None:
    style = dict(base)
    index = 0
    while index < len(text):
        char = text[index]
        if char == "\u00a7" and index + 1 < len(text):
            code = text[index + 1].lower()
            if code == "x" and index + 13 < len(text):
                parsed = _parse_hex_sequence(text, index + 2)
                if parsed:
                    style["color"] = parsed
                    index += 14
                    continue
            if _apply_legacy_code(style, base, code):
                index += 2
                continue
            index += 1
            continue
        end = text.find("\u00a7", index)
        if end < 0:
            end = len(text)
        if end > index:
            out.append({"text": text[index:end], **_style_values(style)})
        index = end


def _style_values(style: dict) -> dict:
    return {key: style[key] for key in _STYLE_KEYS}


def _apply_legacy_code(style: dict, base: dict, code: str) -> bool:
    if "0" <= code <= "9":
        style["color"] = _LEGACY_HEX[ord(code) - ord("0")]
        return True
    if "a" <= code <= "f":
        style["color"] = _LEGACY_HEX[10 + ord(code) - ord("a")]
        return True
    if code == "k":
        return True
    if code == "l":
        style["bold"] = True
        return True
    if code == "m":
        style["strike"] = True
        return True
    if code == "n":
        style["underline"] = True
        return True
    if code == "o":
        style["italic"] = True
        return True
    if code == "r":
        for key in _STYLE_KEYS:
            style[key] = base[key]
        return True
    return False


def _parse_hex_sequence(text: str, start: int) -> Optional[str]:
    if start + 12 > len(text):
        return None
    digits = []
    for i in range(6):
        position = start + i * 2
        if text[position] != "\u00a7" or text[position + 1] not in "0123456789abcdefABCDEF":
            return None
        digits.append(text[position + 1])
    return "#" + "".join(digits).upper()


def _parse_color(value: str) -> Optional[str]:
    lowered = value.lower()
    if lowered in _NAMED:
        return _NAMED[lowered]
    if lowered.startswith("#"):
        hex_part = lowered[1:]
        if len(hex_part) == 3:
            hex_part = "".join(ch * 2 for ch in hex_part)
        if len(hex_part) == 6 and all(ch in "0123456789abcdef" for ch in hex_part):
            return "#" + hex_part.upper()
    return None


def _to_int(value: Any, fallback: int) -> int:
    try:
        return int(value)
    except (TypeError, ValueError):
        return fallback


def query(address: ServerAddress) -> ServerStatus:
    """Query either edition based on the address' default port choice."""
    return query_bedrock(address) if address.port == BEDROCK_PORT else query_java(address)
