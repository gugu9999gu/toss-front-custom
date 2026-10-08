"""Read all six UFS LUNs and validate each saved image before calling it complete."""
from pathlib import Path
import hashlib
import json
import shutil
import struct
import subprocess
import sys
import zlib

root = Path(__file__).resolve().parent.parent
backup_dir = root / "outputs" / "toss-front2-backup"
layout = json.loads((backup_dir / "storage-layout.json").read_text(encoding="utf-8"))
assert shutil.disk_usage(backup_dir).free > sum(lun["size_bytes"] for lun in layout["luns"]) + 5 * 1024**3
manifest_path = backup_dir / "backup-manifest.json"
manifest = {"complete": False, "device_storage_written": False, "images": []}
manifest_path.write_text(json.dumps(manifest, indent=2), encoding="utf-8")

for number in [4, 1, 2, 3, 5, 0]:
    lun = next(item for item in layout["luns"] if item["lun"] == number)
    partial = backup_dir / f"ufs-lun{number}.bin.partial"
    final = backup_dir / f"ufs-lun{number}.bin"
    assert not partial.exists() and not final.exists(), "Refusing to overwrite a previous backup"
    log_path = root / "work" / f"edl-full-backup-lun{number}.log"
    print(json.dumps({"phase": "reading", "lun": number, "expected_bytes": lun["size_bytes"]}), flush=True)
    with log_path.open("w", encoding="utf-8") as log:
        result = subprocess.run([sys.executable, "-u", str(root / "work" / "edl_readonly.py"),
                                 "rs", "0", str(lun["sectors"]), str(partial), f"--lun={number}"],
                                cwd=root, stdout=log, stderr=subprocess.STDOUT)
    log_text = log_path.read_text(encoding="utf-8", errors="replace")
    assert result.returncode == 0 and "Traceback" not in log_text, f"Read failed on LUN {number}"
    assert f"Dumped sector 0 with sector count {lun['sectors']}" in log_text, f"No successful read acknowledgement for LUN {number}"
    assert partial.stat().st_size == lun["size_bytes"], f"Incomplete LUN {number}"
    print(json.dumps({"phase": "verifying", "lun": number}), flush=True)
    digest = hashlib.sha256()
    with partial.open("rb") as image:
        head = image.read(131072)
        assert head == (backup_dir / f"gpt-head.bin.lun{number}").read_bytes(), "Primary GPT snapshot differs"
        image.seek(-4096, 2)
        header = image.read(4096)
        assert header[:8] == b"EFI PART", "Backup GPT is missing"
        header_size, header_crc = struct.unpack_from("<II", header, 12)
        checked = bytearray(header[:header_size])
        struct.pack_into("<I", checked, 16, 0)
        assert zlib.crc32(checked) == header_crc, "Backup GPT header CRC failed"
        entry_lba, count, entry_size, entries_crc = struct.unpack_from("<QIII", header, 72)
        image.seek(entry_lba * 4096)
        assert zlib.crc32(image.read(count * entry_size)) == entries_crc, "Backup GPT entries CRC failed"
        image.seek(0)
        while block := image.read(16 * 1024 * 1024):
            digest.update(block)
    partial.rename(final)
    manifest["images"].append({"lun": number, "filename": final.name, "bytes": lun["size_bytes"],
                                "sha256": digest.hexdigest(), "primary_gpt_verified": True,
                                "backup_gpt_verified": True, "read_acknowledged": True})
    manifest_path.write_text(json.dumps(manifest, indent=2), encoding="utf-8")
    print(json.dumps({"phase": "complete", "lun": number, "bytes": lun["size_bytes"]}), flush=True)

manifest["complete"] = True
manifest["total_bytes"] = sum(image["bytes"] for image in manifest["images"])
manifest_path.write_text(json.dumps(manifest, indent=2), encoding="utf-8")
print(json.dumps({"phase": "all_complete", "bytes": manifest["total_bytes"]}), flush=True)
