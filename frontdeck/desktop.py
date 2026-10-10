"""Windows app inventory and taskbar; paths stay in the paired local controller."""
from __future__ import annotations

import ctypes
from ctypes import wintypes
import hashlib
import json
import os
from pathlib import Path
import subprocess
import threading
import time


def app_key(value):
    return hashlib.sha256(value.casefold().encode("utf-8")).hexdigest()[:24]


def builtin_path(executable):
    root = Path(os.environ["SystemRoot"])
    return root / executable if executable == "explorer.exe" else root / "System32" / executable


class AppCatalog:
    """Return immediately while Shell's installed-app inventory loads in the background."""
    def __init__(self):
        self.lock = threading.Lock()
        self.loading = False
        self.updated = 0
        self.error = False
        self.items = {"builtin-" + exe[:-4]: {"id": "builtin-" + exe[:-4], "name": label,
                      "executable": exe} for exe, label in
                      (("notepad.exe", "메모장"), ("calc.exe", "계산기"),
                       ("explorer.exe", "파일 탐색기"), ("mspaint.exe", "그림판"))}

    def snapshot(self):
        with self.lock:
            if os.name == "nt" and not self.loading and time.monotonic() - self.updated > 300:
                self.loading = True
                threading.Thread(target=self._load, daemon=True).start()
            return {"apps": [{"id": a["id"], "name": a["name"], "keywords": Path(a.get("executable", "")).stem} for a in
                             sorted(self.items.values(), key=lambda a: a["name"].casefold())],
                    "loading": self.loading, "unavailable": self.error}

    def resolve(self, identifier):
        with self.lock:
            value = self.items.get(identifier)
            return dict(value) if value else None

    def _load(self):
        try:
            result = subprocess.run(["powershell.exe", "-NoProfile", "-NonInteractive", "-File",
                                     str(Path(__file__).with_name("list_apps.ps1"))],
                                    capture_output=True, timeout=25, creationflags=subprocess.CREATE_NO_WINDOW)
            if result.returncode:
                raise RuntimeError("App inventory unavailable")
            rows = json.loads(result.stdout.decode("utf-8-sig"))
            found = {}
            for row in rows:
                name, aid = row.get("name", ""), row.get("app_id", "")
                if not isinstance(name, str) or not name.strip() or not valid_app_id(aid):
                    continue
                identifier = "app-" + app_key(aid)
                item = {"id": identifier, "name": name[:80], "app_id": aid}
                target = row.get("target", "")
                if isinstance(target, str) and Path(target).is_absolute() and Path(target).suffix.lower() == ".exe":
                    item["executable"] = target
                found[identifier] = item
            with self.lock:
                self.items = {**{k: v for k, v in self.items.items() if k.startswith("builtin-")}, **found}
                self.error = False
        except Exception:
            with self.lock:
                self.error = True
        finally:
            with self.lock:
                self.updated, self.loading = time.monotonic(), False


def valid_app_id(value):
    return (isinstance(value, str) and 1 <= len(value) <= 512 and
            not any(c in value for c in ("\x00", "\r", "\n", '"')) and
            not value.lower().startswith(("http:", "https:", "file:", "shell:")))


