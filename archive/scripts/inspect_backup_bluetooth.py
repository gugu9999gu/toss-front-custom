"""Inspect Bluetooth configuration in the existing read-only firmware backup."""
from pathlib import Path
from datetime import datetime
import ast
import json
import struct

root = Path(__file__).resolve().parent.parent
source = ast.parse((root / "work/read_stock_inventory.py").read_text(encoding="utf-8"))
reader_definition = next(item for item in source.body if isinstance(item, ast.ClassDef) and item.name == "Ext4Reader")
namespace = {"struct": struct}
exec(compile(ast.Module(body=[reader_definition], type_ignores=[]), "ext4_reader", "exec"), namespace)
Ext4Reader = namespace["Ext4Reader"]
backup = root / "outputs/toss-front2-backup"
layout = json.loads((backup / "logical-partitions.json").read_text(encoding="utf-8"))
report = {
    "checked_at": datetime.now().astimezone().isoformat(),
    "basis": "Original firmware backup, excludes virtual A/B overlays; not a live Bluetooth test",
    "partitions": {},
}
with (backup / "ufs-lun0.bin").open("rb") as raw:
    for name in ["system_a", "system_ext_a", "vendor_a", "product_a"]:
        partition = next(item for item in layout["partitions"] if item["name"] == name)
        fs = Ext4Reader(raw, partition)
        evidence = {"properties": {}, "files": {}}
        for path in ["build.prop", "system/build.prop", "etc/build.prop"]:
            try:
                contents = fs.file_bytes(fs.lookup(path)).decode("utf-8", errors="replace")
            except KeyError:
                continue
            evidence["properties"][path] = [line for line in contents.splitlines() if any(term in line.lower() for term in ["bluetooth", "a2dp", "avrcp"])]
        for path in ["etc/permissions", "system/etc/permissions", "etc/bluetooth", "system/etc/bluetooth"]:
            try:
                entries = fs.directory(fs.lookup(path))
            except KeyError:
                continue
            for filename, inode in entries.items():
                if "bluetooth" not in filename.lower() and "bluetooth" not in path:
                    continue
                try:
                    contents = fs.file_bytes(inode).decode("utf-8", errors="replace")
                except (AssertionError, ValueError):
                    continue
                evidence["files"][path + "/" + filename] = contents
        report["partitions"][name] = evidence
out = root / "work/bluetooth-backup-evidence.json"
out.write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")
for partition, evidence in report["partitions"].items():
    print(partition)
    print(json.dumps(evidence["properties"], ensure_ascii=True))
    for path, contents in evidence["files"].items():
        print(path)
        print("\n".join(line for line in contents.splitlines() if any(term in line.lower() for term in ["feature", "a2dp", "avrcp", "profile", "enabled"])))
