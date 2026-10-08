"""Loopback-only, authenticated Windows actions; Python 3.11+, no dependencies."""
from __future__ import annotations

import argparse
import ctypes
from ctypes import wintypes
import hmac
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
import json
import os
from pathlib import Path
import re
import secrets
import socket
import subprocess
import threading
import time
from urllib.parse import urlsplit

KEYS = {**{chr(n): n for n in range(65, 91)}, **{str(n): n + 48 for n in range(10)},
        **{f"F{n}": 0x6F + n for n in range(1, 25)},
        "CTRL": 0x11, "ALT": 0x12, "SHIFT": 0x10, "WIN": 0x5B,
        "TAB": 0x09, "ENTER": 0x0D, "ESC": 0x1B, "SPACE": 0x20,
        "LEFT": 0x25, "UP": 0x26, "RIGHT": 0x27, "DOWN": 0x28,
        "HOME": 0x24, "END": 0x23, "DELETE": 0x2E, "BACKSPACE": 0x08}
MEDIA = {"MUTE": 0xAD, "VOLUME_DOWN": 0xAE, "VOLUME_UP": 0xAF,
         "NEXT": 0xB0, "PREVIOUS": 0xB1, "STOP": 0xB2, "PLAY_PAUSE": 0xB3}
TONES = {"blue", "violet", "green", "slate", "red"}
BUILTINS = {"notepad.exe", "calc.exe", "explorer.exe", "mspaint.exe"}


