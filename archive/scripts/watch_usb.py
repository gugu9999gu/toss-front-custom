import argparse
import ctypes as c
from ctypes import wintypes as w
import datetime as dt
import json
from pathlib import Path
import subprocess
import sys
import threading
import time

sys.stdout.reconfigure(encoding='utf-8')


class GUID(c.Structure):
    _fields_ = [('Data1', w.DWORD), ('Data2', w.WORD), ('Data3', w.WORD), ('Data4', c.c_ubyte * 8)]


class DEVINFO(c.Structure):
    _fields_ = [('cbSize', w.DWORD), ('ClassGuid', GUID), ('DevInst', w.DWORD), ('Reserved', c.c_size_t)]


setup = c.WinDLL('setupapi', use_last_error=True)
cm = c.WinDLL('cfgmgr32', use_last_error=True)
setup.SetupDiGetClassDevsW.argtypes = [c.c_void_p, w.LPCWSTR, w.HWND, w.DWORD]
setup.SetupDiGetClassDevsW.restype = w.HANDLE
setup.SetupDiEnumDeviceInfo.argtypes = [w.HANDLE, w.DWORD, c.POINTER(DEVINFO)]
setup.SetupDiEnumDeviceInfo.restype = w.BOOL
setup.SetupDiGetDeviceInstanceIdW.argtypes = [w.HANDLE, c.POINTER(DEVINFO), w.LPWSTR, w.DWORD, c.POINTER(w.DWORD)]
setup.SetupDiGetDeviceInstanceIdW.restype = w.BOOL
setup.SetupDiGetDeviceRegistryPropertyW.argtypes = [w.HANDLE, c.POINTER(DEVINFO), w.DWORD, c.POINTER(w.DWORD), c.c_void_p, w.DWORD, c.POINTER(w.DWORD)]
setup.SetupDiGetDeviceRegistryPropertyW.restype = w.BOOL
setup.SetupDiDestroyDeviceInfoList.argtypes = [w.HANDLE]
setup.SetupDiDestroyDeviceInfoList.restype = w.BOOL
cm.CM_Get_DevNode_Status.argtypes = [c.POINTER(w.DWORD), c.POINTER(w.DWORD), w.DWORD, w.ULONG]
cm.CM_Get_DevNode_Status.restype = w.DWORD


def prop(handle, dev, code):
    buf = c.create_string_buffer(16384)
    kind, needed = w.DWORD(), w.DWORD()
    if not setup.SetupDiGetDeviceRegistryPropertyW(handle, c.byref(dev), code, c.byref(kind), buf, len(buf), c.byref(needed)):
        return None
    if kind.value == 4:
        return int.from_bytes(buf.raw[:4], 'little')
    data = buf.raw[:needed.value].decode('utf-16-le', errors='replace').rstrip('\0')
    return data.split('\0') if kind.value == 7 else data


def scan():
    handle = setup.SetupDiGetClassDevsW(None, 'USB', None, 2 | 4)
    if handle == c.c_void_p(-1).value:
        raise c.WinError(c.get_last_error())
    devices = {}
    try:
        i = 0
        while True:
            dev = DEVINFO()
            dev.cbSize = c.sizeof(dev)
            if not setup.SetupDiEnumDeviceInfo(handle, i, c.byref(dev)):
                error = c.get_last_error()
                if error == 259:
                    break
                raise c.WinError(error)
            i += 1
            buf = c.create_unicode_buffer(2048)
            if not setup.SetupDiGetDeviceInstanceIdW(handle, c.byref(dev), buf, len(buf), None):
                continue
            status, problem = w.DWORD(), w.DWORD()
            cm.CM_Get_DevNode_Status(c.byref(status), c.byref(problem), dev.DevInst, 0)
            devices[buf.value] = {
                'instance_id': buf.value,
                'hardware_ids': prop(handle, dev, 1),
                'compatible_ids': prop(handle, dev, 2),
                'name': prop(handle, dev, 12) or prop(handle, dev, 0),
                'description': prop(handle, dev, 0),
                'manufacturer': prop(handle, dev, 11),
                'class': prop(handle, dev, 7),
                'service': prop(handle, dev, 4),
                'driver_key': prop(handle, dev, 9),
                'location': prop(handle, dev, 13),
                'problem_code': problem.value,
                'devnode_status': status.value,
            }
    finally:
        setup.SetupDiDestroyDeviceInfoList(handle)
    return devices


