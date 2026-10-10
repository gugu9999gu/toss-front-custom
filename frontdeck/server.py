"""Authenticated Windows actions over USB loopback or optional pinned LAN TLS."""
from __future__ import annotations

import argparse
import ctypes
from ctypes import wintypes
import hmac
import html
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
import json
import os
from pathlib import Path
import re
import secrets
import socket
import subprocess
import sys
import tempfile
import threading
import time
from urllib.parse import urlsplit, parse_qs

if __package__ in {None, ""}:
    sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from frontdeck.desktop import Desktop, valid_app_id

KEYS = {**{chr(n): n for n in range(65, 91)}, **{str(n): n + 48 for n in range(10)},
        **{f"F{n}": 0x6F + n for n in range(1, 25)},
        "CTRL": 0x11, "ALT": 0x12, "SHIFT": 0x10, "WIN": 0x5B,
        "TAB": 0x09, "ENTER": 0x0D, "ESC": 0x1B, "SPACE": 0x20,
        "LEFT": 0x25, "UP": 0x26, "RIGHT": 0x27, "DOWN": 0x28,
        "HOME": 0x24, "END": 0x23, "DELETE": 0x2E, "BACKSPACE": 0x08,
        "PAGEUP": 0x21, "PAGEDOWN": 0x22, "INSERT": 0x2D, "CAPS": 0x14, "HANGUL": 0x15}
MEDIA = {"MUTE": 0xAD, "VOLUME_DOWN": 0xAE, "VOLUME_UP": 0xAF,
         "NEXT": 0xB0, "PREVIOUS": 0xB1, "STOP": 0xB2, "PLAY_PAUSE": 0xB3}
TONES = {"blue", "violet", "green", "slate", "red"}
BUILTINS = {"notepad.exe", "calc.exe", "explorer.exe", "mspaint.exe"}


def load_config(path):
    return validate_config(json.loads(Path(path).read_text(encoding="utf-8")))


def validate_config(data):
    if not isinstance(data, dict) or not isinstance(data.get("actions"), list) or len(data["actions"]) > 256:
        raise ValueError("버튼은 최대 256개까지 등록할 수 있습니다.")
    actions = {}
    for action in data["actions"]:
        if not isinstance(action, dict):
            raise ValueError("Invalid action")
        action_id = action["id"]
        if not isinstance(action_id, str) or not re.fullmatch(r"[a-z0-9_-]{1,48}", action_id) or action_id in actions:
            raise ValueError("Invalid or duplicate action ID")
        if not isinstance(action["label"], str) or not 1 <= len(action["label"]) <= 40:
            raise ValueError("Invalid label")
        if not isinstance(action.get("tone", "slate"), str) or action.get("tone", "slate") not in TONES:
            raise ValueError("Invalid button tone")
        if not isinstance(action.get("icon", "plus"), str) or not re.fullmatch(r"[a-z-]{1,32}", action.get("icon", "plus")):
            raise ValueError("Invalid icon")
        if "focus_existing" in action and not isinstance(action["focus_existing"], bool):
            raise ValueError("Invalid focus option")
        kind = action["type"]
        if kind == "hotkey":
            keys = action["keys"]
            if not isinstance(keys, list) or not 1 <= len(keys) <= 5 or any(not isinstance(k, str) or k not in KEYS for k in keys) or len(keys) != len(set(keys)):
                raise ValueError("Invalid shortcut")
        elif kind == "media":
            if not isinstance(action["key"], str) or action["key"] not in MEDIA:
                raise ValueError("Invalid media key")
        elif kind == "launch":
            exe = action["executable"]
            if not isinstance(exe, str) or "\x00" in exe or (exe not in BUILTINS and (not Path(exe).is_absolute() or Path(exe).suffix.lower() != ".exe")):
                raise ValueError("Use a built-in executable or an absolute application path")
            args = action.get("args", [])
            if not isinstance(args, list) or len(args) > 32 or any(not isinstance(a, str) or len(a) > 2048 or "\x00" in a for a in args):
                raise ValueError("Application args must be a string list")
        elif kind == "app":
            if not valid_app_id(action.get("app_id")):
                raise ValueError("Invalid installed application")
            exe = action.get("executable", "")
            if exe and (not isinstance(exe, str) or not Path(exe).is_absolute() or Path(exe).suffix.lower() != ".exe" or "\x00" in exe):
                raise ValueError("Invalid application path")
        elif kind == "url":
            if not isinstance(action["url"], str) or len(action["url"]) > 2048 or any(c in action["url"] for c in ("\x00", "\r", "\n")):
                raise ValueError("Invalid website URL")
            parsed = urlsplit(action["url"])
            if parsed.scheme not in {"https", "http"} or not parsed.hostname or parsed.username or parsed.password:
                raise ValueError("Invalid website URL")
        else:
            raise ValueError("Unsupported action type")
        actions[action_id] = action
    profiles = data["profiles"]
    if not isinstance(profiles, list) or not 1 <= len(profiles) <= 6:
        raise ValueError("One to six profiles required")
    profile_ids = set()
    for profile in profiles:
        if profile["id"] in profile_ids or not re.fullmatch(r"[a-z0-9_-]{1,32}", profile["id"]):
            raise ValueError("Invalid profile ID")
        profile_ids.add(profile["id"])
        if not isinstance(profile["name"], str) or not 1 <= len(profile["name"]) <= 24:
            raise ValueError("Invalid profile name")
        if not isinstance(profile["actions"], list) or len(profile["actions"]) > 48 or any(not isinstance(a, str) or a not in actions for a in profile["actions"]) or len(set(profile["actions"])) != len(profile["actions"]):
            raise ValueError("버튼 모음에는 최대 48개의 서로 다른 버튼을 등록할 수 있습니다.")
    name = data.get("name", "내 PC")
    if not isinstance(name, str) or len(name) > 40:
        raise ValueError("Invalid PC name")
    return {"name": name, "profiles": profiles, "actions": actions}


