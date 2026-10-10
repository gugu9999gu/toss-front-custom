import ctypes
import json
import socket
import struct
import threading
import unittest
from frontdeck.bluetooth import read_frame, write_frame, dispatch, MAX_REQUEST, SOCKADDR_BTH, WSAQUERYSET
from frontdeck.input_control import InputController, NativeInput
from frontdeck.server import Controller, WindowsExecutor, KEYS, validate_config


class Recorder:
    keys = KEYS
    dry_run = True
    def __init__(self): self.calls = []
    def execute(self, body): self.calls.append(body); return {'ok': True}


class InputTests(unittest.TestCase):
    def setUp(self):
        self.now = 100
        self.executor = Recorder()
        self.control = InputController(self.executor, lambda: self.now)
        self.body = dict(session='session_123', seq=1, kind='text', text='한글 😀')

    def test_retries_conflict_and_stale_input(self):
        self.assertEqual(self.control.handle(self.body)[0], 200)
        self.assertEqual(self.control.handle(dict(self.body))[0], 200)
        self.assertEqual(len(self.executor.calls), 1)
        self.assertEqual(self.control.handle(dict(self.body, text='different'))[0], 409)
        self.assertEqual(self.control.handle(dict(self.body, seq=5))[0], 200)
        self.assertEqual(self.control.handle(dict(self.body, seq=2))[0], 409)

    def test_long_pointer_session_does_not_exhaust_button_quota(self):
        for number in range(1, 1001):
            self.now += .04
            body = dict(session='session_move', seq=number, kind='move', dx=2, dy=-1)
            self.assertEqual(self.control.handle(body)[0], 200)
        self.assertEqual(len(self.control.sessions['session_move']['recent']), 128)

    def test_validation_blocks_unbounded_or_arbitrary_input(self):
        invalid = [dict(self.body, text='x'*513), dict(self.body, text='\ud800'), dict(self.body, text='\x00'),
                   dict(self.body, seq=True), dict(self.body, shell='cmd.exe'), dict(self.body, session=[]),
                   dict(session='session_123', seq=1, kind='key', keys=['CTRL', 'CTRL']),
                   dict(session='session_123', seq=1, kind='move', dx=513, dy=0),
                   dict(session='session_123', seq=1, kind='move', dx=True, dy=0)]
        for body in invalid: self.assertEqual(self.control.handle(body)[0], 400)
        self.assertEqual(self.executor.calls, [])

    def test_failed_input_is_not_repeated(self):
        def fail(body): self.executor.calls.append(body); raise OSError()
        self.executor.execute = fail
        self.assertEqual(self.control.handle(self.body)[0], 500)
        self.assertEqual(self.control.handle(self.body)[0], 500)
        self.assertEqual(len(self.executor.calls), 1)

    def test_native_unicode_emits_utf16_and_mouse_release_on_failure(self):
        class Native(NativeInput):
            def event(self, **event): self.events.append(event)
        native = Native(KEYS, WindowsExecutor.send_keys)
        native.events = []
        class Api:
            class Function:
                def __call__(self, key): return 0
            GetAsyncKeyState = Function()
        native.user32 = Api()
        native.execute(self.body)
        units = [e['unicode_unit'] for e in native.events if not e.get('released')]
        self.assertEqual(units, [0xd55c, 0xae00, 0x20, 0xd83d, 0xde00])
        self.assertEqual(len(native.events), 10)
        native.events = []
        def fail(**event):
            native.events.append(event)
            if event['mouse'][3] == 2: raise OSError()
        native.event = fail
        with self.assertRaises(OSError): native.execute(dict(kind='click', button='left'))
        self.assertEqual(native.events[-1]['mouse'][3], 4)


class BluetoothTests(unittest.TestCase):
    def setUp(self):
        config = validate_config({'profiles': [{'id': 'work', 'name': '작업', 'actions': []}], 'actions': []})
        self.controller = Controller(config, 'token_'*8, '12345678', Recorder())
        self.message = dict(version=1, id='1', route='config', token=self.controller.token, body={})

    def test_native_sdp_structures_match_windows_abi(self):
        self.assertEqual(ctypes.sizeof(SOCKADDR_BTH), 30)
        if ctypes.sizeof(ctypes.c_void_p) == 8: self.assertEqual(ctypes.sizeof(WSAQUERYSET), 120)

    def test_auth_routes_pin_expiry(self):
        self.assertEqual(dispatch(self.controller, self.message)['status'], 200)
        self.assertEqual(dispatch(self.controller, dict(self.message, token='wrong'))['status'], 401)
        with self.assertRaises(ValueError): dispatch(self.controller, dict(self.message, route='shell'))
        with self.assertRaises(ValueError): dispatch(self.controller, dict(self.message, version=True))
        pair = dict(self.message, route='pair', token='', body={'pin': '12345678'})
        self.assertEqual(dispatch(self.controller, pair)['status'], 200)
        self.controller.pin_deadline = 0
        self.assertEqual(dispatch(self.controller, pair)['status'], 403)
        inp = dict(self.message, route='input', body=dict(session='session_123', seq=1, kind='move', dx=2, dy=1))
        self.assertEqual(dispatch(self.controller, inp)['status'], 200)

    def test_fragmented_frames_unicode_and_truncation(self):
        left, right = socket.socketpair()
        try:
            value = dict(self.message, body={'text': '한글 😀'})
            raw = json.dumps(value, ensure_ascii=False).encode('utf-8')
            payload = struct.pack('!I', len(raw)) + raw
            def fragments():
                for byte in payload: right.sendall(bytes([byte]))
            thread = threading.Thread(target=fragments); thread.start()
            self.assertEqual(read_frame(left), value); thread.join()
            write_frame(right, value); self.assertEqual(read_frame(left), value)
            right.sendall(struct.pack('!I', MAX_REQUEST+1))
            with self.assertRaises(ValueError): read_frame(left)
            right.sendall(struct.pack('!I', 10) + b'{}'); right.shutdown(socket.SHUT_WR)
            with self.assertRaises(EOFError): read_frame(left)
        finally: left.close(); right.close()


if __name__ == '__main__': unittest.main()
