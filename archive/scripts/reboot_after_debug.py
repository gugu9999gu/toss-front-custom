from pathlib import Path
from datetime import datetime
import hashlib
import json
import runpy
import sys

root = Path(__file__).resolve().parent.parent
prepared = root / "outputs" / "toss-front2-prepared"
before = (root / "work" / "smraw-live-before.bin").read_bytes()
after = (root / "work" / "smraw-live-after.bin").read_bytes()
assert before == (prepared / "smraw-original-first-sector.bin").read_bytes()
assert after == (prepared / "smraw-debug-first-sector.bin").read_bytes()
assert len(after) == 4096 and sum(a != b for a, b in zip(before, after)) == 34
timestamp = datetime.now().astimezone().isoformat()
record = {"applied_at": timestamp, "partition": "smraw", "lun": 0, "start_sector": 1701672,
          "bytes_written": 4096, "changed_bytes": 34, "exact_readback_verified": True,
          "original_sector_sha256": hashlib.sha256(before).hexdigest(),
          "applied_sector_sha256": hashlib.sha256(after).hexdigest(), "userdata_erased": False}
(prepared / "debug-apply-result.json").write_text(json.dumps(record, indent=2), encoding="utf-8")
for filename in ["smraw-patch-review.json", "smraw-sector-write-plan.json"]:
    path = prepared / filename
    data = json.loads(path.read_text(encoding="utf-8"))
    data.update({"device_written": True, "applied_at": timestamp, "exact_readback_verified": True})
    path.write_text(json.dumps(data, indent=2), encoding="utf-8")
status_path = root / "outputs" / "toss-front2-preparation.json"
status = json.loads(status_path.read_text(encoding="utf-8"))
status.update({"updated_at": timestamp, "device_partitions_modified": True,
               "modified_partition": "smraw", "changed_bytes": 34, "userdata_erased": False,
               "next_required_step": "Reboot and verify persistent ADB before configuring the existing Android home"})
status_path.write_text(json.dumps(status, indent=2), encoding="utf-8")
repo = root / "work" / "edl"
sys.path.insert(0, str(repo))
sys.stdout.reconfigure(encoding="utf-8")
sys.argv = [str(repo / "edl.py"), "reset", "--vid=05c6", "--pid=9008",
            "--loader=" + str(root / "work" / "firehose.elf")]
runpy.run_path(str(repo / "edl.py"), run_name="__main__")
