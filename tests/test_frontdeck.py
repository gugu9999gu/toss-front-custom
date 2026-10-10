import ctypes
from http.client import HTTPConnection
import json
import os
from pathlib import Path
import tempfile
import threading
import unittest
from unittest.mock import patch
from concurrent.futures import ThreadPoolExecutor
from frontdeck.server import Controller, FrontDeckServer, WindowsExecutor, load_config, public_config
from frontdeck.desktop import AppCatalog, Desktop, valid_app_id

ROOT = Path(__file__).resolve().parents[1]

class Recorder:
    dry_run = True
    def __init__(self): self.ids = []
    def execute(self, action):
        self.ids.append(action["id"])
        return {"ok": True, "label": action["label"]}

class ApiTests(unittest.TestCase):
    def setUp(self):
        self.config = load_config(ROOT / "frontdeck/config.example.json")
        self.executor = Recorder()
        self.controller = Controller(self.config, "test_token_" * 4, "12345678", self.executor)
        self.server = FrontDeckServer(("127.0.0.1", 0), self.controller)
        self.thread = threading.Thread(target=self.server.serve_forever, daemon=True); self.thread.start()
    def tearDown(self):
        self.server.shutdown(); self.server.server_close(); self.thread.join()
    def call(self, route, body=None, authenticated=False, headers=None):
        conn = HTTPConnection("127.0.0.1", self.server.server_port, timeout=3)
        req_headers = {"Content-Type": "application/json"}
        if authenticated: req_headers["Authorization"] = "Bearer " + self.controller.token
        req_headers.update(headers or {})
        conn.request("GET" if body is None else "POST", route, None if body is None else json.dumps(body), req_headers)
        response = conn.getresponse(); raw = response.read(); status = response.status; conn.close()
        return status, raw
    def test_auth_pair_and_public_config(self):
        self.assertEqual(self.call("/api/config")[0], 401)
        self.assertEqual(self.call("/api/pair", {"pin": "wrong"})[0], 403)
        self.assertEqual(self.call("/api/pair", {"pin": "한글"})[0], 403)
        status, raw = self.call("/api/pair", {"pin": "12345678"})
        self.assertEqual(status, 200); self.assertEqual(json.loads(raw)["token"], self.controller.token)
        status, raw = self.call("/api/config", authenticated=True)
        self.assertEqual(status, 200); self.assertNotIn(b"executable", raw); self.assertNotIn(b"keys", raw)
        self.assertEqual(len(json.loads(raw)["profiles"]), 2)
    def test_native_only_fixed_commands(self):
        body = {"id": "notepad", "request_id": "request_001"}
        self.assertEqual(self.call("/api/action", body)[0], 401)
        self.assertEqual(self.call("/api/action", body, True, {"Origin": "https://evil.example"})[0], 403)
        self.assertEqual(self.call("/api/action", body, True, {"Host": "evil.example:8765"})[0], 403)
        self.assertEqual(self.call("/api/action", {**body, "command": "cmd.exe"}, True)[0], 400)
        self.assertEqual(self.call("/api/action", {"id": "arbitrary", "request_id": "request_002"}, True)[0], 404)
        self.assertEqual(self.executor.ids, [])
    def test_concurrent_retries_execute_once_and_conflicts_fail(self):
        body = {"id": "notepad", "request_id": "request_003"}
        with ThreadPoolExecutor(max_workers=4) as pool:
            results = list(pool.map(lambda _: self.call("/api/action", body, True)[0], range(4)))
        self.assertEqual(results, [200] * 4); self.assertEqual(self.executor.ids, ["notepad"])
        self.assertEqual(self.call("/api/action", {**body, "id": "calculator"}, True)[0], 409)
    def test_expired_code_and_rate_limit(self):
        self.controller.pin_deadline = 0
        self.assertEqual(self.call("/api/pair", {"pin": "12345678"})[0], 403)
        for _ in range(9): self.call("/api/pair", {"pin": "wrong"})
        self.assertEqual(self.call("/api/pair", {"pin": "12345678"})[0], 429)
    def test_preview_cannot_trigger_actions_and_paths_are_fixed(self):
        status, raw = self.call("/preview/")
        self.assertEqual(status, 200); self.assertIn(b"FRONTDECK", raw)
        self.assertEqual(self.call("/preview/../../frontdeck/server.py")[0], 404)
        self.assertEqual(self.call("/preview/demo.json")[0], 200)
        self.assertEqual(self.executor.ids, [])
    def test_invalid_and_oversized_body(self):
        self.assertEqual(self.call("/api/action", {"id": "notepad", "request_id": []}, True)[0], 400)
        self.assertEqual(self.call("/api/action", {"id": "notepad", "request_id": "x" * 3000}, True)[0], 413)
    def test_port_cannot_be_shared_with_another_server(self):
        with self.assertRaises(OSError):
            FrontDeckServer(("127.0.0.1", self.server.server_port), self.controller)

