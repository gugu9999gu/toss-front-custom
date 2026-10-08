from pathlib import Path
import concurrent.futures as cf
import datetime as dt
import json
import subprocess
import sys
import time

sys.stdout.reconfigure(encoding="utf-8")
ROOT = Path(__file__).resolve().parent
ADB = Path.home() / "AppData/Local/Android/Sdk/platform-tools/adb.exe"
STAMP = dt.datetime.now().strftime("%Y%m%d-%H%M%S")
OUT = ROOT / f"removal-boot-{STAMP}.jsonl"
START = time.monotonic()

def run(args, timeout=4):
    try:
        p = subprocess.run([str(ADB), *args], capture_output=True, text=True,
                           encoding="utf-8", errors="replace", timeout=timeout,
                           creationflags=0x08000000)
        return {"code": p.returncode, "out": p.stdout.strip(), "err": p.stderr.strip()}
    except subprocess.TimeoutExpired:
        return {"code": None, "out": "", "err": "timeout"}

def emit(event, **values):
    record = {"event": event, "time": dt.datetime.now().astimezone().isoformat(), **values}
    with OUT.open("a", encoding="utf-8") as f:
        f.write(json.dumps(record, ensure_ascii=False) + "\n")
    print(json.dumps({"event": event, **{k: v for k, v in values.items() if k != "result"}}, ensure_ascii=False), flush=True)

emit("READY", mode="read-only", path=str(OUT), duration_seconds=600)
last_state = None
captures = 0
while time.monotonic() - START < 600:
    listed = run(["devices", "-l"], timeout=2)
    state = listed["out"]
    if state != last_state:
        emit("ADB_STATE", result=listed)
        last_state = state
    targets = []
    for row in state.splitlines():
        fields = row.split()
        if len(fields) >= 2 and fields[1] == "device" and any("toss_front2" in x.lower() for x in fields[2:]):
            targets.append(fields[0])
    if len(targets) == 1:
        serial = targets[0]
        cmd = "id; getprop ro.product.model; getprop ro.product.device; getprop ro.tossplace.sysdebugmode; getprop ro.tossplace.appdebugmode; getprop persist.tossplace.operatingmode; getprop sys.usb.config; getprop sys.usb.state; getprop persist.sys.usb.config; getprop sys.boot_completed; ps -A -o NAME"
        initial = run(["-s", serial, "shell", cmd], timeout=5)
        emit("FAST_SNAPSHOT", result=initial, capture=captures + 1)
        if initial["code"] == 0:
            queries = {
                "packages": ["shell", "pm", "list", "packages", "-f", "-U"],
                "disabled": ["shell", "pm", "list", "packages", "-d"],
                "home": ["shell", "cmd", "package", "resolve-activity", "--brief", "-a", "android.intent.action.MAIN", "-c", "android.intent.category.HOME"],
                "window": ["shell", "dumpsys", "window", "windows"],
                "debug_settings": ["shell", "settings", "get", "global", "hide_toss_wartermark"],
                "mounts": ["shell", "cat", "/proc/mounts"],
            }
            with cf.ThreadPoolExecutor(max_workers=4) as pool:
                jobs = {pool.submit(run, ["-s", serial, *args], 6): name for name, args in queries.items()}
                for job in cf.as_completed(jobs):
                    emit("QUERY", name=jobs[job], result=job.result())
            captures += 1
            emit("CAPTURE_COMPLETE", capture=captures)
            time.sleep(3)
    time.sleep(0.15)
emit("WATCH_COMPLETE", captures=captures)