def public_config(config):
    return {"name": config["name"], "profiles": [{"id": p["id"], "name": p["name"],
        "actions": [{k: config["actions"][a].get(k, "slate" if k == "tone" else "")
                    for k in ("id", "label", "icon", "tone")} for a in p["actions"]]}
        for p in config["profiles"]]}


class WindowsExecutor:
    def __init__(self, dry_run=False):
        self.dry_run = dry_run
        if not dry_run and os.name != "nt":
            raise RuntimeError("PC actions require Windows; use --dry-run for a preview")
        self.desktop = None if dry_run else Desktop()

    def execute(self, action):
        if self.dry_run:
            return {"ok": True, "preview": True, "label": action["label"]}
        kind = action["type"]
        if kind in {"launch", "app"}:
            return {**self.desktop.launch(action), "label": action["label"]}
        elif kind == "url":
            os.startfile(action["url"])
        else:
            self.send_keys([MEDIA[action["key"]]] if kind == "media" else [KEYS[k] for k in action["keys"]])
        return {"ok": True, "label": action["label"]}

    @staticmethod
    def send_keys(keys, user32=None):
        class KEYBDINPUT(ctypes.Structure):
            _fields_ = [("wVk", wintypes.WORD), ("wScan", wintypes.WORD), ("dwFlags", wintypes.DWORD),
                        ("time", wintypes.DWORD), ("dwExtraInfo", wintypes.WPARAM)]
        class MOUSEINPUT(ctypes.Structure):
            _fields_ = [("dx", wintypes.LONG), ("dy", wintypes.LONG), ("mouseData", wintypes.DWORD),
                        ("dwFlags", wintypes.DWORD), ("time", wintypes.DWORD), ("dwExtraInfo", wintypes.WPARAM)]
        class HARDWAREINPUT(ctypes.Structure):
            _fields_ = [("uMsg", wintypes.DWORD), ("wParamL", wintypes.WORD), ("wParamH", wintypes.WORD)]
        class UNION(ctypes.Union):
            _fields_ = [("ki", KEYBDINPUT), ("mi", MOUSEINPUT), ("hi", HARDWAREINPUT)]
        class INPUT(ctypes.Structure):
            _anonymous_ = ("u",)
            _fields_ = [("type", wintypes.DWORD), ("u", UNION)]
        user32 = user32 or ctypes.WinDLL("user32", use_last_error=True)
        user32.SendInput.argtypes = [wintypes.UINT, ctypes.POINTER(INPUT), ctypes.c_int]
        user32.SendInput.restype = wintypes.UINT
        user32.GetAsyncKeyState.argtypes = [ctypes.c_int]
        user32.GetAsyncKeyState.restype = ctypes.c_short
        owned = [key for key in keys if not user32.GetAsyncKeyState(key) & 0x8000]
        extended = {0x5B, 0x21, 0x22, 0x25, 0x26, 0x27, 0x28, 0x24, 0x23, 0x2D, 0x2E, *MEDIA.values()}
        def event(key, released):
            item = INPUT(type=1)
            item.ki = KEYBDINPUT(key, 0, (1 if key in extended else 0) | (2 if released else 0), 0, 0)
            return item
        pressed = []
        try:
            for key in owned:
                item = event(key, False)
                if user32.SendInput(1, ctypes.byref(item), ctypes.sizeof(INPUT)) != 1:
                    raise RuntimeError("Windows could not send the shortcut")
                pressed.append(key)
        finally:
            failed_release = False
            for key in reversed(pressed):
                item = event(key, True)
                if user32.SendInput(1, ctypes.byref(item), ctypes.sizeof(INPUT)) != 1:
                    # Retry only releases; never repeat a failed action's key presses.
                    if user32.SendInput(1, ctypes.byref(item), ctypes.sizeof(INPUT)) != 1:
                        failed_release = True
            if failed_release:
                raise RuntimeError("Windows could not release a shortcut key")


