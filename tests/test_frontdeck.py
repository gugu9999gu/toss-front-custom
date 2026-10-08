import ctypes
from http.client import HTTPConnection
import json
from pathlib import Path
import tempfile
import threading
import unittest
from concurrent.futures import ThreadPoolExecutor
from frontdeck.server import Controller, FrontDeckServer, WindowsExecutor, load_config, public_config

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
