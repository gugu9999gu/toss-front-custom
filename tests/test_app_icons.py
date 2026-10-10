"""Icon extraction, bounded async loading and metadata isolation."""
import base64
import ctypes
import json
import os
from pathlib import Path
import struct
import threading
import time
import unittest
import zlib

from frontdeck.app_icons import IconCache, WindowsIcons, png_rgba, SIZE
from frontdeck.desktop import Desktop, builtin_path
from frontdeck.server import Controller, load_config

ROOT = Path(__file__).resolve().parents[1]


def pixels(image):
    raw = base64.b64decode(image.removeprefix('data:image/png;base64,'))
    if not raw.startswith(b'\x89PNG\r\n\x1a\n'): raise AssertionError('Invalid PNG')
    offset, data = 8, b''
    while offset < len(raw):
        length = struct.unpack('>I', raw[offset:offset+4])[0]; kind = raw[offset+4:offset+8]
        value = raw[offset+8:offset+8+length]
        if kind == b'IHDR':
            if struct.unpack('>IIBBBBB', value) != (SIZE, SIZE, 8, 6, 0, 0, 0): raise AssertionError('Unexpected PNG format')
        if kind == b'IDAT': data += value
        offset += length + 12
    scanlines = zlib.decompress(data)
    if len(scanlines) != SIZE * (1 + SIZE*4): raise AssertionError('Invalid pixel count')
    return b''.join(scanlines[y*(SIZE*4+1)+1:(y+1)*(SIZE*4+1)] for y in range(SIZE))


class IconTests(unittest.TestCase):
    def test_async_extraction_never_blocks_requests_or_duplicates_pending_work(self):
        entered, release = threading.Event(), threading.Event()
        calls = []
        def extract(**source):
            calls.append(source); entered.set(); release.wait(3)
            return 'data:image/png;base64,' + base64.b64encode(png_rgba(bytes([20, 40, 80, 255]) * (SIZE*SIZE))).decode()
        cache = IconCache(extract)
        try:
            self.assertEqual(cache.get('same-app', executable='fixture.exe'), '')
            self.assertTrue(entered.wait(1))
            # The worker is still blocked; API calls must immediately return the fallback.
            for _ in range(20): self.assertEqual(cache.get('same-app', executable='fixture.exe'), '')
            self.assertEqual(len(calls), 1)
        finally: release.set()
        cache.queue.join()
        image = cache.get('same-app', executable='fixture.exe')
        self.assertEqual(pixels(image), bytes([20, 40, 80, 255]) * (SIZE*SIZE))

    def test_paired_panel_gets_icon_without_executable_or_app_identity(self):
        class Executor: dry_run = True
        class IconDesktop:
            def action_image(self, action): return 'data:image/png;base64,fixture' if action['type'] == 'launch' else ''
        config = load_config(ROOT / 'frontdeck/config.example.json')
        controller = Controller(config, 'token'*10, '12345678', Executor(), desktop=IconDesktop())
        original = json.dumps(config)
        status, public = controller.read('config')
        self.assertEqual(status, 200)
        self.assertTrue(public['profiles'][0]['actions'][0]['image'].startswith('data:image/png;'))
        for profile in public['profiles']:
            for action in profile['actions']:
                self.assertNotIn('executable', action); self.assertNotIn('app_id', action)
        self.assertEqual(json.dumps(config), original)

    @unittest.skipUnless(os.name == 'nt', 'Windows Shell icon extraction')
    def test_real_windows_icons_have_color_transparency_and_no_gdi_leak(self):
        reader = WindowsIcons()
        kernel, user = ctypes.WinDLL('kernel32'), ctypes.WinDLL('user32')
        kernel.GetCurrentProcess.restype = ctypes.c_void_p
        user.GetGuiResources.argtypes = [ctypes.c_void_p, ctypes.c_uint]; user.GetGuiResources.restype = ctypes.c_ulong
        process = kernel.GetCurrentProcess()
        try:
            note = reader.read(executable=str(builtin_path('notepad.exe')))
            calc = reader.read(executable=str(builtin_path('calc.exe')))
            self.assertTrue(note); self.assertTrue(calc); self.assertNotEqual(note, calc)
            for image in (note, calc):
                rgba = pixels(image)
                self.assertIn(255, rgba[3::4])
                self.assertGreater(len(set(zip(rgba[0::4], rgba[1::4], rgba[2::4]))), 4)
            # calc.exe itself has an opaque monochrome icon; preserve it rather than inventing alpha.
            note_rgba = pixels(note)
            self.assertIn(0, note_rgba[3::4])
            self.assertTrue(any(r != g or g != b for r, g, b in zip(note_rgba[0::4], note_rgba[1::4], note_rgba[2::4])))
            before = user.GetGuiResources(process, 0)
            for _ in range(40): reader.read(executable=str(builtin_path('notepad.exe')))
            self.assertLessEqual(user.GetGuiResources(process, 0), before + 2)
        finally: reader.close()


if __name__ == '__main__': unittest.main()
