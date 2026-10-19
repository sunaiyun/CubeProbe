"""Local Minecraft Java + Bedrock mock servers used to verify CubeProbe."""
from __future__ import annotations

import base64
import io
import json
import math
import random
import socket
import struct
import threading
import time
from pathlib import Path

from PIL import Image, ImageDraw

HOST = "0.0.0.0"
JAVA_PORT = 25565
JAVA_ALT_PORT = 25566
BEDROCK_PORT = 19132
MAGIC = bytes((0x00, 0xFF, 0xFF, 0x00, 0xFE, 0xFE, 0xFE, 0xFE,
               0xFD, 0xFD, 0xFD, 0xFD, 0x12, 0x34, 0x56, 0x78))
HERE = Path(__file__).resolve().parent
FAVICON = HERE / "favicon.png"


def make_favicon() -> str:
    size = 64
    image = Image.new("RGBA", (size, size), (8, 23, 32, 255))
    draw = ImageDraw.Draw(image)
    for y in range(size):
        t = y / (size - 1)
        color = (
            int(10 + 5 * t),
            int(34 + 28 * t),
            int(33 + 18 * t),
            255,
        )
        draw.line((0, y, size, y), fill=color)
    # Pixel grid.
    for p in range(0, size, 8):
        draw.line((p, 0, p, size), fill=(255, 255, 255, 10))
        draw.line((0, p, size, p), fill=(255, 255, 255, 10))
    # Isometric cube.
    cx, cy, s = 32, 33, 13
    top = [(cx, cy - s * 1.2), (cx + s * 1.7, cy - s * 0.3),
           (cx, cy + s * 0.6), (cx - s * 1.7, cy - s * 0.3)]
    left = [(cx - s * 1.7, cy - s * 0.3), (cx, cy + s * 0.6),
            (cx, cy + s * 1.9), (cx - s * 1.7, cy + s)]
    right = [(cx + s * 1.7, cy - s * 0.3), (cx + s * 1.7, cy + s),
             (cx, cy + s * 1.9), (cx, cy + s * 0.6)]
    draw.polygon(top, fill=(151, 255, 191, 255))
    draw.polygon(left, fill=(55, 220, 134, 255))
    draw.polygon(right, fill=(18, 143, 83, 255))
    draw.line(top + [top[0]], fill=(7, 43, 27, 200), width=2)
    draw.line(left + [left[0]], fill=(7, 43, 27, 200), width=2)
    draw.line(right + [right[0]], fill=(7, 43, 27, 200), width=2)
    # Sparks.
    draw.rectangle((48, 12, 51, 15), fill=(151, 255, 191, 230))
    draw.rectangle((11, 49, 13, 51), fill=(78, 200, 255, 190))
    buffer = io.BytesIO()
    image.save(buffer, format="PNG")
    data = base64.b64encode(buffer.getvalue()).decode("ascii")
    image.save(FAVICON)
    return "data:image/png;base64," + data


FAVICON_DATA = make_favicon()

JAVA_DESCRIPTION = {
    "text": "",
    "extra": [
        {"text": "方块绿洲", "color": "aqua", "bold": True},
        {"text": "  BLOCK OASIS", "color": "dark_aqua", "bold": False},
        {"text": "\n"},
        {"text": "生存 · 建筑 · 没有边界的方块世界", "color": "green"},
        {"text": "\n"},
        {"text": "Java 1.21.4", "color": "yellow"},
        {"text": "  |  ", "color": "dark_gray"},
        {"text": "正版与离线均可", "color": "gray"},
    ],
}

JAVA_STATUS = {
    "version": {"name": "Paper 1.21.4", "protocol": 769},
    "players": {
        "max": 120,
        "online": 87,
        "sample": [
            {"name": "Steve", "id": "00000000-0000-0000-0000-000000000001"},
            {"name": "Alex", "id": "00000000-0000-0000-0000-000000000002"},
            {"name": "Momo", "id": "00000000-0000-0000-0000-000000000003"},
            {"name": "小矿工", "id": "00000000-0000-0000-0000-000000000004"},
            {"name": "Aurora", "id": "00000000-0000-0000-0000-000000000005"},
            {"name": "Kai", "id": "00000000-0000-0000-0000-000000000006"},
            {"name": "苦力怕", "id": "00000000-0000-0000-0000-000000000007"},
            {"name": "Redstone", "id": "00000000-0000-0000-0000-000000000008"},
            {"name": "Ender", "id": "00000000-0000-0000-0000-000000000009"},
            {"name": "Pumpkin", "id": "00000000-0000-0000-0000-000000000010"},
            {"name": "星野", "id": "00000000-0000-0000-0000-000000000011"},
            {"name": "BlueBird", "id": "00000000-0000-0000-0000-000000000012"},
        ],
    },
    "description": JAVA_DESCRIPTION,
    "favicon": FAVICON_DATA,
    "enforcesSecureChat": True,
}

