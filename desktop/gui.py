"""Tkinter desktop client for CubeProbe — Windows, Linux and macOS."""

from __future__ import annotations

import base64
import json
import os
import queue
import sys
import threading
import time
import tkinter as tk
from pathlib import Path
from tkinter import font as tkfont
from tkinter import messagebox
from typing import List, Optional

import core

APP_NAME = "方块探针"
APP_TAGLINE = "MINECRAFT SERVER CHECKER"
VERSION = "1.0.0"

BG = "#06101C"
SURFACE = "#0E1D2D"
SURFACE_HI = "#152A3D"
PANEL = "#0A1725"
INPUT = "#081724"
LINE = "#233B4E"
TEXT = "#F2F8F5"
MUTED = "#8FA3B8"
SOFT = "#5F7488"
ACCENT = "#62F2A2"
ACCENT_DARK = "#042114"
ACCENT_SOFT = "#153326"
BEDROCK = "#4EC8FF"
BEDROCK_DARK = "#062330"
BEDROCK_SOFT = "#0E2E3F"
DANGER = "#FF6D7D"
ERROR_BG = "#2A1820"


def _config_dir() -> Path:
    if os.name == "nt":
        base = Path(os.environ.get("APPDATA") or Path.home())
    elif sys.platform == "darwin":
        base = Path.home() / "Library" / "Application Support"
    else:
        base = Path(os.environ.get("XDG_CONFIG_HOME") or (Path.home() / ".config"))
    return base / "CubeProbe"


def _resource_path(*parts: str) -> Path:
    root = Path(getattr(sys, "_MEIPASS", Path(__file__).resolve().parent))
    return root.joinpath(*parts)


def _format_count(value: int) -> str:
    if value < 1000:
        return str(value)
    if value < 1_000_000:
        rendered = f"{value / 1000:.1f}".rstrip("0").rstrip(".")
        return rendered + "k"
    rendered = f"{value / 1_000_000:.1f}".rstrip("0").rstrip(".")
    return rendered + "M"


def _relative_time(timestamp: float) -> str:
    if not timestamp:
        return "刚刚"
    delta = time.time() - timestamp
    if delta < 60:
        return "刚刚"
    if delta < 3600:
        return f"{int(delta // 60)} 分钟前"
    if delta < 86400:
        return f"{int(delta // 3600)} 小时前"
    if delta < 86400 * 7:
        return f"{int(delta // 86400)} 天前"
    return time.strftime("%m月%d日", time.localtime(timestamp))


def _pretty_game_mode(mode: str) -> str:
    return {
        "survival": "生存",
        "creative": "创造",
        "adventure": "冒险",
        "spectator": "旁观",
    }.get((mode or "").strip().lower(), (mode or "").strip())


def _pretty_software(value: str) -> str:
    normalized = (value or "").strip()
    if not normalized:
        return ""
    lower = normalized.lower()
    if lower in ("fml", "forge"):
        return "Forge"
    if "fabric" in lower:
        return "Fabric"
    if "quilt" in lower:
        return "Quilt"
    if lower == "modded":
        return "Modded"
    return normalized


def error_message(error: core.QueryError, mode: str) -> str:
    kind = error.kind
    if kind == "unknown_host":
        return "找不到这个服务器，请检查域名或网络连接。"
    if kind == "timeout":
        if mode == "bedrock":
            return "没有收到基岩版响应。请确认端口（默认 19132）以及服务器是否在线。"
        return "连接超时。请确认端口开放、服务器在线，并检查 Java / 基岩版是否选对。"
    if kind == "refused":
        return "连接被拒绝，端口可能未开放。"
    if kind == "network":
        return "网络不可用，请检查网络连接。"
    if kind == "invalid":
        message = str(error)
        if "port" in message:
            return "端口必须介于 1 和 65535 之间。"
        return "服务器地址格式不正确。"
    if kind == "protocol":
        return "服务器返回了无法识别的数据，或这不是对应版本的服务器。"
    return f"查询失败：{error}"


