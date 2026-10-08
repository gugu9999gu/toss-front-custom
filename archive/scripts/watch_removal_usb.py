import datetime as dt
import json
from pathlib import Path
import sys
import time
from watch_usb import scan, safe_device

sys.stdout.reconfigure(encoding="utf-8")
out = Path(__file__).resolve().parent / ("removal-usb-" + dt.datetime.now().strftime("%Y%m%d-%H%M%S") + ".jsonl")
start = time.monotonic()
previous = {}
def emit(event, **values):
    record = {"event": event, "time": dt.datetime.now().astimezone().isoformat(), **values}
    line = json.dumps(record, ensure_ascii=False)
    with out.open("a", encoding="utf-8") as f:
        f.write(line + "\n")
    print(line, flush=True)
emit("READY", path=str(out), duration_seconds=600)
while time.monotonic() - start < 600:
    tick = time.monotonic()
    current = {k: v for k, v in scan().items() if "VID_05C6" in k or "VID_18D1" in k or v.get("problem_code")}
    for key in current.keys() - previous.keys():
        emit("ADDED", device=safe_device(current[key]))
    for key in previous.keys() - current.keys():
        emit("REMOVED", device=safe_device(previous[key]))
    for key in current.keys() & previous.keys():
        if current[key] != previous[key]:
            emit("CHANGED", device=safe_device(current[key]))
    previous = current
    time.sleep(max(0, 0.05 - (time.monotonic() - tick)))
emit("FINISHED")