def safe_device(device):
    result = dict(device)
    result.pop('instance_id', None)
    result['usb_id'] = device['instance_id'].split('\\')[1]
    return result


def now():
    return dt.datetime.now(dt.timezone(dt.timedelta(hours=9))).isoformat(timespec='milliseconds')


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--seconds', type=int, default=300)
    parser.add_argument('--check', action='store_true')
    args = parser.parse_args()
    previous = scan()
    if args.check:
        print(json.dumps({'count': len(previous), 'devices': [safe_device(d) for d in previous.values()]}, ensure_ascii=False), flush=True)
        return
    directory = Path(__file__).resolve().parent
    stop_file = directory / 'stop-usb-watch'
    stop_file.unlink(missing_ok=True)
    log_path = directory / 'usb-watch-events.jsonl'
    baseline_path = directory / 'usb-watch-start.json'
    baseline_path.write_text(json.dumps(previous, ensure_ascii=False, indent=2), encoding='utf-8')
    lock = threading.Lock()
    stopped = threading.Event()
    seen_at = {}
    stats = {'added': 0, 'removed': 0, 'changed': 0}
    start = time.monotonic()
    with log_path.open('w', encoding='utf-8', buffering=1) as log:
        def emit(event):
            event['time'] = now()
            event['elapsed_seconds'] = round(time.monotonic() - start, 3)
            line = json.dumps(event, ensure_ascii=False)
            with lock:
                log.write(line + '\n')
                print(line, flush=True)

        def protocol_watch():
            adb = Path.home() / 'AppData/Local/Android/Sdk/platform-tools/adb.exe'
            fastboot = adb.with_name('fastboot.exe')
            last = {'ADB': None, 'FASTBOOT': None}
            while not stopped.is_set():
                for protocol, exe, command in [('ADB', adb, ['devices', '-l']), ('FASTBOOT', fastboot, ['devices'])]:
                    if not exe.exists():
                        continue
                    try:
                        out = subprocess.run([str(exe), *command], capture_output=True, text=True, timeout=3, creationflags=0x08000000)
                        rows = []
                        for line in out.stdout.splitlines():
                            if not line.strip() or line.startswith('List of devices'):
                                continue
                            fields = line.split()
                            rows.append(' '.join(['[device-id]', *fields[1:]]))
                        if rows != last[protocol]:
                            emit({'event': protocol, 'devices': rows})
                            last[protocol] = rows
                    except Exception as error:
                        emit({'event': 'PROBE_ERROR', 'protocol': protocol, 'error': str(error)})
                stopped.wait(0.5)

        emit({'event': 'READY', 'usb_device_count': len(previous), 'poll_interval_ms': 50, 'duration_seconds': args.seconds})
        worker = threading.Thread(target=protocol_watch, daemon=True)
        worker.start()
        try:
            while time.monotonic() - start < args.seconds and not stop_file.exists():
                tick = time.monotonic()
                current = scan()
                for instance in current.keys() - previous.keys():
                    seen_at[instance] = time.monotonic()
                    stats['added'] += 1
                    emit({'event': 'ADDED', 'device': safe_device(current[instance])})
                for instance in previous.keys() - current.keys():
                    stats['removed'] += 1
                    connected_at = seen_at.pop(instance, None)
                    emit({'event': 'REMOVED', 'device': safe_device(previous[instance]), 'visible_seconds': None if connected_at is None else round(time.monotonic() - connected_at, 3)})
                for instance in current.keys() & previous.keys():
                    if current[instance] != previous[instance]:
                        stats['changed'] += 1
                        emit({'event': 'CHANGED', 'device': safe_device(current[instance]), 'previous': safe_device(previous[instance])})
                previous = current
                time.sleep(max(0, 0.05 - (time.monotonic() - tick)))
        finally:
            stopped.set()
            worker.join(timeout=4)
            emit({'event': 'FINISHED', **stats, 'usb_device_count': len(previous)})


if __name__ == '__main__':
    main()