class ConfigTests(unittest.TestCase):
    def test_untrusted_actions_rejected(self):
        original = json.loads((ROOT / "frontdeck/config.example.json").read_text(encoding="utf-8"))
        with tempfile.TemporaryDirectory() as folder:
            file = Path(folder) / "config.json"
            for replacement in ({"type": "shell"}, {"type": "launch", "executable": "cmd.exe"},
                                {"type": "hotkey", "keys": ["CTRL", "UNKNOWN"]},
                                {"type": "url", "url": "file:///C:/secret"}):
                data = json.loads(json.dumps(original)); data["actions"][0].update(replacement)
                file.write_text(json.dumps(data), encoding="utf-8")
                with self.assertRaises(ValueError): load_config(file)

    @unittest.skipUnless(os.name == "nt", "Verify installed Windows shell")
    def test_catalog_and_file_manager_launch_use_existing_shell_executable(self):
        desktop = Desktop.__new__(Desktop)
        with patch("frontdeck.desktop.subprocess.Popen") as launch:
            desktop.launch({"type": "app", "app_id": "Example_abcdefgh!App"})
            command = launch.call_args.args[0]
            self.assertTrue(Path(command[0]).is_file())
            self.assertEqual(command[1], "shell:AppsFolder\\Example_abcdefgh!App")
            self.assertFalse(launch.call_args.kwargs["shell"])
            desktop.launch({"type": "launch", "executable": "explorer.exe"})
            self.assertTrue(Path(launch.call_args.args[0][0]).is_file())

    def test_packaged_window_matching_uses_app_identity(self):
        desktop = Desktop.__new__(Desktop)
        other = {"id": "other", "app_id": "Other!App", "executable": "", "app": "ApplicationFrameHost", "active": True}
        target = {"id": "calc", "app_id": "Calculator!App", "executable": "", "app": "CalculatorApp", "active": False}
        desktop.windows = lambda: [other, target]
        self.assertIs(desktop.existing("", "Calculator!App"), target)
        self.assertIsNone(desktop.existing("", "Closed!App"))

    def test_taskbar_names_do_not_expose_packaged_process_metadata(self):
        desktop = Desktop.__new__(Desktop); desktop.catalog = AppCatalog()
        desktop.catalog.items["app"] = {"id": "app", "name": "Calculator", "app_id": "Calculator!App"}
        desktop.windows = lambda: [{"id": "calc", "app_id": "Calculator!App", "executable": "private-path.exe", "pid": 123,
                                   "hwnd": 456, "app": "CalculatorApp", "title": "Calculator", "active": False, "minimized": True}]
        result = desktop.public_windows()["windows"][0]
        self.assertEqual(result["app"], "Calculator")
        for field in ("app_id", "executable", "pid", "hwnd"):
            self.assertNotIn(field, result)


class FakeDesktop:
    def __init__(self):
        self.catalog = AppCatalog()
        self.catalog.updated = float("inf")
        self.ids = ["live-window"]
        self.focused = []
    def public_windows(self):
        return {"windows": [{"id": i, "title": "Example", "app": "notepad", "active": False, "minimized": True} for i in self.ids]}
    def focus(self, identifier):
        if identifier not in self.ids:
            raise LookupError("Closed")
        self.focused.append(identifier)
        return {"ok": True, "focused": True}


