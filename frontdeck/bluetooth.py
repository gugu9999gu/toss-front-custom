"""Encrypted, OS-paired RFCOMM with bounded FrontDeck JSON frames (Windows)."""
from __future__ import annotations
import ctypes
import hmac
import json
import re
import socket
import struct
import threading
import uuid

SERVICE_UUID = 'd6c83020-6e4d-4fc5-a632-0419fef81ca3'
MAX_REQUEST, MAX_RESPONSE = 16384, 4194304


def read_frame(stream, limit=MAX_REQUEST):
    def exact(size):
        data = bytearray()
        while len(data) < size:
            chunk = stream.recv(size - len(data))
            if not chunk:
                raise EOFError()
            data.extend(chunk)
        return bytes(data)
    length, = struct.unpack('!I', exact(4))
    if not 0 < length <= limit:
        raise ValueError('Invalid frame size')
    return json.loads(exact(length).decode('utf-8'))


def write_frame(stream, value):
    data = json.dumps(value, ensure_ascii=True, separators=(',', ':')).encode('utf-8')
    if len(data) > MAX_RESPONSE:
        raise ValueError('Response too large')
    stream.sendall(struct.pack('!I', len(data)) + data)


def dispatch(controller, message):
    if not isinstance(message, dict) or set(message) != {'version', 'id', 'route', 'token', 'body'}:
        raise ValueError('Invalid request')
    identifier, route, token, body = (message[k] for k in ('id', 'route', 'token', 'body'))
    if type(message['version']) is not int or message['version'] != 1 or not isinstance(identifier, str) or not re.fullmatch(r'[0-9]{1,16}', identifier):
        raise ValueError('Invalid protocol')
    if not isinstance(route, str) or route not in {'config', 'windows', 'apps', 'editor', 'pair', 'action', 'focus', 'save', 'delete', 'input'} or not isinstance(body, dict) or not isinstance(token, str) or len(token) > 256:
        raise ValueError('Invalid route')
    if route == 'pair':
        status, result = controller.pair(body['pin']) if set(body) == {'pin'} else (400, {'error': 'Invalid pairing request'})
    elif not hmac.compare_digest(token.encode(), controller.token.encode()):
        status, result = 401, {'error': 'PC 연결이 필요합니다.'}
    elif route in {'config', 'windows', 'apps', 'editor'}:
        if body:
            status, result = 400, {'error': 'Invalid read request'}
        else:
            status, result = controller.read(route)
    elif route == 'action':
        status, result = controller.action(body)
    elif route == 'focus':
        status, result = controller.focus(body)
    elif route == 'input':
        status, result = controller.input.handle(body)
    else:
        status, result = controller.modify(route, body)
    return {'id': identifier, 'status': status, 'result': result}


class GUID(ctypes.Structure):
    _fields_ = [('data1', ctypes.c_uint32), ('data2', ctypes.c_uint16), ('data3', ctypes.c_uint16), ('data4', ctypes.c_ubyte * 8)]


class SOCKADDR_BTH(ctypes.Structure):
    _pack_ = 1
    _fields_ = [('family', ctypes.c_uint16), ('address', ctypes.c_uint64), ('service', GUID), ('port', ctypes.c_uint32)]


class SOCKET_ADDRESS(ctypes.Structure):
    _fields_ = [('address', ctypes.c_void_p), ('length', ctypes.c_int)]


class CSADDR_INFO(ctypes.Structure):
    _fields_ = [('local', SOCKET_ADDRESS), ('remote', SOCKET_ADDRESS), ('type', ctypes.c_int), ('protocol', ctypes.c_int)]


class WSAQUERYSET(ctypes.Structure):
    _fields_ = [('size', ctypes.c_uint32), ('name', ctypes.c_wchar_p), ('service', ctypes.POINTER(GUID)),
                ('version', ctypes.c_void_p), ('comment', ctypes.c_wchar_p), ('namespace', ctypes.c_uint32),
                ('provider', ctypes.c_void_p), ('context', ctypes.c_wchar_p), ('protocols', ctypes.c_uint32),
                ('protocol', ctypes.c_void_p), ('query', ctypes.c_wchar_p), ('addresses', ctypes.c_uint32),
                ('csaddr', ctypes.POINTER(CSADDR_INFO)), ('flags', ctypes.c_uint32), ('blob', ctypes.c_void_p)]