JAVA_ALT_STATUS = {
    "version": {"name": "Fabric 1.21.1", "protocol": 767},
    "players": {
        "max": 20,
        "online": 6,
        "sample": [{"name": n} for n in ("Creeper", "Slime", "Piston", "Observer")],
    },
    "description": {
        "text": "创造工坊",
        "color": "light_purple",
        "bold": True,
        "extra": [
            {"text": "\n"},
            {"text": "红石 · 机械 · 原版生存", "color": "gray"},
        ],
    },
    "modinfo": {"type": "Fabric"},
}

BEDROCK_MOTD = "方块港湾 · 基岩版"
BEDROCK_SUB = "天空群岛 · 欢迎回家"


def read_exact(stream, count: int) -> bytes:
    data = b""
    while len(data) < count:
        chunk = stream.recv(count - len(data))
        if not chunk:
            raise EOFError("connection closed")
        data += chunk
    return data


def read_varint(stream) -> int:
    value = 0
    position = 0
    while True:
        current = read_exact(stream, 1)[0]
        value |= (current & 0x7F) << position
        if not current & 0x80:
            return value
        position += 7
        if position >= 35:
            raise ValueError("varint too long")


def write_varint(value: int) -> bytes:
    out = bytearray()
    while True:
        part = value & 0x7F
        value >>= 7
        if value:
            out.append(part | 0x80)
        else:
            out.append(part)
            return bytes(out)


def read_string(stream) -> str:
    length = read_varint(stream)
    return read_exact(stream, length).decode("utf-8")


def handle_java(client: socket.socket, status: dict, label: str) -> None:
    try:
        client.settimeout(8)
        packet_length = read_varint(client)
        if packet_length <= 0 or packet_length > 65536:
            return
        packet = read_exact(client, packet_length)
        offset = 0

        def take_varint() -> int:
            nonlocal offset
            value = 0
            position = 0
            while True:
                current = packet[offset]
                offset += 1
                value |= (current & 0x7F) << position
                if not current & 0x80:
                    return value
                position += 7

        take_varint()  # handshake id
        take_varint()  # protocol version
        host_length = take_varint()
        host = packet[offset:offset + host_length].decode("utf-8", "replace")
        offset += host_length
        requested_port = struct.unpack(">H", packet[offset:offset + 2])[0]
        offset += 2
        next_state = take_varint()

        # Status request frame.
        request_length = read_varint(client)
        read_exact(client, request_length)

        payload = json.dumps(status, ensure_ascii=False, separators=(",", ":")).encode("utf-8")
        body = write_varint(0) + write_varint(len(payload)) + payload
        client.sendall(write_varint(len(body)) + body)
        print(f"[java:{label}] {host}:{requested_port} state={next_state} -> status sent", flush=True)
    except Exception as exc:
        print(f"[java:{label}] error: {exc}", flush=True)
    finally:
        try:
            client.close()
        except Exception:
            pass


def java_listener(port: int, status: dict, label: str) -> None:
    server = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    server.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    server.bind((HOST, port))
    server.listen(16)
    print(f"[java:{label}] listening on {port}", flush=True)
    while True:
        try:
            client, addr = server.accept()
            threading.Thread(target=handle_java, args=(client, status, label), daemon=True).start()
        except Exception as exc:
            print(f"[java:{label}] accept error: {exc}", flush=True)
            time.sleep(0.2)


def bedrock_listener() -> None:
    server = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    server.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    server.bind((HOST, BEDROCK_PORT))
    server_guid = 0x1122334455667788
    text = (f"MCPE;{BEDROCK_MOTD};786;1.21.60;32;100;{server_guid};"
            f"{BEDROCK_SUB};Survival;1;19132;19133;")
    payload = text.encode("utf-8")
    print(f"[bedrock] listening on {BEDROCK_PORT}", flush=True)
    while True:
        try:
            data, addr = server.recvfrom(2048)
            if data and data[0] == 0x01:
                timestamp = data[1:9] if len(data) >= 9 else struct.pack(">Q", int(time.time() * 1000))
                response = (b"\x1c" + timestamp + struct.pack(">Q", server_guid)
                            + MAGIC + struct.pack(">H", len(payload)) + payload)
                server.sendto(response, addr)
                print(f"[bedrock] ping from {addr} -> pong", flush=True)
        except Exception as exc:
            print(f"[bedrock] error: {exc}", flush=True)
            time.sleep(0.2)


def main() -> None:
    threading.Thread(target=java_listener, args=(JAVA_PORT, JAVA_STATUS, "main"), daemon=True).start()
    threading.Thread(target=java_listener, args=(JAVA_ALT_PORT, JAVA_ALT_STATUS, "alt"), daemon=True).start()
    threading.Thread(target=bedrock_listener, daemon=True).start()
    print("mock servers ready", flush=True)
    while True:
        time.sleep(60)


if __name__ == "__main__":
    main()