class EditingTests(unittest.TestCase):
    call = ApiTests.call
    def setUp(self):
        ApiTests.setUp(self)
        self.folder = tempfile.TemporaryDirectory()
        self.path = Path(self.folder.name) / "config.local.json"
        self.path.write_bytes((ROOT / "frontdeck/config.example.json").read_bytes())
        self.executor.dry_run = False
        self.desktop = FakeDesktop()
        self.controller.config_path = self.path
        self.controller.desktop = self.desktop
    def tearDown(self):
        ApiTests.tearDown(self); self.folder.cleanup()
    def save(self, action=None, request_id="edit_request_001", profile="custom"):
        return {"action": action or {"id": "", "label": "My app", "icon": "desktop", "tone": "blue", "type": "application",
                                     "application_id": "builtin-notepad", "focus_existing": True},
                "profile_id": profile, "request_id": request_id}
    def test_edit_routes_require_native_authentication(self):
        for route in ("windows", "apps", "editor"):
            self.assertEqual(self.call("/api/" + route)[0], 401)
            self.assertEqual(self.call("/api/" + route, authenticated=True)[0], 200)
            self.assertEqual(self.call("/api/" + route, authenticated=True, headers={"Origin": "https://example.com"})[0], 403)
        for route, body in (("save", self.save()), ("delete", {"id": "notepad", "request_id": "delete_001"}),
                            ("focus", {"id": "live-window", "request_id": "focus_001"})):
            self.assertEqual(self.call("/api/" + route, body)[0], 401)
            self.assertEqual(self.call("/api/" + route, body, True, {"Origin": "http://localhost"})[0], 403)
    def test_save_is_persistent_and_retries_do_not_duplicate(self):
        body = self.save()
        with ThreadPoolExecutor(max_workers=3) as pool:
            results = list(pool.map(lambda _: self.call("/api/save", body, True), range(3)))
        self.assertTrue(all(status == 200 for status, _ in results))
        saved = load_config(self.path)
        self.assertEqual(len(saved["actions"]), len(self.config["actions"]) + 1)
        profile = next(p for p in saved["profiles"] if p["id"] == "custom")
        self.assertEqual(len(profile["actions"]), 1)
        action = saved["actions"][profile["actions"][0]]
        self.assertTrue(action["focus_existing"]); self.assertEqual(action["executable"], "notepad.exe")
        self.assertEqual(self.call("/api/save", self.save(request_id=body["request_id"], profile="work"), True)[0], 409)
    def test_update_delete_and_empty_profile_are_preserved(self):
        action = {"id": "explorer", "label": "Files", "type": "application", "application_id": "builtin-explorer", "focus_existing": True}
        self.assertEqual(self.call("/api/save", self.save(action, profile="work"), True)[0], 200)
        self.assertEqual(load_config(self.path)["actions"]["explorer"]["label"], "Files")
        self.assertEqual(self.call("/api/delete", {"id": "explorer", "request_id": "delete_explorer"}, True)[0], 200)
        config = load_config(self.path)
        self.assertNotIn("explorer", config["actions"])
        self.assertTrue(all("explorer" not in p["actions"] for p in config["profiles"]))
    def test_invalid_edit_never_changes_disk_or_memory(self):
        before = self.path.read_bytes()
        for index, change in enumerate(({"type": "shell", "command": "cmd.exe"}, {"type": "application", "application_id": "arbitrary.exe"},
                                        {"type": "hotkey", "keys": ["CTRL", "BOGUS"]}, {"type": "url", "url": "file:///C:/secret"},
                                        {"type": []}, {"type": "launch", "executable": []}, {"tone": []})):
            action = self.save()["action"].copy()
            # Remove type-specific fields before switching action type.
            for key in ("application_id", "focus_existing"): action.pop(key, None)
            action.update(change)
            self.assertEqual(self.call("/api/save", self.save(action, "bad_edit_" + str(index)), True)[0], 400)
            self.assertEqual(self.path.read_bytes(), before)
            self.assertEqual(self.controller.config, self.config)
    def test_failed_atomic_write_keeps_previous_settings(self):
        before = self.path.read_bytes()
        with patch("frontdeck.server.os.replace", side_effect=OSError("Read-only")):
            self.assertEqual(self.call("/api/save", self.save(), True)[0], 500)
        self.assertEqual(self.path.read_bytes(), before); self.assertEqual(self.controller.config, self.config)
        self.assertEqual(list(self.path.parent.glob("*.tmp")), [])
    def test_more_than_twelve_buttons_and_full_profile_limit(self):
        for n in range(36):
            self.assertEqual(self.call("/api/save", self.save(request_id="page_edit_" + str(n), profile="work"), True)[0], 200)
            # Tests are faster than real taps; reset the per-second action quota between edits.
            self.controller.action_times = []
        self.assertEqual(len(load_config(self.path)["profiles"][0]["actions"]), 48)
        before = self.path.read_bytes()
        self.assertEqual(self.call("/api/save", self.save(request_id="over_capacity", profile="work"), True)[0], 400)
        self.assertEqual(self.path.read_bytes(), before)
    def test_live_window_focus_is_deduplicated_and_stale_ids_rejected(self):
        body = {"id": "live-window", "request_id": "focus_live_001"}
        self.assertEqual(self.call("/api/focus", body, True)[0], 200)
        self.assertEqual(self.call("/api/focus", body, True)[0], 200)
        self.assertEqual(self.desktop.focused, ["live-window"])
        self.desktop.ids.clear()
        self.assertEqual(self.call("/api/focus", {**body, "request_id": "focus_closed_001"}, True)[0], 404)
        self.assertEqual(self.call("/api/focus", {**body, "id": 123, "request_id": "focus_hwnd_001"}, True)[0], 400)
    def test_preview_mode_cannot_edit_or_focus(self):
        self.executor.dry_run = True
        self.assertEqual(self.call("/api/save", self.save(), True)[0], 403)
        self.assertEqual(self.call("/api/focus", {"id": "live-window", "request_id": "focus_preview_001"}, True)[0], 403)
        self.assertFalse(self.desktop.focused)
    def test_catalog_only_returns_ids_and_labels(self):
        status, raw = self.call("/api/apps", authenticated=True)
        self.assertEqual(status, 200)
        self.assertNotIn(b"executable", raw); self.assertNotIn(b"app_id", raw)
        self.assertFalse(valid_app_id("shell:AppsFolder")); self.assertFalse(valid_app_id("https://example.com"))
    def test_installed_application_uses_only_catalog_metadata(self):
        self.desktop.catalog.items["known-app"] = {"id": "known-app", "name": "Example", "app_id": "Example_abcdefgh!App"}
        action = self.save()["action"]
        action["application_id"] = "known-app"
        self.assertEqual(self.call("/api/save", self.save(action), True)[0], 200)
        stored = load_config(self.path)
        new_id = next(p for p in stored["profiles"] if p["id"] == "custom")["actions"][0]
        self.assertEqual(stored["actions"][new_id]["app_id"], "Example_abcdefgh!App")
        self.assertEqual(stored["actions"][new_id]["type"], "app")
        action["app_id"] = "https://example.com"
        self.assertEqual(self.call("/api/save", self.save(action, "invalid_raw_appid"), True)[0], 400)