class Advertisement:
    def __init__(self, address, channel):
        self.service = GUID.from_buffer_copy(uuid.UUID(SERVICE_UUID).bytes_le)
        self.address = SOCKADDR_BTH(32, int(address.replace(':', ''), 16), GUID(), channel)
        endpoint = SOCKET_ADDRESS(ctypes.addressof(self.address), ctypes.sizeof(self.address))
        self.csaddr = CSADDR_INFO(endpoint, endpoint, 1, 3)
        self.query = WSAQUERYSET(size=ctypes.sizeof(WSAQUERYSET), name='FrontDeck', service=ctypes.pointer(self.service),
                                 namespace=16, addresses=1, csaddr=ctypes.pointer(self.csaddr))
        self.api = ctypes.WinDLL('ws2_32', use_last_error=True)
        self.api.WSASetServiceW.argtypes = [ctypes.POINTER(WSAQUERYSET), ctypes.c_int, ctypes.c_uint32]
        self.api.WSASetServiceW.restype = ctypes.c_int
        self.api.WSAGetLastError.restype = ctypes.c_int
        if self.api.WSASetServiceW(ctypes.byref(self.query), 0, 0) != 0:
            raise OSError(self.api.WSAGetLastError(), 'Bluetooth SDP registration failed')

    def close(self):
        if self.query is not None:
            self.api.WSASetServiceW(ctypes.byref(self.query), 2, 0)
            self.query = None


class BluetoothServer:
    def __init__(self, controller):
        self.controller, self.closed = controller, threading.Event()
        self.lock, self.clients, self.slots = threading.Lock(), set(), threading.BoundedSemaphore(4)
        self.requests = 0
        self.input_requests = 0
        self.socket = socket.socket(socket.AF_BLUETOOTH, socket.SOCK_STREAM, socket.BTPROTO_RFCOMM)
        self.advertisement = None
        try:
            # Mandatory Windows bonding and encryption. Plain/insecure clients are rejected by the radio.
            self.socket.setsockopt(3, ctypes.c_int(0x80000001).value, 1)
            self.socket.setsockopt(3, 2, 1)
            # CPython parses a signed C int; -1 is Windows BT_PORT_ANY.
            self.socket.bind(('00:00:00:00:00:00', -1))
            self.socket.listen(4)
            self.socket.settimeout(1)
            address, channel = self.socket.getsockname()
            self.advertisement = Advertisement(address, channel)
            self.info = {'address': address, 'name': socket.gethostname(), 'uuid': SERVICE_UUID}
        except Exception:
            self.socket.close()
            raise

    def start(self):
        threading.Thread(target=self.serve, daemon=True, name='FrontDeck-Bluetooth').start()

    def serve(self):
        while not self.closed.is_set():
            try:
                client, _ = self.socket.accept()
            except socket.timeout:
                continue
            except OSError:
                break
            if not self.slots.acquire(blocking=False):
                client.close(); continue
            with self.lock:
                self.clients.add(client)
            threading.Thread(target=self.handle, args=(client,), daemon=True).start()

    def handle(self, client):
        try:
            client.settimeout(20)
            while not self.closed.is_set():
                message = read_frame(client)
                try:
                    reply = dispatch(self.controller, message)
                except ValueError:
                    raise
                except Exception:
                    # Do not include local paths or native exception text in a remote reply.
                    reply = {'id': message['id'], 'status': 500, 'result': {'error': 'PC 요청을 처리하지 못했습니다.'}}
                if reply['status'] == 200:
                    with self.lock:
                        self.requests += 1
                        if message['route'] == 'input':
                            self.input_requests += 1
                write_frame(client, reply)
        except (ValueError, UnicodeError, EOFError, OSError, TypeError):
            pass
        finally:
            with self.lock:
                self.clients.discard(client)
            client.close(); self.slots.release()

    def close(self):
        self.closed.set()
        if self.advertisement:
            self.advertisement.close()
        self.socket.close()
        with self.lock:
            for client in list(self.clients):
                client.close()
