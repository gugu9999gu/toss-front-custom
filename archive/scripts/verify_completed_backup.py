"""Cross-check independently captured partitions against the completed UFS image."""
from pathlib import Path
from datetime import datetime
import hashlib
import json

root = Path(__file__).resolve().parent.parent
backup = root / "outputs" / "toss-front2-backup"
manifest = json.loads((backup / "backup-manifest.json").read_text(encoding="utf-8"))
assert manifest["complete"] is True
assert {item["lun"] for item in manifest["images"]} == set(range(6))
assert manifest["total_bytes"] == 31977373696
for item in manifest["images"]:
    assert (backup / item["filename"]).stat().st_size == item["bytes"]
    assert item["primary_gpt_verified"] and item["backup_gpt_verified"] and item["read_acknowledged"]

layout = json.loads((backup / "storage-layout.json").read_text(encoding="utf-8"))
checks = []
for name, filename in [("smraw", "smraw-original.bin"), ("boot_a", "boot_a.bin"),
                       ("vendor_boot_a", "vendor_boot_a.bin")]:
    part = next(part for part in layout["partitions"] if part["name"] == name)
    lun = next(item for item in layout["luns"] if item["lun"] == part["lun"])
    separate = backup / filename
    digest = hashlib.sha256()
    with (backup / f"ufs-lun{lun['lun']}.bin").open("rb") as full, separate.open("rb") as earlier:
        full.seek(part["start_sector"] * 4096)
        while block := earlier.read(4 * 1024 * 1024):
            assert full.read(len(block)) == block, f"Partition differs: {name}"
            digest.update(block)
    checks.append({"partition": name, "filename": filename, "bytes": separate.stat().st_size,
                   "sha256": digest.hexdigest(), "matches_full_ufs_image": True})

report = {"checked_at": datetime.now().astimezone().isoformat(), "full_backup_complete": True,
          "lun_count": 6, "total_bytes": manifest["total_bytes"], "partition_cross_checks": checks,
          "device_storage_written": False}
(backup / "backup-verification.json").write_text(json.dumps(report, indent=2), encoding="utf-8")
status_path = root / "outputs" / "toss-front2-preparation.json"
status = json.loads(status_path.read_text(encoding="utf-8"))
status.update({"updated_at": report["checked_at"], "edl_driver_current": "WinUSB; device status OK",
               "backup_completed": True, "backup_bytes": manifest["total_bytes"],
               "backup_manifest": "toss-front2-backup/backup-manifest.json",
               "device_partitions_modified": False,
               "next_required_step": "Apply the reviewed 34-byte smraw debug flag change, then inspect persistent ADB"})
status["loader"]["compatibility_with_connected_device_verified"] = True
status_path.write_text(json.dumps(status, indent=2), encoding="utf-8")
print(json.dumps(report, indent=2))