class CubeProbeApp:
    ICON_SIZE = 64

    def __init__(self, root: tk.Tk):
        self.root = root
        self.mode = "java"
        self.busy = False
        self.generation = 0
        self.worker_queue: queue.Queue = queue.Queue()
        self.history: List[dict] = []
        self.last_status: Optional[core.ServerStatus] = None
        self.last_address: Optional[core.ServerAddress] = None
        self._segment_fonts = []
        self._accent = ACCENT
        self.config_dir = _config_dir()
        self.state_file = self.config_dir / "state.json"
        self.history_file = self.config_dir / "history.json"

        self.ui_family, self.mono_family = self._pick_fonts()
        self.font_title = tkfont.Font(family=self.ui_family, size=22, weight="bold")
        self.font_h1 = tkfont.Font(family=self.ui_family, size=18, weight="bold")
        self.font_section = tkfont.Font(family=self.ui_family, size=12, weight="bold")
        self.font_body = tkfont.Font(family=self.ui_family, size=10)
        self.font_small = tkfont.Font(family=self.ui_family, size=9)
        self.font_tiny = tkfont.Font(family=self.ui_family, size=8)
        self.font_eyebrow = tkfont.Font(family=self.ui_family, size=8, weight="bold")
        self.font_button = tkfont.Font(family=self.ui_family, size=10, weight="bold")
        self.font_stat = tkfont.Font(family=self.ui_family, size=17, weight="bold")
        self.font_mono = tkfont.Font(family=self.mono_family, size=9)

        self._configure_window()
        self._build_scroll_area()
        self._build_header()
        self._build_query_card()
        self._build_status_rows()
        self._build_result_card()
        self._build_history_card()
        self._build_footer()
        self._load_state()
        self._load_history()
        self._render_history()
        self._update_mode_styles()

    # ------------------------------------------------------------------ #
    # Setup                                                              #
    # ------------------------------------------------------------------ #

    def _pick_fonts(self):
        available = set(tkfont.families(self.root))
        for family in ("Microsoft YaHei UI", "Microsoft YaHei", "PingFang SC",
                       "Hiragino Sans GB", "Noto Sans CJK SC", "Noto Sans SC",
                       "Source Han Sans SC", "WenQuanYi Micro Hei", "Segoe UI",
                       "Helvetica Neue", "Arial"):
            if family in available:
                ui_family = family
                break
        else:
            ui_family = "TkDefaultFont"
        for family in ("Cascadia Mono", "Cascadia Code", "Consolas", "SF Mono",
                       "Menlo", "DejaVu Sans Mono", "Courier New"):
            if family in available:
                mono = family
                break
        else:
            mono = "TkFixedFont"
        return ui_family, mono

    def _configure_window(self):
        self.root.title(f"{APP_NAME} · CubeProbe")
        self.root.configure(bg=BG)
        self.root.minsize(420, 640)
        width, height = 500, 920
        screen_w = self.root.winfo_screenwidth()
        screen_h = self.root.winfo_screenheight()
        x = max(0, (screen_w - width) // 2)
        y = max(0, (screen_h - height) // 2 - 20)
        self.root.geometry(f"{width}x{height}+{x}+{y}")
        try:
            icon = _resource_path("assets", "icon.png")
            if icon.exists():
                self._window_icon = tk.PhotoImage(file=str(icon))
                self.root.iconphoto(True, self._window_icon)
        except Exception:
            pass
        self.root.bind_all("<MouseWheel>", self._on_mousewheel, add="+")
        self.root.bind_all("<Button-4>", lambda e: self._scroll(-3), add="+")
        self.root.bind_all("<Button-5>", lambda e: self._scroll(3), add="+")
        self.root.bind("<Prior>", lambda e: self._scroll(-6), add="+")
        self.root.bind("<Next>", lambda e: self._scroll(6), add="+")
        self.root.protocol("WM_DELETE_WINDOW", self._on_close)

    def _build_scroll_area(self):
        self.canvas = tk.Canvas(self.root, bg=BG, highlightthickness=0, bd=0)
        self.canvas.pack(side="left", fill="both", expand=True)
        self.content = tk.Frame(self.canvas, bg=BG)
        self.content_id = self.canvas.create_window((0, 0), window=self.content, anchor="nw")
        self.content.grid_columnconfigure(0, weight=1)
        self.content.bind("<Configure>", self._on_content_configure)
        self.canvas.bind("<Configure>", self._on_canvas_configure)

    def _on_content_configure(self, _event=None):
        self.canvas.configure(scrollregion=self.canvas.bbox("all"))

    def _on_canvas_configure(self, event):
        self.canvas.itemconfigure(self.content_id, width=event.width)

    def _scroll(self, amount: int):
        self.canvas.yview_scroll(amount, "units")

    def _on_close(self):
        self._save_state()
        self.root.destroy()

    def _on_mousewheel(self, event):
        if isinstance(event.widget, (tk.Text, tk.Listbox)):
            return
        delta = event.delta
        if abs(delta) >= 120:
            delta = int(delta / 120)
        else:
            delta = -1 if delta > 0 else 1
        self.canvas.yview_scroll(-delta, "units")

    # ------------------------------------------------------------------ #
    # Small UI helpers                                                   #
    # ------------------------------------------------------------------ #

    def _label(self, parent, text="", *, fg=TEXT, bg=None, font=None,
               wraplength=0, justify="left", anchor="w", padx=0, pady=0):
        return tk.Label(
            parent, text=text, fg=fg, bg=bg or parent.cget("bg"),
            font=font or self.font_body, wraplength=wraplength,
            justify=justify, anchor=anchor, padx=padx, pady=pady,
            highlightthickness=0, bd=0,
        )

    def _button(self, parent, text, command, *, primary=False, fg=TEXT,
                bg=SURFACE_HI, active_bg=None, font=None, padx=14, pady=7):
        return tk.Button(
            parent, text=text, command=command, fg=fg, bg=bg,
            activeforeground=fg, activebackground=active_bg or bg,
            disabledforeground=SOFT, relief="flat", bd=0,
            font=font or self.font_button, padx=padx, pady=pady,
            cursor="hand2" if not primary else "hand2",
            highlightthickness=0, takefocus=1,
        )

    def _card(self, row: int, pady=(0, 14)) -> tk.Frame:
        outer = tk.Frame(self.content, bg=BG)
        outer.grid(row=row, column=0, sticky="ew", padx=22, pady=pady)
        outer.grid_columnconfigure(0, weight=1)
        card = tk.Frame(outer, bg=SURFACE, highlightbackground=LINE,
                        highlightthickness=1, bd=0)
        card.grid(row=0, column=0, sticky="ew")
        card.grid_columnconfigure(0, weight=1)
        return card

    # ------------------------------------------------------------------ #
    # Header                                                             #
    # ------------------------------------------------------------------ #

    def _build_header(self):
        header = tk.Frame(self.content, bg=BG)
        header.grid(row=0, column=0, sticky="ew", padx=22, pady=(18, 16))
        header.grid_columnconfigure(1, weight=1)

        logo = tk.Canvas(header, width=44, height=44, bg=BG,
                         highlightthickness=0, bd=0)
        logo.grid(row=0, column=0, rowspan=2, sticky="w")
        self._draw_logo(logo, 44)

        title_box = tk.Frame(header, bg=BG)
        title_box.grid(row=0, column=1, sticky="w", padx=(12, 8))
        self._label(title_box, APP_NAME, fg=TEXT, font=self.font_title).pack(anchor="w")
        self._label(title_box, APP_TAGLINE, fg=SOFT, font=self.font_eyebrow).pack(
            anchor="w", pady=(2, 0))

        badge = tk.Label(
            header, text="●  直连查询", fg=MUTED, bg=BG, font=self.font_small,
            padx=10, pady=4, bd=0, highlightthickness=1,
            highlightbackground=LINE,
        )
        badge.grid(row=0, column=2, sticky="e")

    def _draw_logo(self, canvas: tk.Canvas, size: int):
        canvas.delete("all")
        pad = 1
        canvas.create_rectangle(pad, pad, size - pad, size - pad,
                                fill="#2AD57F", outline="#7CF5B0", width=1)
        cx, cy = size / 2, size / 2 + 1
        s = size * 0.205
        top = [(cx, cy - s * 1.35), (cx + s * 1.72, cy - s * 0.4),
               (cx, cy + s * 0.52), (cx - s * 1.72, cy - s * 0.4)]
        left = [(cx - s * 1.72, cy - s * 0.4), (cx, cy + s * 0.52),
                (cx, cy + s * 1.75), (cx - s * 1.72, cy + s * 0.9)]
        right = [(cx + s * 1.72, cy - s * 0.4), (cx + s * 1.72, cy + s * 0.9),
                 (cx, cy + s * 1.75), (cx, cy + s * 0.52)]
        canvas.create_polygon(top, fill="#04331E", outline="#0A5B35")
        canvas.create_polygon(left, fill="#022415", outline="#0A5B35")
        canvas.create_polygon(right, fill="#01180D", outline="#0A5B35")
        canvas.create_oval(size - 12, 8, size - 8, 12, fill="#D7FFE9", outline="")

    # ------------------------------------------------------------------ #
    # Query card                                                         #
    # ------------------------------------------------------------------ #

    def _build_query_card(self):
        card = self._card(1, pady=(0, 16))
        inner = tk.Frame(card, bg=SURFACE)
        inner.grid(row=0, column=0, sticky="ew", padx=20, pady=20)
        inner.grid_columnconfigure(0, weight=1)

        self._label(inner, "SERVER PULSE", fg=ACCENT, font=self.font_eyebrow).grid(
            row=0, column=0, sticky="w")
        self._label(inner, "听见服务器的脉搏", fg=TEXT, font=self.font_title).grid(
            row=1, column=0, sticky="w", pady=(8, 4))
        subtitle = ("直连 Java 与基岩版服务器\n"
                    "读取 MOTD、在线人数与响应速度。")
        self._label(inner, subtitle, fg=MUTED, font=self.font_body,
                    wraplength=400).grid(row=2, column=0, sticky="w", pady=(0, 14))

        switch = tk.Frame(inner, bg=INPUT, highlightbackground=LINE,
                          highlightthickness=1, bd=0)
        switch.grid(row=3, column=0, sticky="ew", pady=(0, 16))
        switch.grid_columnconfigure(0, weight=1)
        switch.grid_columnconfigure(1, weight=1)
        self.java_button = self._button(switch, "●  Java 版",
                                        lambda: self._set_mode("java"),
                                        font=self.font_button, pady=8)
        self.java_button.grid(row=0, column=0, sticky="ew", padx=4, pady=4)
        self.bedrock_button = self._button(switch, "●  基岩版",
                                           lambda: self._set_mode("bedrock"),
                                           font=self.font_button, pady=8)
        self.bedrock_button.grid(row=0, column=1, sticky="ew", padx=4, pady=4)

        self._label(inner, "服务器地址", fg=MUTED, font=self.font_small).grid(
            row=4, column=0, sticky="w", pady=(0, 6))

        input_row = tk.Frame(inner, bg=SURFACE)
        input_row.grid(row=5, column=0, sticky="ew")
        input_row.grid_columnconfigure(0, weight=1)
        self.entry = tk.Entry(
            input_row, bg=INPUT, fg=TEXT, insertbackground=ACCENT,
            disabledbackground=INPUT, disabledforeground=SOFT,
            relief="flat", bd=0, highlightthickness=1,
            highlightbackground=LINE, highlightcolor=ACCENT,
            font=self.font_body, width=24,
        )
        self.entry.grid(row=0, column=0, sticky="ew", ipady=10, padx=(0, 8))
        self.entry.bind("<Return>", lambda _e: self.start_query())
        self.query_button = tk.Button(
            input_row, text="查询", command=self.start_query,
            bg=ACCENT, fg=ACCENT_DARK, activebackground="#38DC85",
            activeforeground=ACCENT_DARK, disabledforeground="#2D5C46",
            relief="flat", bd=0, font=self.font_button, padx=20, pady=10,
            cursor="hand2", highlightthickness=0,
        )
        self.query_button.grid(row=0, column=1, sticky="ns")

        hint_row = tk.Frame(inner, bg=SURFACE)
        hint_row.grid(row=6, column=0, sticky="ew", pady=(7, 0))
        hint_row.grid_columnconfigure(0, weight=1)
        self.hint_label = self._label(hint_row, "", fg=SOFT, font=self.font_tiny)
        self.hint_label.grid(row=0, column=0, sticky="w")
        self.paste_button = self._button(
            hint_row, "粘贴", self.paste_address, fg=ACCENT, bg=INPUT,
            active_bg="#0B2430", font=self.font_small, padx=10, pady=4)
        self.paste_button.grid(row=0, column=1, sticky="e")

    def _update_mode_styles(self):
        if self.mode == "java":
            self.java_button.configure(bg=ACCENT_SOFT, fg=ACCENT,
                                       activebackground=ACCENT_SOFT,
                                       activeforeground=ACCENT)
            self.bedrock_button.configure(bg=INPUT, fg=SOFT,
                                          activebackground=SURFACE_HI,
                                          activeforeground=TEXT)
            self.hint_label.configure(text="默认 25565 · 支持域名与 SRV")
        else:
            self.java_button.configure(bg=INPUT, fg=SOFT,
                                       activebackground=SURFACE_HI,
                                       activeforeground=TEXT)
            self.bedrock_button.configure(bg=BEDROCK_SOFT, fg=BEDROCK,
                                          activebackground=BEDROCK_SOFT,
                                          activeforeground=BEDROCK)
            self.hint_label.configure(text="默认 19132 · UDP 直连")

    def _set_mode(self, mode: str):
        if self.busy or mode == self.mode:
            return
        self.mode = mode
        self._update_mode_styles()
        self._hide_result()
        self._hide_error()
        self._save_state()

    def paste_address(self):
        try:
            text = self.root.clipboard_get()
        except tk.TclError:
            text = ""
        if text.strip():
            self.entry.delete(0, "end")
            self.entry.insert(0, text.strip())
            self.query_button.focus_set()

    # ------------------------------------------------------------------ #
    # Status / error                                                     #
    # ------------------------------------------------------------------ #

    def _build_status_rows(self):
        self.loading_label = tk.Label(
            self.content, text="", bg=BG, fg=ACCENT, font=self.font_small,
            anchor="w", padx=24, pady=8,
        )
        self.loading_label.grid(row=2, column=0, sticky="ew")
        self.loading_label.grid_remove()

        self.error_frame = tk.Frame(self.content, bg=ERROR_BG,
                                    highlightbackground="#542C38",
                                    highlightthickness=1)
        self.error_frame.grid(row=3, column=0, sticky="ew", padx=22, pady=(0, 14))
        self.error_frame.grid_columnconfigure(0, weight=1)
        self.error_label = tk.Label(
            self.error_frame, text="", bg=ERROR_BG, fg="#FFD3D9",
            font=self.font_small, anchor="w", justify="left",
            wraplength=400, padx=12, pady=10,
        )
        self.error_label.grid(row=0, column=0, sticky="ew")
        self.error_frame.grid_remove()

    def _show_loading(self, text: str):
        self.loading_label.configure(text=text)
        self.loading_label.grid()

    def _hide_loading(self):
        self.loading_label.grid_remove()

    def _show_error(self, text: str):
        self.error_label.configure(text=text)
        self.error_frame.grid()
        self._hide_loading()

    def _hide_error(self):
        self.error_frame.grid_remove()

    # ------------------------------------------------------------------ #
    # Result card                                                        #
    # ------------------------------------------------------------------ #

    def _build_result_card(self):
        self.result_outer = tk.Frame(self.content, bg=BG)
        self.result_outer.grid(row=4, column=0, sticky="ew", padx=22, pady=(0, 16))
        self.result_outer.grid_columnconfigure(0, weight=1)
        self.result_frame = tk.Frame(self.result_outer, bg=SURFACE,
                                     highlightbackground=LINE, highlightthickness=1)
        self.result_frame.grid(row=0, column=0, sticky="ew")
        self.result_frame.grid_columnconfigure(0, weight=1)
        inner = tk.Frame(self.result_frame, bg=SURFACE)
        inner.grid(row=0, column=0, sticky="ew", padx=18, pady=18)
        inner.grid_columnconfigure(1, weight=1)

        self.icon_canvas = tk.Canvas(inner, width=self.ICON_SIZE, height=self.ICON_SIZE,
                                     bg=SURFACE, highlightthickness=0, bd=0)
        self.icon_canvas.grid(row=0, column=0, rowspan=2, sticky="nw")
        self.icon_photo = None

        self.host_label = tk.Label(inner, text="", bg=SURFACE, fg=TEXT,
                                   font=self.font_h1, anchor="w")
        self.host_label.grid(row=0, column=1, sticky="sw", padx=(12, 8))
        self.meta_label = tk.Label(inner, text="", bg=SURFACE, fg=MUTED,
                                   font=self.font_small, anchor="w")
        self.meta_label.grid(row=1, column=1, sticky="nw", padx=(12, 8), pady=(3, 0))
        self.status_chip = tk.Label(
            inner, text="●  在线", bg=ACCENT_SOFT, fg=ACCENT,
            font=self.font_small, padx=10, pady=4,
            highlightbackground="#2A5A44", highlightthickness=1,
        )
        self.status_chip.grid(row=0, column=2, rowspan=2, sticky="ne")

        self.motd_text = tk.Text(
            inner, bg=SURFACE, fg=TEXT, font=self.font_body,
            relief="flat", bd=0, highlightthickness=0, wrap="word",
            height=3, cursor="arrow", padx=0, pady=0, takefocus=0,
        )
        self.motd_text.grid(row=2, column=0, columnspan=3, sticky="ew", pady=(16, 0))
        self.motd_text.configure(state="disabled")

        separator = tk.Frame(inner, bg=LINE, height=1)
        separator.grid(row=3, column=0, columnspan=3, sticky="ew", pady=(14, 12))

        stats = tk.Frame(inner, bg=SURFACE)
        stats.grid(row=4, column=0, columnspan=3, sticky="ew")
        for index in range(3):
            stats.grid_columnconfigure(index, weight=1, uniform="stat")
        self.online_value, self.max_value = self._stat_cell(
            stats, 0, "在线玩家", "0", "/ 0")
        self.latency_value, _ = self._stat_cell(stats, 1, "响应", "—", "")
        self.version_value, _ = self._stat_cell(stats, 2, "版本", "—", "")
        self.version_value.configure(font=self.font_section, wraplength=110,
                                     justify="left", anchor="w")

        self.capacity_canvas = tk.Canvas(inner, height=8, bg=SURFACE,
                                         highlightthickness=0, bd=0)
        self.capacity_canvas.grid(row=5, column=0, columnspan=3, sticky="ew",
                                  pady=(12, 0))
        self.capacity_canvas.bind("<Configure>", lambda _e: self._draw_capacity())

        capacity_row = tk.Frame(inner, bg=SURFACE)
        capacity_row.grid(row=6, column=0, columnspan=3, sticky="ew", pady=(5, 0))
        capacity_row.grid_columnconfigure(0, weight=1)
        self._label(capacity_row, "服务器容量", fg=SOFT, font=self.font_tiny).grid(
            row=0, column=0, sticky="w")
        self.capacity_label = self._label(capacity_row, "—", fg=MUTED,
                                          font=self.font_tiny)
        self.capacity_label.grid(row=0, column=1, sticky="e")

        self.detail_label = self._label(inner, "", fg=MUTED, font=self.font_small,
                                        wraplength=400)
        self.detail_label.grid(row=7, column=0, columnspan=3, sticky="w", pady=(12, 0))

        self.players_outer = tk.Frame(inner, bg=SURFACE)
        self.players_outer.grid(row=8, column=0, columnspan=3, sticky="ew",
                                pady=(14, 0))
        self.players_outer.grid_columnconfigure(0, weight=1)
        players_head = tk.Frame(self.players_outer, bg=SURFACE)
        players_head.grid(row=0, column=0, sticky="ew")
        players_head.grid_columnconfigure(0, weight=1)
        self._label(players_head, "玩家样本", fg=MUTED, font=self.font_small).grid(
            row=0, column=0, sticky="w")
        self.player_count_label = self._label(players_head, "", fg=SOFT,
                                              font=self.font_tiny)
        self.player_count_label.grid(row=0, column=1, sticky="e")
        self.players_text = tk.Text(
            self.players_outer, bg=SURFACE, fg=TEXT, font=self.font_small,
            relief="flat", bd=0, highlightthickness=0, wrap="word",
            height=1, cursor="arrow", padx=0, pady=4, takefocus=0,
        )
        self.players_text.grid(row=1, column=0, sticky="ew", pady=(2, 0))
        self.players_text.configure(state="disabled")

        actions = tk.Frame(inner, bg=SURFACE)
        actions.grid(row=9, column=0, columnspan=3, sticky="ew", pady=(16, 0))
        actions.grid_columnconfigure(0, weight=1)
        actions.grid_columnconfigure(1, weight=1)
        self.requery_button = self._button(actions, "重新探测",
                                           self.start_query, fg=MUTED, bg=PANEL,
                                           padx=10, pady=8)
        self.requery_button.grid(row=0, column=0, sticky="ew", padx=(0, 5))
        self.copy_button = self._button(actions, "复制状态", self.copy_status,
                                        fg=MUTED, bg=PANEL, padx=10, pady=8)
        self.copy_button.grid(row=0, column=1, sticky="ew", padx=(5, 0))

        self.result_outer.grid_remove()
        self._capacity_percent = 0

    def _stat_cell(self, parent, column, title, value, suffix):
        cell = tk.Frame(parent, bg=PANEL, highlightbackground="#1D3346",
                        highlightthickness=1)
        cell.grid(row=0, column=column, sticky="nsew",
                  padx=(0 if column == 0 else 5, 0 if column == 2 else 5))
        cell.grid_columnconfigure(0, weight=1)
        self._label(cell, title, fg=SOFT, font=self.font_tiny).grid(
            row=0, column=0, sticky="w", padx=10, pady=(8, 2))
        row = tk.Frame(cell, bg=PANEL)
        row.grid(row=1, column=0, sticky="w", padx=10, pady=(0, 8))
        value_label = self._label(row, value, fg=ACCENT, font=self.font_stat)
        value_label.pack(side="left")
        suffix_label = self._label(row, suffix, fg=SOFT, font=self.font_tiny)
        suffix_label.pack(side="left", padx=(3, 0), pady=(4, 0))
        return value_label, suffix_label

    def _hide_result(self):
        self.result_outer.grid_remove()
        self.last_status = None
        self.last_address = None

    def _reveal_result(self):
        self.result_outer.grid()

    def _set_result_accent(self, edition: str):
        if edition == "bedrock":
            accent, soft, chip_bg = BEDROCK, BEDROCK_SOFT, BEDROCK_SOFT
            chip_line = "#2B647E"
        else:
            accent, soft, chip_bg = ACCENT, ACCENT_SOFT, ACCENT_SOFT
            chip_line = "#2A5A44"
        self.online_value.configure(fg=accent)
        self.status_chip.configure(bg=chip_bg, fg=accent,
                                   highlightbackground=chip_line)
        self._accent = accent
        self._draw_capacity()

    def _draw_icon(self, status: core.ServerStatus):
        self.icon_canvas.delete("all")
        self.icon_canvas.configure(bg=SURFACE)
        self.icon_canvas.create_rectangle(0, 0, self.ICON_SIZE, self.ICON_SIZE,
                                          fill=PANEL, outline=LINE)
        photo = None
        if status.favicon.startswith("data:image/png;base64,"):
            try:
                payload = status.favicon.split(",", 1)[1]
                raw = base64.b64decode(payload)
                photo = tk.PhotoImage(data=raw)
                while photo.width() > self.ICON_SIZE or photo.height() > self.ICON_SIZE:
                    photo = photo.subsample(2, 2)
                self.icon_photo = photo
            except Exception:
                photo = None
        if photo is not None:
            self.icon_canvas.create_image(self.ICON_SIZE / 2, self.ICON_SIZE / 2,
                                          image=photo)
        else:
            self._draw_mini_cube(self.icon_canvas, self.ICON_SIZE,
                                 BEDROCK if status.edition == "bedrock" else ACCENT)

    def _draw_mini_cube(self, canvas, size, color):
        canvas.delete("all")
        canvas.create_rectangle(1, 1, size - 1, size - 1,
                                fill=PANEL, outline=LINE)
        cx, cy = size / 2, size / 2
        s = size * 0.22
        top = [(cx, cy - s * 1.2), (cx + s * 1.6, cy - s * 0.35),
               (cx, cy + s * 0.5), (cx - s * 1.6, cy - s * 0.35)]
        left = [(cx - s * 1.6, cy - s * 0.35), (cx, cy + s * 0.5),
                (cx, cy + s * 1.7), (cx - s * 1.6, cy + s * 0.9)]
        right = [(cx + s * 1.6, cy - s * 0.35), (cx + s * 1.6, cy + s * 0.9),
                 (cx, cy + s * 1.7), (cx, cy + s * 0.5)]
        canvas.create_polygon(top, fill=color, outline="")
        canvas.create_polygon(left, fill=self._shade(color, 0.72), outline="")
        canvas.create_polygon(right, fill=self._shade(color, 0.45), outline="")

    @staticmethod
    def _shade(hex_color: str, factor: float) -> str:
        try:
            r = int(hex_color[1:3], 16)
            g = int(hex_color[3:5], 16)
            b = int(hex_color[5:7], 16)
            return f"#{int(r * factor):02X}{int(g * factor):02X}{int(b * factor):02X}"
        except Exception:
            return hex_color

    def _draw_capacity(self):
        canvas = self.capacity_canvas
        canvas.delete("all")
        width = max(canvas.winfo_width(), 10)
        canvas.create_rectangle(0, 2, width, 7, fill="#1C3448", outline="")
        fill_width = int(width * max(0, min(100, self._capacity_percent)) / 100)
        color = getattr(self, "_accent", ACCENT)
        if fill_width > 0:
            canvas.create_rectangle(0, 2, fill_width, 7, fill=color, outline="")

    def _display_status(self, status: core.ServerStatus):
        self.last_status = status
        edition_name = "Bedrock Edition" if status.edition == "bedrock" else "Java Edition"
        self._set_result_accent(status.edition)
        self.host_label.configure(text=status.host)
        meta = f"{edition_name}  ·  {status.host}:{status.port}"
        if status.via_srv:
            meta += "  ·  SRV"
        self.meta_label.configure(text=meta)
        self._draw_icon(status)
        self._fill_motd(status)
        self.online_value.configure(text=_format_count(status.online))
        self.max_value.configure(text=f"/ {_format_count(status.max_players)}"
                                 if status.max_players > 0 else "/ —")
        self.latency_value.configure(text=f"{status.latency_ms} ms")
        self.version_value.configure(text=status.version_name)
        if status.max_players > 0:
            self._capacity_percent = min(100, round(status.online * 100
                                                    / status.max_players))
            self.capacity_label.configure(
                text=f"{status.online} / {status.max_players}  ·  {self._capacity_percent}%")
        else:
            self._capacity_percent = 0
            self.capacity_label.configure(text="未公开")
        self._draw_capacity()
        self.detail_label.configure(text=self._details_text(status))
        self._fill_players(status)
        self.status_chip.configure(text="●  在线")
        self._hide_error()
        self._hide_loading()
        self._reveal_result()
        self.root.update_idletasks()
        bbox = self.canvas.bbox("all")
        if bbox:
            target = max(0, self.result_outer.winfo_y() - 10)
            span = max(1, bbox[3] - bbox[1])
            self.canvas.yview_moveto(min(1.0, target / span))

    def _fill_motd(self, status: core.ServerStatus):
        if status.edition == "bedrock":
            raw = status.motd_plain
            if status.sub_motd.strip():
                raw = raw + "\n" + status.sub_motd
            segments = core.motd_segments(raw)
        else:
            segments = status.motd_segments
        self.motd_text.configure(state="normal", height=self._motd_height(segments))
        self.motd_text.delete("1.0", "end")
        self._insert_segments(self.motd_text, segments, self.font_body)
        self.motd_text.configure(state="disabled")

    def _motd_height(self, segments: List[dict]) -> int:
        text = "".join(segment["text"] for segment in segments)
        lines = 1 + text.count("\n")
        lines += max(0, len(text) // 30 - 1)
        return max(1, min(7, lines))

    def _insert_segments(self, widget: tk.Text, segments: List[dict], base_font):
        for index, segment in enumerate(segments):
            text = segment["text"]
            if not text:
                continue
            tag = f"seg{index}"
            color = segment.get("color") or TEXT
            weight = "bold" if segment.get("bold") else "normal"
            slant = "italic" if segment.get("italic") else "roman"
            underline = bool(segment.get("underline"))
            overstrike = bool(segment.get("strike"))
            try:
                style_font = tkfont.Font(family=base_font.cget("family"),
                                         size=base_font.cget("size"),
                                         weight=weight, slant=slant,
                                         underline=underline, overstrike=overstrike)
            except Exception:
                style_font = base_font
            self._segment_fonts.append(style_font)
            widget.tag_configure(tag, foreground=color, font=style_font)
            widget.insert("end", text, tag)

    def _fill_players(self, status: core.ServerStatus):
        if status.edition == "bedrock" or not status.sample:
            self.players_outer.grid_remove()
            return
        self.players_outer.grid()
        self.player_count_label.configure(text=f"{status.online} 位玩家在线")
        self.players_text.configure(state="normal")
        self.players_text.delete("1.0", "end")
        names = status.sample[:24]
        text = "   ".join(names)
        segments = core.motd_segments(text)
        lines = max(1, min(3, 1 + len(text) // 34))
        self.players_text.configure(height=lines)
        self._insert_segments(self.players_text, segments, self.font_small)
        self.players_text.configure(state="disabled")

    def _details_text(self, status: core.ServerStatus) -> str:
        parts: List[str] = []
        if status.edition == "java":
            parts.append("Java Edition")
            if status.protocol > 0:
                parts.append(f"协议 {status.protocol}")
            software = _pretty_software(status.software)
            if software:
                parts.append(software)
            if status.secure_chat:
                parts.append("安全聊天")
            if status.legacy:
                parts.append("Legacy Ping")
            if status.via_srv:
                parts.append("SRV")
        else:
            parts.append("Bedrock Edition")
            if status.protocol > 0:
                parts.append(f"协议 {status.protocol}")
            if status.game_mode.strip():
                parts.append(_pretty_game_mode(status.game_mode))
            parts.append("UDP")
        return "   ·   ".join(parts)

    # ------------------------------------------------------------------ #
    # History                                                            #
    # ------------------------------------------------------------------ #

    def _build_history_card(self):
        card = self._card(5, pady=(0, 16))
        inner = tk.Frame(card, bg=SURFACE)
        inner.grid(row=0, column=0, sticky="ew", padx=18, pady=16)
        inner.grid_columnconfigure(0, weight=1)

        head = tk.Frame(inner, bg=SURFACE)
        head.grid(row=0, column=0, sticky="ew")
        head.grid_columnconfigure(0, weight=1)
        self._label(head, "最近查询", fg=TEXT, font=self.font_section).grid(
            row=0, column=0, sticky="w")
        self.clear_button = self._button(
            head, "清空", self._clear_history, fg=SOFT, bg=PANEL,
            font=self.font_small, padx=10, pady=3)
        self.clear_button.grid(row=0, column=1, sticky="e")
        self._label(head, "双击重新查询 · Delete 移除", fg=SOFT,
                    font=self.font_tiny).grid(row=1, column=0, sticky="w",
                                             pady=(3, 0))

        self.history_empty = self._label(
            inner, "还没有留下足迹  ·  查询成功的服务器会出现在这里",
            fg=SOFT, font=self.font_small)
        self.history_empty.grid(row=1, column=0, sticky="w", pady=(12, 2))

        self.history_list = tk.Listbox(
            inner, height=4, bg=INPUT, fg=TEXT, selectbackground=ACCENT_SOFT,
            selectforeground=ACCENT, font=self.font_mono, relief="flat",
            bd=0, highlightthickness=0, activestyle="none",
            exportselection=False,
        )
        self.history_list.grid(row=2, column=0, sticky="ew", pady=(10, 0))
        self.history_list.bind("<Double-Button-1>", lambda _e: self._history_query())
        self.history_list.bind("<Return>", lambda _e: self._history_query())
        self.history_list.bind("<Delete>", lambda _e: self._delete_history())

    def _load_history(self):
        try:
            data = json.loads(self.history_file.read_text(encoding="utf-8"))
            if isinstance(data, list):
                self.history = [item for item in data if isinstance(item, dict)][:8]
        except Exception:
            self.history = []

    def _save_history(self):
        try:
            self.config_dir.mkdir(parents=True, exist_ok=True)
            self.history_file.write_text(
                json.dumps(self.history, ensure_ascii=False, indent=2),
                encoding="utf-8")
        except Exception:
            pass

    def _render_history(self):
        self.history_list.delete(0, "end")
        for entry in self.history:
            mode = "B" if entry.get("mode") == "bedrock" else "J"
            host = entry.get("host", "")
            port = entry.get("port", 0)
            latency = entry.get("latency", 0)
            when = _relative_time(float(entry.get("ts") or 0))
            self.history_list.insert(
                "end", f"{mode}   {host}:{port}    {latency} ms    {when}")
        has_items = bool(self.history)
        self.history_empty.grid_remove() if has_items else self.history_empty.grid()
        self.history_list.grid() if has_items else self.history_list.grid_remove()
        self.clear_button.configure(state="normal" if has_items else "disabled")

    def _add_history(self, address: core.ServerAddress, status: core.ServerStatus):
        mode = status.edition
        self.history = [item for item in self.history
                        if not (item.get("mode") == mode
                                and str(item.get("host", "")).lower() == address.host.lower()
                                and int(item.get("port", 0)) == address.port)]
        self.history.insert(0, {
            "mode": mode,
            "host": address.host,
            "port": address.port,
            "explicit": address.explicit_port,
            "latency": status.latency_ms,
            "ts": time.time(),
        })
        self.history = self.history[:8]
        self._save_history()
        self._render_history()

    def _history_query(self):
        if self.busy:
            return
        selection = self.history_list.curselection()
        if not selection:
            return
        entry = self.history[selection[0]]
        self.mode = "bedrock" if entry.get("mode") == "bedrock" else "java"
        self._update_mode_styles()
        host = str(entry.get("host", ""))
        port = int(entry.get("port", 0))
        explicit = bool(entry.get("explicit"))
        value = host if not explicit else (
            f"[{host}]:{port}" if ":" in host else f"{host}:{port}")
        self.entry.delete(0, "end")
        self.entry.insert(0, value)
        self.start_query()

    def _delete_history(self):
        selection = self.history_list.curselection()
        if not selection:
            return
        del self.history[selection[0]]
        self._save_history()
        self._render_history()

    def _clear_history(self):
        if not self.history:
            return
        if messagebox.askyesno("清空最近查询", "只会删除本机的查询记录，不会影响服务器。",
                               parent=self.root):
            self.history = []
            self._save_history()
            self._render_history()

    # ------------------------------------------------------------------ #
    # Footer / state                                                     #
    # ------------------------------------------------------------------ #

    def _build_footer(self):
        footer = tk.Frame(self.content, bg=BG)
        footer.grid(row=6, column=0, sticky="ew", padx=22, pady=(0, 24))
        footer.grid_columnconfigure(0, weight=1)
        self._label(footer, "结果由目标服务器实时返回  ·  无需登录",
                    fg=SOFT, font=self.font_tiny).grid(row=0, column=0)
        self._label(footer, f"Java SLP  ·  Bedrock RakNet  ·  v{VERSION}",
                    fg="#4C6074", font=self.font_tiny).grid(row=1, column=0, pady=(4, 0))

    def _load_state(self):
        try:
            state = json.loads(self.state_file.read_text(encoding="utf-8"))
            self.mode = "bedrock" if state.get("mode") == "bedrock" else "java"
            value = str(state.get("input") or "")
            if value:
                self.entry.delete(0, "end")
                self.entry.insert(0, value)
        except Exception:
            pass
        self._update_mode_styles()

    def _save_state(self):
        try:
            self.config_dir.mkdir(parents=True, exist_ok=True)
            self.state_file.write_text(
                json.dumps({"mode": self.mode, "input": self.entry.get().strip()},
                           ensure_ascii=False, indent=2), encoding="utf-8")
        except Exception:
            pass

    # ------------------------------------------------------------------ #
    # Querying                                                           #
    # ------------------------------------------------------------------ #

    def start_query(self):
        if self.busy:
            return
        raw = self.entry.get().strip()
        try:
            address = core.parse_address(raw, self.mode)
        except core.QueryError as exc:
            self._hide_result()
            self._show_error(error_message(exc, self.mode)
                             if raw else "先告诉我服务器地址吧。")
            self.entry.focus_set()
            return
        self._save_state()
        self._hide_error()
        self._hide_result()
        self._show_loading(f"正在查询 {address.display()} …")
        self.busy = True
        self.generation += 1
        generation = self.generation
        self.query_button.configure(text="探测中…", state="disabled",
                                   bg="#2E7D5B", fg="#D7FFE9")
        self.requery_button.configure(state="disabled")
        self.entry.configure(state="disabled")
        self.paste_button.configure(state="disabled")
        mode = self.mode
        thread = threading.Thread(
            target=self._worker, args=(generation, address, mode), daemon=True)
        thread.start()
        self.root.after(80, self._poll_worker)

    def _worker(self, generation: int, address: core.ServerAddress, mode: str):
        try:
            if mode == "bedrock":
                status = core.query_bedrock(address)
            else:
                status = core.query_java(address)
            self.worker_queue.put((generation, "ok", status, address, mode))
        except core.QueryError as exc:
            self.worker_queue.put((generation, "error", exc, address, mode))
        except Exception as exc:
            wrapped = core.QueryError("protocol", str(exc), exc)
            self.worker_queue.put((generation, "error", wrapped, address, mode))

    def _poll_worker(self):
        if not self.busy:
            return
        try:
            generation, kind, payload, address, mode = self.worker_queue.get_nowait()
        except queue.Empty:
            self.root.after(100, self._poll_worker)
            return
        if generation != self.generation:
            return
        self._finish_query()
        if kind == "ok":
            status = payload
            self.last_address = address
            self._display_status(status)
            self._add_history(address, status)
        else:
            self._show_error(error_message(payload, mode))

    def _finish_query(self):
        self.busy = False
        self.query_button.configure(text="查询", state="normal",
                                    bg=ACCENT, fg=ACCENT_DARK)
        self.requery_button.configure(state="normal")
        self.entry.configure(state="normal")
        self.paste_button.configure(state="normal")
        self._hide_loading()

    # ------------------------------------------------------------------ #
    # Actions                                                            #
    # ------------------------------------------------------------------ #

    def copy_status(self):
        if not self.last_status:
            return
        status = self.last_status
        edition = "Bedrock Edition" if status.edition == "bedrock" else "Java Edition"
        lines = [
            f"{APP_NAME} · 服务器状态",
            f"{edition}  ·  {status.host}:{status.port}",
            "",
            status.motd_plain,
            "",
            f"在线玩家：{status.online}" + (f" / {status.max_players}"
                                          if status.max_players > 0 else ""),
            f"响应：{status.latency_ms} ms",
            f"版本：{status.version_name}",
        ]
        if status.protocol > 0:
            lines.append(f"协议：{status.protocol}")
        try:
            self.root.clipboard_clear()
            self.root.clipboard_append("\n".join(lines))
            original = self.copy_button.cget("text")
            self.copy_button.configure(text="已复制")
            self.root.after(1400, lambda: self.copy_button.configure(text=original)
                            if self.copy_button.winfo_exists() else None)
        except tk.TclError:
            pass


def main():
    try:
        if os.name == "nt":
            import ctypes
            ctypes.windll.shcore.SetProcessDpiAwareness(1)
    except Exception:
        pass
    root = tk.Tk()
    app = CubeProbeApp(root)
    root.bind("<Control-Return>", lambda _e: app.start_query())
    try:
        root.mainloop()
    except KeyboardInterrupt:
        root.destroy()


if __name__ == "__main__":
    main()