class Controller:
    def __init__(self, config, token, pin, executor, config_path=None, desktop=None):
        self.config, self.token, self.pin, self.executor = config, token, pin, executor
        self.pin_deadline = time.monotonic() + 300
        self.lock = threading.Lock()
        self.recent = {}
        self.pair_attempts = []
        self.action_times = []
        self.config_path = Path(config_path) if config_path is not None else None
        self.desktop = desktop if desktop is not None else getattr(executor, "desktop", None)
        self.wireless = None
        self.lan_requests = 0
        self.lan_last_peer = None
        self.bootstrap_path = None
        self.setup_nonce = secrets.token_urlsafe(32)
        self.bluetooth = None
        from frontdeck.input_control import InputController, NativeInput
        self.input = InputController(NativeInput(KEYS, WindowsExecutor.send_keys, executor.dry_run))

    def note_wireless_request(self, peer):
        with self.lock:
            self.lan_requests += 1
            self.lan_last_peer = peer

    def renew_pairing(self):
        with self.lock:
            pin = ''.join(secrets.choice('0123456789') for _ in range(8))
            if self.bootstrap_path:
                saved = json.loads(self.bootstrap_path.read_text(encoding='utf-8'))
                saved.update(pin=pin, expires_at=time.time() + 300)
                self.bootstrap_path.write_text(json.dumps(saved), encoding='utf-8')
            self.pin = pin
            self.pin_deadline = time.monotonic() + 300
            self.pair_attempts = []

    def read(self, route):
        if route == "windows":
            return 200, self.desktop.public_windows() if self.desktop else {"windows": []}
        if route == "apps":
            return 200, self.desktop.catalog.snapshot() if self.desktop else {"apps": [], "loading": False}
        with self.lock:
            if route == "config":
                return 200, self.panel_config()
            if route == "editor":
                return 200, {"profiles": self.config["profiles"], "actions": self.config["actions"],
                             "writable": self.config_path is not None and not self.executor.dry_run}
        return 404, {"error": "Not found"}

    def panel_config(self):
        result = public_config(self.config)
        if self.desktop and hasattr(self.desktop, 'action_image'):
            for profile in result['profiles']:
                for action in profile['actions']:
                    action['image'] = self.desktop.action_image(self.config['actions'][action['id']])
        return result

    def persist(self, candidate):
        """Commit disk first, then memory; a failed write keeps the old configuration."""
        config = validate_config(candidate)
        parent = self.config_path.parent
        temp = None
        try:
            with tempfile.NamedTemporaryFile(mode="w", encoding="utf-8", dir=parent,
                                             prefix=self.config_path.name + ".", suffix=".tmp", delete=False) as file:
                temp = Path(file.name)
                json.dump(candidate, file, ensure_ascii=False, indent=2)
                file.write("\n"); file.flush(); os.fsync(file.fileno())
            os.replace(temp, self.config_path)
            self.config = config
        finally:
            if temp is not None and temp.exists():
                temp.unlink()

    def modify(self, route, body):
        if self.config_path is None or self.executor.dry_run:
            return 403, {"error": "이 연결에서는 버튼을 편집할 수 없습니다."}
        # Bodies are authenticated, but only typed actions are accepted; never shell commands.
        required = {"action", "profile_id", "request_id"} if route == "save" else {"id", "request_id"}
        if not isinstance(body, dict) or set(body) != required:
            return 400, {"error": "잘못된 편집 요청입니다."}
        def edit():
            candidate = {"name": self.config["name"], "profiles": json.loads(json.dumps(self.config["profiles"])),
                         "actions": [dict(a) for a in self.config["actions"].values()]}
            if route == "delete":
                identifier = body["id"]
                if not isinstance(identifier, str) or identifier not in self.config["actions"]:
                    return 404, {"error": "이미 삭제된 버튼입니다."}
                candidate["actions"] = [a for a in candidate["actions"] if a["id"] != identifier]
                for p in candidate["profiles"]:
                    p["actions"] = [a for a in p["actions"] if a != identifier]
            else:
                raw, profile_id = body["action"], body["profile_id"]
                if not isinstance(raw, dict) or not isinstance(profile_id, str):
                    return 400, {"error": "잘못된 버튼 설정입니다."}
                kind = raw.get("type")
                if not isinstance(kind, str):
                    return 400, {"error": "동작 종류를 선택하세요."}
                allowed = {"id", "label", "icon", "tone", "type"}
                allowed |= {"application": {"application_id", "focus_existing"}, "launch": {"executable", "args", "focus_existing"},
                            "hotkey": {"keys"}, "media": {"key"}, "url": {"url"}}.get(kind, set())
                if set(raw) - allowed or kind not in {"application", "launch", "hotkey", "media", "url"}:
                    return 400, {"error": "지원하지 않는 버튼 설정입니다."}
                action = dict(raw)
                identifier = action.get("id", "")
                if not isinstance(identifier, str) or (identifier and identifier not in self.config["actions"]):
                    return 404, {"error": "수정할 버튼을 찾을 수 없습니다."}
                action["id"] = identifier or "custom-" + secrets.token_hex(6)
                if kind == "application":
                    selected = action.pop("application_id", None)
                    app = self.desktop.catalog.resolve(selected) if isinstance(selected, str) and self.desktop else None
                    if app is None:
                        return 400, {"error": "앱 목록에서 실행할 프로그램을 선택하세요."}
                    if "app_id" in app:
                        action.update(type="app", app_id=app["app_id"], catalog_id=app["id"])
                        if "executable" in app:
                            action["executable"] = app["executable"]
                    else:
                        action.update(type="launch", executable=app["executable"])
                elif kind == "launch" and (not isinstance(action.get("executable"), str) or action.get("executable") not in BUILTINS):
                    exe = action.get("executable")
                    if not isinstance(exe, str) or not Path(exe).is_file() or Path(exe).suffix.lower() != ".exe":
                        return 400, {"error": "PC에 존재하는 .exe 프로그램의 전체 경로를 입력하세요."}
                profile = next((p for p in candidate["profiles"] if p["id"] == profile_id), None)
                if profile is None:
                    if profile_id != "custom" or len(candidate["profiles"]) >= 6:
                        return 400, {"error": "버튼 모음을 선택하세요."}
                    profile = {"id": "custom", "name": "내 버튼", "actions": []}
                    candidate["profiles"].append(profile)
                if action["id"] not in profile["actions"]:
                    profile["actions"].append(action["id"])
                candidate["actions"] = [a for a in candidate["actions"] if a["id"] != action["id"]] + [action]
            try:
                self.persist(candidate)
            except (ValueError, TypeError, KeyError):
                return 400, {"error": "설정값을 확인하세요. 버튼은 모음당 48개, 단축키는 최대 5개입니다."}
            except OSError:
                return 500, {"error": "PC에 버튼 설정을 저장하지 못했습니다."}
            return 200, {"ok": True, "config": self.panel_config()}
        return self.once(body["request_id"], route + ":" + json.dumps(body, sort_keys=True), edit)

    def focus(self, body):
        if not isinstance(body, dict) or set(body) != {"id", "request_id"} or not isinstance(body["id"], str):
            return 400, {"error": "잘못된 창 전환 요청입니다."}
        def activate():
            if not self.desktop or self.executor.dry_run:
                return 403, {"error": "실제 PC 연결에서만 창을 전환할 수 있습니다."}
            try:
                return 200, self.desktop.focus(body["id"])
            except LookupError:
                return 404, {"error": "이미 닫힌 창입니다. 목록을 새로 고쳐 주세요."}
            except Exception:
                return 500, {"error": "창을 앞으로 가져오지 못했습니다. PC의 열린 메뉴·관리자 창을 확인하세요."}
        return self.once(body["request_id"], "focus:" + body["id"], activate)

    def pair(self, pin):
        with self.lock:
            now = time.monotonic()
            self.pair_attempts = [t for t in self.pair_attempts if t > now - 60]
            if len(self.pair_attempts) >= 10:
                return 429, {"error": "연결 코드를 잠시 후 다시 입력하세요."}
            self.pair_attempts.append(now)
            if now > self.pin_deadline or not isinstance(pin, str) or not hmac.compare_digest(pin.encode(), self.pin.encode()):
                return 403, {"error": "연결 코드가 올바르지 않거나 만료됐습니다."}
            return 200, {"ok": True, "token": self.token}

    def action(self, body):
        if not isinstance(body, dict) or set(body) != {"id", "request_id"}:
            return 400, {"error": "잘못된 버튼 요청입니다."}
        action_id, request_id = body["id"], body["request_id"]
        if not isinstance(action_id, str):
            return 400, {"error": "잘못된 버튼 요청입니다."}
        def execute():
            if action_id not in self.config["actions"]:
                return 404, {"error": "등록되지 않은 버튼입니다."}
            try:
                return 200, self.executor.execute(self.config["actions"][action_id])
            except Exception:
                return 500, {"error": "PC에서 실행하지 못했습니다. 앱 경로와 Windows 권한을 확인하세요."}
        return self.once(request_id, "action:" + action_id, execute)

    def once(self, request_id, identity, callback):
        if not isinstance(request_id, str) or not re.fullmatch(r"[A-Za-z0-9_-]{8,64}", request_id):
            return 400, {"error": "잘못된 요청 번호입니다."}
        with self.lock:
            now = time.monotonic()
            self.recent = {k: v for k, v in self.recent.items() if v[0] > now - 60}
            if request_id in self.recent:
                old = self.recent[request_id]
                return (old[2], old[3]) if old[1] == identity else (409, {"error": "요청 번호가 충돌했습니다."})
            self.action_times = [t for t in self.action_times if t > now - 1]
            if len(self.action_times) >= 20 or len(self.recent) >= 512:
                return 429, {"error": "버튼을 잠시 후 다시 눌러 주세요."}
            self.action_times.append(now)
            status, result = callback()
            self.recent[request_id] = (now, identity, status, result)
            return status, result


