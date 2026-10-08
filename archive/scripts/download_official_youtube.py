from pathlib import Path
import datetime as dt
import hashlib
import json
import sys
import time
import urllib.request

sys.stdout.reconfigure(encoding="utf-8")
root = Path(__file__).resolve().parent.parent
plan_path = root / "work/official-youtube-plan.json"
plan = json.loads(plan_path.read_text(encoding="utf-8"))
plan["apkmirror_permission"] = True
plan["google_components_authorized"] = True
plan["permission_confirmed_at"] = dt.datetime.now().astimezone().isoformat()
plan["status"] = "download_in_progress"
plan_path.write_text(json.dumps(plan, ensure_ascii=False, indent=2), encoding="utf-8")
url = "https://www.apkmirror.com/wp-content/themes/APKMirror/download.php?id=16466060&key=60a29f16fec74435e469e2b35e2050bae47e8baf&forcebaseapk=true"
request = urllib.request.Request(url, headers={"User-Agent": "Mozilla/5.0", "Referer": "https://www.apkmirror.com/apk/google-inc/youtube/youtube-21-39-524-release/youtube-21-39-524-android-apk-download/"})
target = root / "work/YouTube-official-21.39.524.apk"
partial = target.with_suffix(".apk.part")
assert not target.exists() and not partial.exists()
size = 0
last = time.monotonic()
hasher = hashlib.sha256()
with urllib.request.urlopen(request, timeout=30) as response:
    print(json.dumps({"status": response.status, "type": response.headers.get("Content-Type"), "length": response.headers.get("Content-Length")}), flush=True)
    assert response.status == 200
    assert "html" not in response.headers.get("Content-Type", "").lower()
    with partial.open("wb") as f:
        while chunk := response.read(1024*1024):
            if size == 0:
                assert chunk.startswith(b"PK\x03\x04"), "The response is not an APK ZIP."
            f.write(chunk)
            hasher.update(chunk)
            size += len(chunk)
            if time.monotonic() - last > 10:
                print(json.dumps({"bytes_downloaded": size}), flush=True)
                last = time.monotonic()
assert size == 204622029
assert hasher.hexdigest() == "f47bf195f52c78bbb922ae6c40b882c27436f635eea346be9ad944afaf063d81"
partial.rename(target)
plan["status"] = "downloaded_pending_signature_verification"
plan["downloaded_apk"] = str(target)
plan_path.write_text(json.dumps(plan, ensure_ascii=False, indent=2), encoding="utf-8")
print(json.dumps({"path": str(target), "bytes": size, "sha256": hasher.hexdigest()}), flush=True)
