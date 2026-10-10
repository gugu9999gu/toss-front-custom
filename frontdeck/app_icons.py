"""Read Windows application icons in a bounded background cache, as PNG only."""
from __future__ import annotations

import base64
from collections import OrderedDict
import ctypes
from ctypes import wintypes as W
import os
import queue
import struct
import threading
import time
import zlib

SIZE = 48


def png_rgba(pixels, size=SIZE):
    if len(pixels) != size * size * 4: raise ValueError('Invalid icon dimensions')
    def chunk(kind, data):
        return struct.pack('>I', len(data)) + kind + data + struct.pack('>I', zlib.crc32(kind + data))
    scanlines = b''.join(b'\0' + pixels[y*size*4:(y+1)*size*4] for y in range(size))
    return (b'\x89PNG\r\n\x1a\n' + chunk(b'IHDR', struct.pack('>IIBBBBB', size, size, 8, 6, 0, 0, 0))
            + chunk(b'IDAT', zlib.compress(scanlines)) + chunk(b'IEND', b''))


class WindowsIcons:
    """Own Shell icons, borrow window icons, and always release GDI resources."""
    def __init__(self):
        self.user = ctypes.WinDLL('user32', use_last_error=True)
        self.gdi = ctypes.WinDLL('gdi32', use_last_error=True)
        self.shell = ctypes.WinDLL('shell32', use_last_error=True)
        self.ole = ctypes.OleDLL('ole32')
        self.ole.CoInitializeEx.argtypes = [ctypes.c_void_p, W.DWORD]
        self.ole.CoInitializeEx.restype = W.LONG
        if self.ole.CoInitializeEx(None, 2) < 0: raise OSError('Icon COM initialization failed')
        self.ole.CoTaskMemFree.argtypes = [ctypes.c_void_p]
        self.shell.SHParseDisplayName.argtypes = [W.LPCWSTR, ctypes.c_void_p, ctypes.POINTER(ctypes.c_void_p), W.DWORD, ctypes.POINTER(W.DWORD)]
        self.shell.SHParseDisplayName.restype = W.LONG
        self.shell.SHGetFileInfoW.argtypes = [ctypes.c_void_p, W.DWORD, ctypes.c_void_p, W.UINT, W.UINT]
        self.shell.SHGetFileInfoW.restype = ctypes.c_size_t
        self.user.SendMessageTimeoutW.argtypes = [W.HWND, W.UINT, W.WPARAM, W.LPARAM, W.UINT, W.UINT, ctypes.POINTER(ctypes.c_size_t)]
        self.user.SendMessageTimeoutW.restype = ctypes.c_ssize_t
        self.class_icon = self.user.GetClassLongPtrW if ctypes.sizeof(ctypes.c_void_p) == 8 else self.user.GetClassLongW
        self.class_icon.argtypes = [W.HWND, ctypes.c_int]; self.class_icon.restype = ctypes.c_size_t
        self.user.DrawIconEx.argtypes = [W.HDC, ctypes.c_int, ctypes.c_int, W.HICON, ctypes.c_int, ctypes.c_int, W.UINT, W.HBRUSH, W.UINT]
        self.user.DrawIconEx.restype = W.BOOL
        self.user.DestroyIcon.argtypes = [W.HICON]
        self.gdi.CreateCompatibleDC.argtypes = [W.HDC]; self.gdi.CreateCompatibleDC.restype = W.HDC
        self.gdi.CreateDIBSection.argtypes = [W.HDC, ctypes.c_void_p, W.UINT, ctypes.POINTER(ctypes.c_void_p), W.HANDLE, W.DWORD]
        self.gdi.CreateDIBSection.restype = W.HBITMAP
        self.gdi.SelectObject.argtypes = [W.HDC, W.HANDLE]; self.gdi.SelectObject.restype = W.HANDLE
        self.gdi.DeleteObject.argtypes = [W.HANDLE]; self.gdi.DeleteDC.argtypes = [W.HDC]
        self.gdi.GdiFlush.argtypes = []; self.gdi.GdiFlush.restype = W.BOOL

    def shell_icon(self, executable, application):
        class FileInfo(ctypes.Structure):
            _fields_ = [('hIcon', W.HICON), ('iIcon', ctypes.c_int), ('attributes', W.DWORD), ('display', W.WCHAR*260), ('type', W.WCHAR*80)]
        info, pidl = FileInfo(), ctypes.c_void_p()
        try:
            if application and self.shell.SHParseDisplayName('shell:AppsFolder\\' + application, None, ctypes.byref(pidl), 0, None) == 0:
                if self.shell.SHGetFileInfoW(pidl, 0, ctypes.byref(info), ctypes.sizeof(info), 0x108) and info.hIcon:
                    return info.hIcon
            if executable:
                path = ctypes.create_unicode_buffer(executable)
                if self.shell.SHGetFileInfoW(ctypes.cast(path, ctypes.c_void_p), 0, ctypes.byref(info), ctypes.sizeof(info), 0x100):
                    return info.hIcon
            return None
        finally:
            if pidl: self.ole.CoTaskMemFree(pidl)

    def window_icon(self, hwnd):
        for kind in (1, 2, 0):
            result = ctypes.c_size_t()
            if self.user.SendMessageTimeoutW(hwnd, 0x7F, kind, 96, 2, 80, ctypes.byref(result)) and result.value:
                return result.value
        return self.class_icon(hwnd, -14) or self.class_icon(hwnd, -34)

    def render(self, icon):
        # A black/white pair recovers transparency for modern alpha and legacy mask icons.
        class Header(ctypes.Structure):
            _fields_ = [('size', W.DWORD), ('width', W.LONG), ('height', W.LONG), ('planes', W.WORD), ('bits', W.WORD),
                        ('compression', W.DWORD), ('image', W.DWORD), ('x', W.LONG), ('y', W.LONG), ('used', W.DWORD), ('important', W.DWORD)]
        header = Header(ctypes.sizeof(Header), SIZE, -SIZE, 1, 32, 0, SIZE*SIZE*4, 0, 0, 0, 0)
        dc = self.gdi.CreateCompatibleDC(None)
        if not dc: return ''
        bitmap, previous = None, None
        try:
            pixels = ctypes.c_void_p()
            bitmap = self.gdi.CreateDIBSection(dc, ctypes.byref(header), 0, ctypes.byref(pixels), None, 0)
            if not bitmap or not pixels: return ''
            previous = self.gdi.SelectObject(dc, bitmap)
            renders = []
            for background in (0, 255):
                ctypes.memset(pixels, background, SIZE*SIZE*4)
                if not self.user.DrawIconEx(dc, 0, 0, icon, SIZE, SIZE, 0, None, 3): return ''
                self.gdi.GdiFlush()
                renders.append(ctypes.string_at(pixels, SIZE*SIZE*4))
            black, white = renders; rgba = bytearray()
            for index in range(0, len(black), 4):
                alpha = 255 - max(0, min(255, max(white[index+c]-black[index+c] for c in range(3))))
                rgba.extend(min(255, (black[index+c]*255 + alpha//2)//alpha) if alpha else 0 for c in (2, 1, 0))
                rgba.append(alpha)
            return 'data:image/png;base64,' + base64.b64encode(png_rgba(rgba)).decode('ascii')
        finally:
            if previous: self.gdi.SelectObject(dc, previous)
            if bitmap: self.gdi.DeleteObject(bitmap)
            self.gdi.DeleteDC(dc)

    def read(self, executable='', app_id='', hwnd=0):
        # Packaged apps expose their actual icon through AppsFolder rather than their host EXE.
        if app_id:
            owned = self.shell_icon('', app_id)
            if owned:
                try: return self.render(owned)
                finally: self.user.DestroyIcon(owned)
        borrowed = self.window_icon(hwnd) if hwnd else None
        if borrowed: return self.render(borrowed)
        owned = self.shell_icon(executable, '')
        if not owned: return ''
        try: return self.render(owned)
        finally: self.user.DestroyIcon(owned)

    def close(self): self.ole.CoUninitialize()


class IconCache:
    def __init__(self, extractor=None):
        self.lock = threading.Lock()
        self.items, self.pending = OrderedDict(), set()
        self.queue = queue.Queue(maxsize=256)
        self.extractor = extractor
        self.enabled = extractor is not None or os.name == 'nt'
        if self.enabled: threading.Thread(target=self._work, daemon=True).start()

    def get(self, key, **source):
        if not self.enabled: return ''
        with self.lock:
            value, updated = self.items.get(key, ('', 0))
            if key in self.items: self.items.move_to_end(key)
            if key not in self.pending and (key not in self.items or time.monotonic() - updated > (300 if value else 30)):
                try: self.queue.put_nowait((key, source)); self.pending.add(key)
                except queue.Full: pass
            return value

    def _work(self):
        reader = None
        try:
            if self.extractor is None: reader = WindowsIcons()
            extract = self.extractor or reader.read
            while True:
                key, source = self.queue.get()
                if key is None: return
                try: image = extract(**source)
                except Exception: image = ''
                with self.lock:
                    self.items[key] = (image, time.monotonic()); self.pending.discard(key)
                    while len(self.items) > 512: self.items.popitem(last=False)
                self.queue.task_done()
        finally:
            if reader: reader.close()