class FrontDeckServer(ThreadingHTTPServer):
    daemon_threads = True
    allow_reuse_address = False
    def __init__(self, address, controller, tls_context=None):
        self.controller = controller
        self.tls_context = tls_context
        self.is_wireless = tls_context is not None
        super().__init__(address, Handler, bind_and_activate=False)
        try:
            if hasattr(socket, "SO_EXCLUSIVEADDRUSE"):
                self.socket.setsockopt(socket.SOL_SOCKET, socket.SO_EXCLUSIVEADDRUSE, 1)
            self.server_bind()
            self.server_activate()
        except Exception:
            self.server_close()
            raise

    def get_request(self):
        connection, address = super().get_request()
        connection.settimeout(5)
        if self.tls_context:
            try:
                connection = self.tls_context.wrap_socket(connection, server_side=True)
            except Exception:
                connection.close()
                raise
        return connection, address


class Handler(BaseHTTPRequestHandler):
    server_version = "FrontDeck/1"
    def log_message(self, *_):
        pass

    def reply(self, status, body, content_type="application/json; charset=utf-8"):
        payload = body if isinstance(body, bytes) else json.dumps(body, ensure_ascii=False).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", content_type)
        self.send_header("Content-Length", str(len(payload)))
        self.send_header("Cache-Control", "no-store")
        self.send_header("X-Content-Type-Options", "nosniff")
        self.send_header("X-Frame-Options", "DENY")
        self.send_header("Referrer-Policy", "no-referrer")
        self.end_headers()
        self.wfile.write(payload)

    def valid_host(self):
        expected = {f"{self.server.server_address[0]}:{self.server.server_port}"}
        if not self.server.is_wireless:
            expected.add(f"localhost:{self.server.server_port}")
        return self.headers.get("Host") in expected

    def authorized(self):
        value = self.headers.get("Authorization", "")
        valid = hmac.compare_digest(value.encode(), ("Bearer " + self.server.controller.token).encode())
        if valid and self.server.is_wireless:
            self.server.controller.note_wireless_request(self.client_address[0])
        return valid

    def connection_page(self):
        from frontdeck.wireless import confirmation_code
        c = self.server.controller
        wifi = ''
        if c.wireless:
            address = html.escape(c.wireless['host'])
            code = confirmation_code(c.wireless['fingerprint'])
            wifi = f'<section>Wi-Fi PC 주소<b>{address}</b>기기 확인 코드<b>{code}</b><p>같은 공유기에 연결하고 FrontDeck 설정 → Wi-Fi 연결에서 입력하세요.</p></section>'
        bluetooth = ''
        if c.bluetooth:
            name = html.escape(c.bluetooth.info['name'])
            bluetooth = f'<section>Bluetooth PC 이름<b>{name}</b><p>PC와 기기를 OS Bluetooth 설정에서 페어링한 뒤, FrontDeck 설정 → Bluetooth 연결에서 이 PC와 아래 연결 코드를 선택하세요.</p></section>'
        with c.lock:
            pin = c.pin if time.monotonic() < c.pin_deadline else '만료됨'
        body = f'''<!doctype html><html lang="ko"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>FrontDeck 무선 연결</title><style>body{{font:17px system-ui;background:#101217;color:#eef3f7;margin:0;padding:48px 24px}}main{{max-width:560px;margin:auto}}h1{{font-size:32px}}small,p{{color:#aab7c6;line-height:1.7}}section{{padding:22px;background:#1c222b;border-radius:18px;margin:16px 0}}b{{display:block;font-size:28px;letter-spacing:2px;margin:8px 0}}button{{font:inherit;border:0;border-radius:12px;background:#70dcb3;color:#09251b;padding:14px 20px;cursor:pointer}}</style>
<main><small>FRONTDECK</small><h1>PC 연결</h1>{wifi}{bluetooth}<section>연결 코드 · 5분<b>{pin}</b></section>
<form method="post" action="/connect/renew"><input type="hidden" name="nonce" value="{c.setup_nonce}"><button>새 연결 코드</button></form><p>한 번 연결하면 주소와 연결 정보가 기기에 저장됩니다. PC 프로그램과 기기의 네트워크 연결을 유지해 주세요.</p></main></html>'''
        return self.reply(200, body.encode('utf-8'), 'text/html; charset=utf-8')

    def do_GET(self):
        if not self.valid_host():
            return self.reply(403, {"error": "Invalid host"})
        if self.path == "/api/health":
            c = self.server.controller
            result = {"ok": True, "app": "FrontDeck", "dry_run": c.executor.dry_run, "wireless_enabled": c.wireless is not None}
            if not self.server.is_wireless:
                result.update(wireless=c.wireless, wireless_authenticated_requests=c.lan_requests, wireless_last_peer=c.lan_last_peer,
                              bluetooth_enabled=c.bluetooth is not None, bluetooth=c.bluetooth.info if c.bluetooth else None,
                              bluetooth_requests=c.bluetooth.requests if c.bluetooth else 0,
                              bluetooth_input_requests=c.bluetooth.input_requests if c.bluetooth else 0)
            return self.reply(200, result)
        if self.path in {"/api/config", "/api/windows", "/api/apps", "/api/editor"}:
            if self.headers.get("Origin") is not None:
                return self.reply(403, {"error": "Native paired client required"})
            if not self.authorized():
                return self.reply(401, {"error": "PC 연결이 필요합니다."})
            try:
                status, result = self.server.controller.read(self.path[5:])
            except Exception:
                status, result = 500, {"error": "PC 목록을 불러오지 못했습니다."}
            return self.reply(status, result)
        if self.server.is_wireless:
            return self.reply(404, {"error": "Not found"})
        if self.path == "/connect/":
            return self.connection_page()
        if self.path == "/":
            self.send_response(302)
            self.send_header("Location", "/preview/")
            self.send_header("Content-Length", "0")
            self.end_headers()
            return
        assets = {"/preview/": "index.html", "/preview/index.html": "index.html",
                  "/preview/style.css": "style.css", "/preview/app.js": "app.js", "/preview/icons.js": "icons.js",
                  "/preview/demo.json": "demo.json", "/preview/input.js": "input.js"}
        name = assets.get(self.path)
        if name:
            path = Path(__file__).parent / "ui" / name
            if name == "demo.json":
                return self.reply(200, public_config(self.server.controller.config))
            mime = "text/css" if name.endswith(".css") else "text/javascript" if name.endswith(".js") else "text/html"
            return self.reply(200, path.read_bytes(), mime + "; charset=utf-8")
        return self.reply(404, {"error": "Not found"})

    def do_POST(self):
        if self.path == '/connect/renew':
            if self.server.is_wireless or not self.valid_host() or self.headers.get('Origin') not in (None, 'http://' + self.headers.get('Host', '')):
                return self.reply(403, {'error': 'Local connection setup required'})
            try:
                size = int(self.headers.get('Content-Length', '0'))
                if not 0 < size <= 256 or self.headers.get('Transfer-Encoding'):
                    raise ValueError()
                body = parse_qs(self.rfile.read(size).decode('ascii'))
                if set(body) != {'nonce'} or len(body['nonce']) != 1 or not hmac.compare_digest(body['nonce'][0], self.server.controller.setup_nonce):
                    raise ValueError()
            except (ValueError, OSError):
                return self.reply(403, {'error': 'Invalid setup request'})
            self.server.controller.renew_pairing()
            self.send_response(303); self.send_header('Location', '/connect/'); self.send_header('Content-Length', '0'); self.end_headers()
            return
        if not self.valid_host() or self.headers.get("Origin") is not None:
            return self.reply(403, {"error": "Native paired client required"})
        if self.path not in {"/api/pair", "/api/action", "/api/focus", "/api/save", "/api/delete", "/api/input"}:
            return self.reply(404, {"error": "Not found"})
        if self.path != "/api/pair" and not self.authorized():
            return self.reply(401, {"error": "PC 연결이 필요합니다."})
        if self.headers.get("Content-Type", "").split(";")[0].strip() != "application/json" or self.headers.get("Transfer-Encoding"):
            return self.reply(400, {"error": "JSON request required"})
        try:
            length = int(self.headers.get("Content-Length", "0"))
            if not 0 < length <= (8192 if self.path in {"/api/save", "/api/input"} else 2048):
                return self.reply(413, {"error": "Request too large"})
            self.connection.settimeout(3)
            body = json.loads(self.rfile.read(length))
        except (ValueError, TimeoutError, OSError):
            return self.reply(400, {"error": "Invalid JSON"})
        if self.path == "/api/pair":
            if not isinstance(body, dict) or set(body) != {"pin"}:
                return self.reply(400, {"error": "Invalid pairing request"})
            status, result = self.server.controller.pair(body["pin"])
            if status == 200 and self.server.is_wireless:
                self.server.controller.note_wireless_request(self.client_address[0])
        elif self.path == "/api/action":
            status, result = self.server.controller.action(body)
        elif self.path == "/api/focus":
            status, result = self.server.controller.focus(body)
        elif self.path == "/api/input":
            status, result = self.server.controller.input.handle(body)
        else:
            status, result = self.server.controller.modify(self.path[5:], body)
        return self.reply(status, result)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--config", type=Path, default=Path(__file__).with_name("config.local.json"))
    parser.add_argument("--state-dir", type=Path, default=Path(os.environ.get("LOCALAPPDATA", Path.home() / ".local/share")) / "FrontDeck")
    parser.add_argument("--port", type=int, default=38765)
    parser.add_argument("--dry-run", action="store_true")
    parser.add_argument('--lan-host', help='사설 LAN IPv4 주소에 TLS 무선 연결을 추가합니다.')
    parser.add_argument('--lan-port', type=int, default=38766)
    parser.add_argument('--bluetooth', action='store_true', help='암호화된 Bluetooth RFCOMM 연결을 추가합니다.')
    args = parser.parse_args()
    if not args.config.exists() and args.config == Path(__file__).with_name("config.local.json"):
        args.config.write_bytes(Path(__file__).with_name("config.example.json").read_bytes())
    config = load_config(args.config)
    args.state_dir.mkdir(parents=True, exist_ok=True)
    state_path = args.state_dir / "credentials.json"
    if state_path.exists():
        token = json.loads(state_path.read_text(encoding="utf-8"))["token"]
        if not isinstance(token, str) or len(token) < 32:
            raise ValueError("Invalid saved credentials")
    else:
        token = secrets.token_urlsafe(32)
        state_path.write_text(json.dumps({"token": token}), encoding="utf-8")
        state_path.chmod(0o600)
    pin = "".join(secrets.choice("0123456789") for _ in range(8))
    controller = Controller(config, token, pin, WindowsExecutor(args.dry_run), args.config)
    server = FrontDeckServer(("127.0.0.1", args.port), controller)
    wireless_server = None
    try:
        if args.lan_host:
            from frontdeck.wireless import ensure_identity, lan_address
            host = lan_address(args.lan_host)
            if not 1024 <= args.lan_port <= 65535 or args.lan_port == args.port:
                raise ValueError('무선 포트는 USB 포트와 다른 1024–65535 범위여야 합니다.')
            context, fingerprint = ensure_identity(args.state_dir, host)
            wireless_server = FrontDeckServer((host, args.lan_port), controller, context)
            controller.wireless = {'host': host, 'port': args.lan_port, 'fingerprint': fingerprint}
        if args.bluetooth:
            from frontdeck.bluetooth import BluetoothServer
            controller.bluetooth = BluetoothServer(controller)
    except Exception:
        server.server_close()
        if wireless_server:
            wireless_server.server_close()
        if controller.bluetooth:
            controller.bluetooth.close()
        raise
    controller.bootstrap_path = args.state_dir / 'bootstrap.json'
    controller.bootstrap_path.write_text(json.dumps({"pin": pin, "port": args.port, "expires_at": time.time() + 300, "pid": os.getpid(), 'wireless': controller.wireless,
                                                    'bluetooth': controller.bluetooth.info if controller.bluetooth else None}), encoding="utf-8")
    if controller.bluetooth:
        controller.bluetooth.start()
    if wireless_server:
        threading.Thread(target=wireless_server.serve_forever, daemon=True).start()
    print(f"FrontDeck · http://127.0.0.1:{args.port}/preview/ · 연결 코드: {pin} (5분)", flush=True)
    if wireless_server:
        print(f'Wi-Fi 연결 설정: http://127.0.0.1:{args.port}/connect/', flush=True)
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        pass
    finally:
        server.server_close()
        if wireless_server:
            wireless_server.shutdown(); wireless_server.server_close()
        if controller.bluetooth:
            controller.bluetooth.close()


if __name__ == "__main__":
    main()