class Desktop:
    def __init__(self):
        self.catalog = AppCatalog()
        self.user = ctypes.WinDLL("user32", use_last_error=True)
        self.kernel = ctypes.WinDLL("kernel32", use_last_error=True)
        self.dwm = ctypes.WinDLL("dwmapi", use_last_error=True)
        prototypes = {
            "IsWindowVisible": ([wintypes.HWND], wintypes.BOOL),
            "IsIconic": ([wintypes.HWND], wintypes.BOOL),
            "GetWindowTextLengthW": ([wintypes.HWND], ctypes.c_int),
            "GetWindowTextW": ([wintypes.HWND, wintypes.LPWSTR, ctypes.c_int], ctypes.c_int),
            "GetWindowThreadProcessId": ([wintypes.HWND, ctypes.POINTER(wintypes.DWORD)], wintypes.DWORD),
            "GetWindowLongW": ([wintypes.HWND, ctypes.c_int], wintypes.LONG),
            "GetWindow": ([wintypes.HWND, wintypes.UINT], wintypes.HWND),
            "GetForegroundWindow": ([], wintypes.HWND),
            "ShowWindowAsync": ([wintypes.HWND, ctypes.c_int], wintypes.BOOL),
            "SetForegroundWindow": ([wintypes.HWND], wintypes.BOOL),
            "GetAsyncKeyState": ([ctypes.c_int], ctypes.c_short),
        }
        for name, (args, result) in prototypes.items():
            fn = getattr(self.user, name); fn.argtypes, fn.restype = args, result
        self.kernel.OpenProcess.argtypes = [wintypes.DWORD, wintypes.BOOL, wintypes.DWORD]
        self.kernel.OpenProcess.restype = wintypes.HANDLE
        self.kernel.CloseHandle.argtypes = [wintypes.HANDLE]
        self.kernel.QueryFullProcessImageNameW.argtypes = [wintypes.HANDLE, wintypes.DWORD, wintypes.LPWSTR, ctypes.POINTER(wintypes.DWORD)]
        self.kernel.GetProcessTimes.argtypes = [wintypes.HANDLE, *([ctypes.POINTER(wintypes.FILETIME)] * 4)]
        self.kernel.GetApplicationUserModelId.argtypes = [wintypes.HANDLE, ctypes.POINTER(wintypes.UINT), wintypes.LPWSTR]
        self.kernel.GetApplicationUserModelId.restype = wintypes.LONG
        self.dwm.DwmGetWindowAttribute.argtypes = [wintypes.HWND, wintypes.DWORD, ctypes.c_void_p, wintypes.DWORD]

    def process_info(self, pid):
        handle = self.kernel.OpenProcess(0x1000, False, pid)
        path, created, application = "", 0, ""
        if handle:
            try:
                buffer, size = ctypes.create_unicode_buffer(32768), wintypes.DWORD(32768)
                if self.kernel.QueryFullProcessImageNameW(handle, 0, buffer, ctypes.byref(size)):
                    path = buffer.value
                times = [wintypes.FILETIME() for _ in range(4)]
                if self.kernel.GetProcessTimes(handle, *(ctypes.byref(t) for t in times)):
                    created = (times[0].dwHighDateTime << 32) | times[0].dwLowDateTime
                name, length = ctypes.create_unicode_buffer(512), wintypes.UINT(512)
                if self.kernel.GetApplicationUserModelId(handle, ctypes.byref(length), name) == 0:
                    application = name.value
            finally:
                self.kernel.CloseHandle(handle)
        return path, created, application

    def windows(self):
        rows, foreground = [], self.user.GetForegroundWindow()
        self.user.GetClassNameW.argtypes = [wintypes.HWND, wintypes.LPWSTR, ctypes.c_int]
        callback_type = ctypes.WINFUNCTYPE(wintypes.BOOL, wintypes.HWND, wintypes.LPARAM)
        self.user.EnumWindows.argtypes = [callback_type, wintypes.LPARAM]
        self.user.EnumChildWindows.argtypes = [wintypes.HWND, callback_type, wintypes.LPARAM]
        processes = {}
        def info(pid):
            if pid not in processes:
                processes[pid] = self.process_info(pid)
            return processes[pid]

        def collect(hwnd, _):
            if len(rows) >= 128 or not self.user.IsWindowVisible(hwnd):
                return True
            window_class = ctypes.create_unicode_buffer(128)
            self.user.GetClassNameW(hwnd, window_class, len(window_class))
            if window_class.value in {"Progman", "WorkerW", "Shell_TrayWnd", "Shell_SecondaryTrayWnd"}:
                return True
            style = self.user.GetWindowLongW(hwnd, -20)
            if style & 0x80 or (self.user.GetWindow(hwnd, 4) and not style & 0x40000):
                return True
            length = self.user.GetWindowTextLengthW(hwnd)
            if not length:
                return True
            cloaked = wintypes.DWORD()
            if self.dwm.DwmGetWindowAttribute(hwnd, 14, ctypes.byref(cloaked), ctypes.sizeof(cloaked)) == 0 and cloaked.value:
                return True
            title = ctypes.create_unicode_buffer(min(length + 1, 513))
            self.user.GetWindowTextW(hwnd, title, len(title))
            pid = wintypes.DWORD()
            self.user.GetWindowThreadProcessId(hwnd, ctypes.byref(pid))
            path, created, application = info(pid.value)
            identity = f"{hwnd}:{pid.value}:{created}"
            if Path(path).stem.casefold() == "applicationframehost":
                # UWP content belongs to a different process from its top-level frame.
                content = []
                def child(child_hwnd, _):
                    child_pid = wintypes.DWORD()
                    self.user.GetWindowThreadProcessId(child_hwnd, ctypes.byref(child_pid))
                    if child_pid.value != pid.value:
                        child_path, child_start, child_app = info(child_pid.value)
                        if child_path and child_app:
                            content.append((child_path, child_start, child_app, child_pid.value))
                            return False
                    return True
                self.user.EnumChildWindows(hwnd, callback_type(child), 0)
                if content:
                    path, start, application, app_pid = content[0]
                    identity += f":{app_pid}:{start}"
            rows.append({"id": app_key(identity), "hwnd": hwnd,
                         "pid": pid.value, "executable": path, "app_id": application, "title": title.value,
                         "app": Path(path).stem if path else "Windows 앱",
                         "active": hwnd == foreground, "minimized": bool(self.user.IsIconic(hwnd))})
            return True
        self.user.EnumWindows(callback_type(collect), 0)
        return rows

    def public_windows(self):
        with self.catalog.lock:
            labels = {a.get("app_id", "").casefold(): a["name"] for a in self.catalog.items.values() if a.get("app_id")}
        builtins = {"notepad": "메모장", "calculatorapp": "계산기", "calculator": "계산기", "mspaint": "그림판", "explorer": "파일 탐색기"}
        result = []
        for row in self.windows():
            public = {k: row[k] for k in ("id", "title", "app", "active", "minimized")}
            public["app"] = labels.get(row["app_id"].casefold(), builtins.get(row["app"].casefold(), row["app"]))
            result.append(public)
        return {"windows": result}

    def focus(self, identifier):
        row = next((w for w in self.windows() if w["id"] == identifier), None)
        if row is None:
            raise LookupError("이미 닫힌 창입니다. 목록을 새로 고쳐 주세요.")
        hwnd = row["hwnd"]
        if row["minimized"]:
            self.user.ShowWindowAsync(hwnd, 9)
            time.sleep(.08)
        self.user.SetForegroundWindow(hwnd)
        if self.user.GetForegroundWindow() != hwnd:
            # Windows unlocks foreground changes on ALT input. Never release a user's held key.
            if not any(self.user.GetAsyncKeyState(k) & 0x8000 for k in (0x10, 0x11, 0x12, 0x5B, 0x5C)):
                from .server import WindowsExecutor
                WindowsExecutor.send_keys([0x12], self.user)
                self.user.SetForegroundWindow(hwnd)
        for _ in range(5):
            if self.user.GetForegroundWindow() == hwnd:
                return {"ok": True, "focused": True}
            time.sleep(.04)
        raise RuntimeError("Windows가 창 전환을 제한했습니다. PC에서 열린 메뉴·관리자 창을 확인하세요.")

    def existing(self, executable, application=""):
        windows = self.windows()
        if application:
            matching = [w for w in windows if w["app_id"].casefold() == application.casefold()]
            if matching:
                return next((w for w in matching if w["active"]), matching[0])
        if not executable:
            return None
        expected = str(builtin_path(executable)) if executable in {
            "notepad.exe", "calc.exe", "explorer.exe", "mspaint.exe"} else executable
        rows = [w for w in windows if w["executable"].casefold() == expected.casefold()]
        # Packaged Windows Notepad/Calculator may use a different installation path.
        if not rows and executable in {"notepad.exe", "calc.exe", "mspaint.exe"}:
            stems = {"notepad.exe": {"notepad"}, "calc.exe": {"calculatorapp", "calculator"}, "mspaint.exe": {"mspaint", "paint"}}
            rows = [w for w in windows if w["app"].casefold() in stems[executable]]
        return next((w for w in rows if w["active"]), rows[0] if rows else None)

    def launch(self, action):
        if action.get("focus_existing", False):
            row = self.existing(action.get("executable", ""), action.get("app_id", ""))
            if row:
                return self.focus(row["id"])
        if action["type"] == "app":
            subprocess.Popen([str(builtin_path("explorer.exe")), "shell:AppsFolder\\" + action["app_id"]],
                             shell=False, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
        else:
            executable = action["executable"]
            if executable in {"notepad.exe", "calc.exe", "explorer.exe", "mspaint.exe"}:
                executable = str(builtin_path(executable))
            subprocess.Popen([executable, *action.get("args", [])], shell=False,
                             stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
        return {"ok": True, "focused": False}