def load_config(path):
    data = json.loads(Path(path).read_text(encoding="utf-8"))
    actions = {}
    for action in data["actions"]:
        action_id = action["id"]
        if not re.fullmatch(r"[a-z0-9_-]{1,48}", action_id) or action_id in actions:
            raise ValueError("Invalid or duplicate action ID")
        if not isinstance(action["label"], str) or not 1 <= len(action["label"]) <= 40:
            raise ValueError("Invalid label")
        if action.get("tone", "slate") not in TONES:
            raise ValueError("Invalid button tone")
        kind = action["type"]
        if kind == "hotkey":
            keys = action["keys"]
            if not isinstance(keys, list) or not 1 <= len(keys) <= 5 or any(k not in KEYS for k in keys) or len(keys) != len(set(keys)):
                raise ValueError("Invalid shortcut")
        elif kind == "media":
            if action["key"] not in MEDIA:
                raise ValueError("Invalid media key")
        elif kind == "launch":
            exe = action["executable"]
            if not isinstance(exe, str) or (exe not in BUILTINS and not Path(exe).is_absolute()):
                raise ValueError("Use a built-in executable or an absolute application path")
            args = action.get("args", [])
            if not isinstance(args, list) or any(not isinstance(a, str) for a in args):
                raise ValueError("Application args must be a string list")
        elif kind == "url":
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
        if not 1 <= len(profile["actions"]) <= 12 or any(a not in actions for a in profile["actions"]):
            raise ValueError("Profile must reference 1 to 12 known actions")
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

    def execute(self, action):
        if self.dry_run:
            return {"ok": True, "preview": True, "label": action["label"]}
        kind = action["type"]
        if kind == "launch":
            exe = action["executable"]
            if exe in BUILTINS:
                exe = str(Path(os.environ["SystemRoot"]) / "System32" / exe)
            subprocess.Popen([exe, *action.get("args", [])], shell=False,
                             stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
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
        extended = {0x5B, 0x25, 0x26, 0x27, 0x28, 0x24, 0x23, 0x2E, *MEDIA.values()}
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
    def __init__(self, config, token, pin, executor):
        self.config, self.token, self.pin, self.executor = config, token, pin, executor
        self.pin_deadline = time.monotonic() + 300
        self.lock = threading.Lock()
        self.recent = {}
        self.pair_attempts = []
        self.action_times = []

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
        if not isinstance(action_id, str) or action_id not in self.config["actions"]:
            return 404, {"error": "등록되지 않은 버튼입니다."}
        if not isinstance(request_id, str) or not re.fullmatch(r"[A-Za-z0-9_-]{8,64}", request_id):
            return 400, {"error": "잘못된 요청 번호입니다."}
        with self.lock:
            now = time.monotonic()
            self.recent = {k: v for k, v in self.recent.items() if v[0] > now - 60}
            if request_id in self.recent:
                old = self.recent[request_id]
                return (old[2], old[3]) if old[1] == action_id else (409, {"error": "요청 번호가 충돌했습니다."})
            self.action_times = [t for t in self.action_times if t > now - 1]
            if len(self.action_times) >= 20 or len(self.recent) >= 512:
                return 429, {"error": "버튼을 잠시 후 다시 눌러 주세요."}
            self.action_times.append(now)
            try:
                result, status = self.executor.execute(self.config["actions"][action_id]), 200
            except Exception:
                result, status = {"error": "PC에서 실행하지 못했습니다. 앱 경로와 Windows 권한을 확인하세요."}, 500
            self.recent[request_id] = (now, action_id, status, result)
            return status, result


class FrontDeckServer(ThreadingHTTPServer):
    daemon_threads = True
    allow_reuse_address = False
    def __init__(self, address, controller):
        self.controller = controller
        super().__init__(address, Handler, bind_and_activate=False)
        try:
            if hasattr(socket, "SO_EXCLUSIVEADDRUSE"):
                self.socket.setsockopt(socket.SOL_SOCKET, socket.SO_EXCLUSIVEADDRUSE, 1)
            self.server_bind()
            self.server_activate()
        except Exception:
            self.server_close()
            raise


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
        self.end_headers()
        self.wfile.write(payload)

    def valid_host(self):
        expected = {f"127.0.0.1:{self.server.server_port}", f"localhost:{self.server.server_port}"}
        return self.headers.get("Host") in expected

    def authorized(self):
        value = self.headers.get("Authorization", "")
        return hmac.compare_digest(value.encode(), ("Bearer " + self.server.controller.token).encode())

    def do_GET(self):
        if not self.valid_host():
            return self.reply(403, {"error": "Invalid host"})
        if self.path == "/api/health":
            return self.reply(200, {"ok": True, "app": "FrontDeck", "dry_run": self.server.controller.executor.dry_run})
        if self.path == "/api/config":
            if not self.authorized():
                return self.reply(401, {"error": "PC 연결이 필요합니다."})
            return self.reply(200, public_config(self.server.controller.config))
        if self.path == "/":
            self.send_response(302)
            self.send_header("Location", "/preview/")
            self.send_header("Content-Length", "0")
            self.end_headers()
            return
        assets = {"/preview/": "index.html", "/preview/index.html": "index.html",
                  "/preview/style.css": "style.css", "/preview/app.js": "app.js", "/preview/icons.js": "icons.js",
                  "/preview/demo.json": "demo.json"}
        name = assets.get(self.path)
        if name:
            path = Path(__file__).parent / "ui" / name
            if name == "demo.json":
                return self.reply(200, public_config(self.server.controller.config))
            mime = "text/css" if name.endswith(".css") else "text/javascript" if name.endswith(".js") else "text/html"
            return self.reply(200, path.read_bytes(), mime + "; charset=utf-8")
        return self.reply(404, {"error": "Not found"})

    def do_POST(self):
        if not self.valid_host() or self.headers.get("Origin") is not None:
            return self.reply(403, {"error": "Native paired client required"})
        if self.path not in {"/api/pair", "/api/action"}:
            return self.reply(404, {"error": "Not found"})
        if self.path == "/api/action" and not self.authorized():
            return self.reply(401, {"error": "PC 연결이 필요합니다."})
        if self.headers.get("Content-Type", "").split(";")[0].strip() != "application/json" or self.headers.get("Transfer-Encoding"):
            return self.reply(400, {"error": "JSON request required"})
        try:
            length = int(self.headers.get("Content-Length", "0"))
            if not 0 < length <= 2048:
                return self.reply(413, {"error": "Request too large"})
            self.connection.settimeout(3)
            body = json.loads(self.rfile.read(length))
        except (ValueError, TimeoutError, OSError):
            return self.reply(400, {"error": "Invalid JSON"})
        if self.path == "/api/pair":
            if not isinstance(body, dict) or set(body) != {"pin"}:
                return self.reply(400, {"error": "Invalid pairing request"})
            status, result = self.server.controller.pair(body["pin"])
        else:
            status, result = self.server.controller.action(body)
        return self.reply(status, result)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--config", type=Path, default=Path(__file__).with_name("config.example.json"))
    parser.add_argument("--state-dir", type=Path, default=Path(os.environ.get("LOCALAPPDATA", Path.home() / ".local/share")) / "FrontDeck")
    parser.add_argument("--port", type=int, default=38765)
    parser.add_argument("--dry-run", action="store_true")
    args = parser.parse_args()
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
    controller = Controller(config, token, pin, WindowsExecutor(args.dry_run))
    server = FrontDeckServer(("127.0.0.1", args.port), controller)
    (args.state_dir / "bootstrap.json").write_text(json.dumps({"pin": pin, "port": args.port, "expires_at": time.time() + 300, "pid": os.getpid()}), encoding="utf-8")
    print(f"FrontDeck · http://127.0.0.1:{args.port}/preview/ · 연결 코드: {pin} (5분)", flush=True)
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        pass
    finally:
        server.server_close()


if __name__ == "__main__":
    main()
