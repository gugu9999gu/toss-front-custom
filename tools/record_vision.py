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
    for bundle in lock.get('model_bundles', []):
        filename = bundle['file']
        if Path(filename).name != filename or not filename.endswith('.task'):
            raise ValueError('Invalid model bundle filename')
        reference_name = bundle.get('metadata_source')
        if reference_name and Path(reference_name).name != reference_name:
            raise ValueError('Invalid metadata reference filename')
        folder = assets / 'gesture'
        folder.mkdir(exist_ok=True)
        target = folder / filename
        with zipfile.ZipFile(target, 'w', compression=zipfile.ZIP_STORED) as archive:
            for name, source in bundle['models'].items():
                if Path(name).name != name or Path(source).name != source:
                    raise ValueError('Invalid model bundle entry')
                data = (cache / source).read_bytes()
                if data[4:8] != b'TFL3':
                    raise ValueError('Invalid TFLite model')
                if bundle.get('metadata_source'):
                    from record_vision_models import lite_with_metadata
                    with zipfile.ZipFile(cache / bundle['metadata_source']) as reference:
                        data = lite_with_metadata(data, reference.read(name))
                    if hashlib.sha256(data).hexdigest() != bundle['models_sha256'][name]:
                        raise RuntimeError('Derived lite model integrity mismatch: ' + name)
                entry = zipfile.ZipInfo(name, date_time=(1980, 1, 1, 0, 0, 0))
                entry.external_attr = 0o644 << 16
                archive.writestr(entry, data)
        if hashlib.sha256(target.read_bytes()).hexdigest() != bundle['sha256']:
            raise RuntimeError('Model bundle integrity mismatch: ' + filename)
    return jars, native
