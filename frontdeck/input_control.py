"""Bounded, ordered pointer/keyboard input shared by all authenticated transports."""
from __future__ import annotations
from collections import OrderedDict, deque
import ctypes
from ctypes import wintypes
import json
import re
import threading
import time


class MOUSE(ctypes.Structure):
    _fields_ = [('dx', wintypes.LONG), ('dy', wintypes.LONG), ('data', wintypes.DWORD),
                ('flags', wintypes.DWORD), ('time', wintypes.DWORD), ('extra', wintypes.WPARAM)]


class KEY(ctypes.Structure):
    _fields_ = [('vk', wintypes.WORD), ('scan', wintypes.WORD), ('flags', wintypes.DWORD),
                ('time', wintypes.DWORD), ('extra', wintypes.WPARAM)]


class HARDWARE(ctypes.Structure):
    _fields_ = [('msg', wintypes.DWORD), ('low', wintypes.WORD), ('high', wintypes.WORD)]


class UNION(ctypes.Union):
    _fields_ = [('mi', MOUSE), ('ki', KEY), ('hi', HARDWARE)]


class INPUT(ctypes.Structure):
    _anonymous_ = ('u',)
    _fields_ = [('type', wintypes.DWORD), ('u', UNION)]


class NativeInput:
    def __init__(self, keys, send_keys, dry_run=False, user32=None):
        self.keys, self.send_keys, self.dry_run = keys, send_keys, dry_run
        self.user32 = user32

    def event(self, mouse=None, unicode_unit=None, released=False):
        api = self.user32 or ctypes.WinDLL('user32', use_last_error=True)
        api.SendInput.argtypes = [wintypes.UINT, ctypes.POINTER(INPUT), ctypes.c_int]
        api.SendInput.restype = wintypes.UINT
        value = INPUT(type=0 if mouse is not None else 1)
        if mouse is not None:
            dx, dy, data, flags = mouse
            value.mi = MOUSE(dx, dy, data & 0xffffffff, flags, 0, 0)
        else:
            value.ki = KEY(0, unicode_unit, 4 | (2 if released else 0), 0, 0)
        if api.SendInput(1, ctypes.byref(value), ctypes.sizeof(INPUT)) != 1:
            raise OSError('Windows input rejected')

    def execute(self, body):
        if self.dry_run:
            return {'ok': True, 'preview': True}
        kind = body['kind']
        if kind == 'move':
            self.event(mouse=(body['dx'], body['dy'], 0, 1))
        elif kind == 'wheel':
            if body['vertical']:
                self.event(mouse=(0, 0, body['vertical'], 0x800))
            if body['horizontal']:
                self.event(mouse=(0, 0, body['horizontal'], 0x1000))
        elif kind == 'click':
            key, down, up = {'left': (1, 2, 4), 'right': (2, 8, 16), 'middle': (4, 32, 64)}[body['button']]
            api = self.user32 or ctypes.WinDLL('user32', use_last_error=True)
            api.GetAsyncKeyState.argtypes = [ctypes.c_int]
            api.GetAsyncKeyState.restype = ctypes.c_short
            if api.GetAsyncKeyState(key) & 0x8000:
                raise OSError('Mouse button already held')
            try:
                self.event(mouse=(0, 0, 0, down))
            finally:
                # A failed key-up gets a second release attempt; no button remains held.
                try:
                    self.event(mouse=(0, 0, 0, up))
                except OSError:
                    self.event(mouse=(0, 0, 0, up))
        elif kind == 'key':
            self.send_keys([self.keys[k] for k in body['keys']], user32=self.user32)
        elif kind == 'text':
            api = self.user32 or ctypes.WinDLL('user32', use_last_error=True)
            api.GetAsyncKeyState.argtypes = [ctypes.c_int]
            api.GetAsyncKeyState.restype = ctypes.c_short
            if any(api.GetAsyncKeyState(k) & 0x8000 for k in (0x11, 0x12, 0x5b, 0x5c)):
                raise OSError('Release PC modifier keys first')
            raw = body['text'].encode('utf-16-le')
            for offset in range(0, len(raw), 2):
                unit = int.from_bytes(raw[offset:offset + 2], 'little')
                try:
                    self.event(unicode_unit=unit)
                finally:
                    try:
                        self.event(unicode_unit=unit, released=True)
                    except OSError:
                        self.event(unicode_unit=unit, released=True)
        return {'ok': True}


class InputController:
    def __init__(self, executor, clock=time.monotonic):
        self.executor, self.clock = executor, clock
        self.lock, self.sessions, self.times = threading.Lock(), {}, deque()

    def handle(self, body):
        try:
            self.validate(body)
        except (ValueError, TypeError, KeyError, UnicodeError):
            return 400, {'error': '잘못된 입력 요청입니다.'}
        identity = json.dumps(body, sort_keys=True, ensure_ascii=True)
        with self.lock:
            now = self.clock()
            self.sessions = {k: v for k, v in self.sessions.items() if v['time'] > now - 300}
            session = self.sessions.get(body['session'])
            if session is None:
                if len(self.sessions) >= 32:
                    return 429, {'error': '입력 연결이 너무 많습니다.'}
                session = self.sessions[body['session']] = {'seq': 0, 'time': now, 'recent': OrderedDict()}
            number = body['seq']
            if number in session['recent']:
                old, status, result = session['recent'][number]
                return (status, result) if old == identity else (409, {'error': '입력 번호가 충돌했습니다.'})
            if number <= session['seq']:
                return 409, {'error': '만료된 입력 요청입니다.'}
            while self.times and self.times[0] <= now - 1:
                self.times.popleft()
            if len(self.times) >= 80:
                return 429, {'error': '입력을 잠시 후 다시 해 주세요.'}
            self.times.append(now)
            try:
                result, status = self.executor.execute(body), 200
            except Exception:
                status, result = 500, {'error': 'PC 입력 실패 · 관리자 창이나 눌린 키를 확인하세요.'}
            session['seq'], session['time'] = number, now
            session['recent'][number] = identity, status, result
            while len(session['recent']) > 128:
                session['recent'].popitem(last=False)
            return status, result

    def validate(self, body):
        if not isinstance(body, dict) or not isinstance(body.get('session'), str) or not re.fullmatch(r'[A-Za-z0-9_-]{8,64}', body['session']):
            raise ValueError()
        if type(body.get('seq')) is not int or not 1 <= body['seq'] <= 2147483647:
            raise ValueError()
        kind = body.get('kind')
        fields = {'move': {'dx', 'dy'}, 'wheel': {'vertical', 'horizontal'}, 'click': {'button'}, 'key': {'keys'}, 'text': {'text'}}
        if not isinstance(kind, str) or kind not in fields or set(body) != {'kind', 'session', 'seq'} | fields[kind]:
            raise ValueError()
        if kind in ('move', 'wheel'):
            bound = 512 if kind == 'move' else 1200
            if any(type(body[k]) is not int or not -bound <= body[k] <= bound for k in fields[kind]):
                raise ValueError()
        elif kind == 'click':
            if not isinstance(body['button'], str) or body['button'] not in ('left', 'right', 'middle'):
                raise ValueError()
        elif kind == 'key':
            keys = body['keys']
            if not isinstance(keys, list) or not 1 <= len(keys) <= 5 or any(not isinstance(k, str) or k not in self.executor.keys for k in keys) or len(set(keys)) != len(keys):
                raise ValueError()
        else:
            value = body['text']
            if not isinstance(value, str) or not 1 <= len(value) <= 512 or '\x00' in value or len(value.encode('utf-16-le')) > 2048:
                raise ValueError()
