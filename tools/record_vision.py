"""Prepare pinned official MediaPipe artifacts for the standalone Android builder."""
from pathlib import Path
import hashlib
import json
import shutil
import urllib.request
import zipfile

ROOT = Path(__file__).resolve().parents[1]

def prepare(build, assets):
    cache = build / 'vision-cache'
    unpack = build / 'vision-jars'
    cache.mkdir(parents=True, exist_ok=True)
    unpack.mkdir(parents=True, exist_ok=True)
    jars, native = [], []
    lock = json.loads((ROOT / 'record-android/vision-dependencies.json').read_text(encoding='utf8'))
    for item in lock['artifacts']:
        filename = item['file']
        if Path(filename).name != filename:
            raise ValueError('Invalid dependency filename')
        file = cache / filename
        if not file.exists():
            with urllib.request.urlopen(item['url'], timeout=90) as response:
                data = response.read()
            if hashlib.sha256(data).hexdigest() != item['sha256']:
                raise RuntimeError('Dependency integrity mismatch: ' + filename)
            file.write_bytes(data)
        if hashlib.sha256(file.read_bytes()).hexdigest() != item['sha256']:
            raise RuntimeError('Cached dependency integrity mismatch: ' + filename)
        if file.suffix == '.task':
            folder = assets / 'gesture'
            folder.mkdir(exist_ok=True)
            shutil.copy2(file, folder / filename)
        elif file.suffix == '.jar':
            jars.append(file)
        elif file.suffix == '.aar':
            with zipfile.ZipFile(file) as archive:
                jar = unpack / (file.stem + '.jar')
                jar.write_bytes(archive.read('classes.jar'))
                jars.append(jar)
                for notice in ('third_party_licenses.json', 'third_party_licenses.txt'):
                    if notice in archive.namelist():
                        notices = assets / 'gesture' / 'licenses'
                        notices.mkdir(parents=True, exist_ok=True)
                        (notices / (file.stem + '-' + notice)).write_bytes(archive.read(notice))
                for name in archive.namelist():
                    if name.startswith('jni/arm64-v8a/') and name.endswith('.so'):
                        native.append(('lib/' + name[4:], archive.read(name)))
    return jars, native
