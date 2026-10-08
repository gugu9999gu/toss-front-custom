import concurrent.futures as cf
import datetime as dt
import json
import re
from pathlib import Path
import subprocess
import sys
import time

sys.stdout.reconfigure(encoding='utf-8')
ADB = Path.home() / 'AppData/Local/Android/Sdk/platform-tools/adb.exe'
OUT = Path(__file__).resolve().parent / 'toss-boot-inspection.jsonl'
TARGET = 'toss_front2'
START = time.monotonic()


def run(args, timeout=3):
    try:
        result = subprocess.run([str(ADB), *args], capture_output=True, text=True,
                                encoding='utf-8', errors='replace', timeout=timeout,
                                creationflags=0x08000000)
        return {'exit_code': result.returncode, 'stdout': result.stdout.strip(), 'stderr': result.stderr.strip()}
    except subprocess.TimeoutExpired:
        return {'exit_code': None, 'stdout': '', 'stderr': 'timeout'}


def emit(event):
    event['time'] = dt.datetime.now(dt.timezone(dt.timedelta(hours=9))).isoformat(timespec='milliseconds')
    event['elapsed_seconds'] = round(time.monotonic() - START, 3)
    line = json.dumps(event, ensure_ascii=False)
    with OUT.open('a', encoding='utf-8') as out:
        out.write(line + '\n')
    print(line, flush=True)


def main():
    OUT.write_text('', encoding='utf-8')
    emit({'event': 'READY', 'mode': 'read-only', 'duration_seconds': 300, 'target_model': TARGET})
    while time.monotonic() - START < 300:
        listed = run(['devices', '-l'])
        serials = []
        for row in listed['stdout'].splitlines():
            fields = row.split()
            if len(fields) > 1 and fields[1] == 'device' and ('model:' + TARGET in fields or ('product:' + TARGET in fields and 'device:' + TARGET in fields)):
                serials.append(fields[0])
        if len(serials) == 1:
            serial = serials[0]
            emit({'event': 'ADB_TARGET_SEEN', 'model_metadata': TARGET})
            model = run(['-s', serial, 'shell', 'getprop', 'ro.product.model'])
            if model['exit_code'] != 0:
                emit({'event': 'MODEL_READ_ERROR', 'result': model})
                time.sleep(0.15)
                continue
            normalized_model = re.sub(r'[^a-zA-Z0-9]', '_', model['stdout'])
            if normalized_model != TARGET:
                emit({'event': 'MODEL_DIFFERENCE', 'model': model['stdout'], 'metadata_matches_target': True})
            emit({'event': 'CONNECTED', 'model': model['stdout']})
            queries = {
                'packages': ['shell', 'pm', 'list', 'packages', '-f'],
                'home_activity': ['shell', 'cmd', 'package', 'resolve-activity', '--brief', '-a', 'android.intent.action.MAIN', '-c', 'android.intent.category.HOME'],
                'process_names': ['shell', 'ps', '-A', '-o', 'NAME'],
                'current_user': ['shell', 'am', 'get-current-user'],
                'usb_config': ['shell', 'getprop', 'sys.usb.config'],
                'usb_state': ['shell', 'getprop', 'sys.usb.state'],
                'persistent_usb_config': ['shell', 'getprop', 'persist.sys.usb.config'],
                'boot_completed': ['shell', 'getprop', 'sys.boot_completed'],
                'android_version': ['shell', 'getprop', 'ro.build.version.release'],
            }
            result = {}
            with cf.ThreadPoolExecutor(max_workers=4) as pool:
                futures = {pool.submit(run, ['-s', serial, *args]): name for name, args in queries.items()}
                for future in cf.as_completed(futures):
                    name = futures[future]
                    value = future.result()
                    result[name] = value
                    if name == 'packages':
                        rows = value['stdout'].splitlines()
                        value = {**value, 'stdout': '\n'.join(r for r in rows if any(t in r.lower() for t in ['toss', 'viva', 'front', 'launcher', 'kiosk', 'mdm']))}
                    elif name == 'process_names':
                        value = {**value, 'stdout': '\n'.join(r for r in value['stdout'].splitlines() if any(t in r.lower() for t in ['toss', 'viva', 'front', 'launcher', 'kiosk', 'adbd']))}
                    emit({'event': 'QUERY', 'name': name, 'result': value})
            report = Path(__file__).resolve().parent / 'toss-boot-inspection.json'
            report.write_text(json.dumps({'model': TARGET, 'queries': result}, ensure_ascii=False, indent=2), encoding='utf-8')
            emit({'event': 'FINISHED', 'read_only': True, 'queries_completed': len(result)})
            return
        time.sleep(0.15)
    emit({'event': 'TIMEOUT'})


if __name__ == '__main__':
    main()