class FakeFunction:
    def __init__(self, call): self.call = call
    def __call__(self, *args): return self.call(*args)

class KeyTests(unittest.TestCase):
    def fake(self, fail_at=None, held=None):
        events = []
        def send(count, pointer, size):
            item = pointer._obj
            events.append((item.ki.wVk, bool(item.ki.dwFlags & 2), size))
            return 0 if len(events) == fail_at else 1
        class User32: pass
        dll = User32(); dll.SendInput = FakeFunction(send)
        dll.GetAsyncKeyState = FakeFunction(lambda key: 0x8000 if key == held else 0)
        return dll, events
    def test_partial_failure_releases_our_modifier(self):
        dll, events = self.fake(fail_at=2)
        with self.assertRaises(RuntimeError): WindowsExecutor.send_keys([0x11, 0x43], dll)
        self.assertEqual([(key, up) for key, up, size in events], [(0x11, False), (0x43, False), (0x11, True)])
        self.assertIn(events[0][2], (28, 40))
    def test_physically_held_modifier_is_not_released(self):
        dll, events = self.fake(held=0x11)
        WindowsExecutor.send_keys([0x11, 0x43], dll)
        self.assertEqual([(key, up) for key, up, size in events], [(0x43, False), (0x43, True)])
    def test_release_is_retried_without_repeating_key_press(self):
        dll, events = self.fake(fail_at=2)
        WindowsExecutor.send_keys([0x43], dll)
        self.assertEqual([(key, up) for key, up, size in events], [(0x43, False), (0x43, True), (0x43, True)])

if __name__ == "__main__": unittest.main()
