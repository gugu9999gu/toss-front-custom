from pathlib import Path
import json
import struct
import zlib

root = Path(__file__).resolve().parent.parent
backup_dir = root / "outputs" / "toss-front2-backup"
scratch_dir = root / "work" / "empty-gpt-probes"
scratch_dir.mkdir(exist_ok=True)
# The old client's r-gpt branch fails to increment its output filename index.
if (backup_dir / "gpt-head.bin").exists():
    assert (backup_dir / "gpt-head.bin").open("rb").read(8) == b"ANDROID!"
    assert (backup_dir / "boot_a.bin").open("rb").read(8) == b"VNDRBOOT"
    assert not (backup_dir / "vendor_boot_a.bin").exists()
    (backup_dir / "boot_a.bin").rename(backup_dir / "vendor_boot_a.bin")
    (backup_dir / "gpt-head.bin").rename(backup_dir / "boot_a.bin")
for path in backup_dir.glob("gpt-head.bin.lun*"):
    if path.stat().st_size == 0:
        path.rename(scratch_dir / path.name)

luns = []
partitions = []
for lun in range(6):
    data = (backup_dir / f"gpt-head.bin.lun{lun}").read_bytes()
    header = data[4096:8192]
    header_size, header_crc = struct.unpack_from("<II", header, 12)
    checked_header = bytearray(header[:header_size])
    struct.pack_into("<I", checked_header, 16, 0)
    backup_lba = struct.unpack_from("<Q", header, 32)[0]
    entries_lba, count, entry_size, entries_crc = struct.unpack_from("<QIII", header, 72)
    table = data[entries_lba * 4096:entries_lba * 4096 + count * entry_size]
    assert header[:8] == b"EFI PART", f"GPT signature failed on LUN {lun}"
    assert zlib.crc32(checked_header) == header_crc, f"GPT header CRC failed on LUN {lun}"
    assert zlib.crc32(table) == entries_crc, f"GPT entries CRC failed on LUN {lun}"
    luns.append({"lun": lun, "sector_size": 4096, "sectors": backup_lba + 1,
                 "size_bytes": (backup_lba + 1) * 4096,
                 "gpt_header_crc_valid": True, "gpt_entries_crc_valid": True})
    for i in range(count):
        entry = table[i * entry_size:(i + 1) * entry_size]
        name = entry[56:128].decode("utf-16-le", errors="replace").rstrip("\0")
        if name:
            start, end = struct.unpack_from("<QQ", entry, 32)
            partitions.append({"lun": lun, "name": name, "start_sector": start,
                               "sectors": end - start + 1,
                               "size_bytes": (end - start + 1) * 4096})
layout = {"luns": luns, "partitions": partitions}
(backup_dir / "storage-layout.json").write_text(json.dumps(layout, indent=2), encoding="utf-8")
print(json.dumps({"luns": luns, "total_bytes": sum(lun["size_bytes"] for lun in luns)}, indent=2))
with (backup_dir / "smraw-original.bin").open("rb") as f:
    first_page = f.read(4096)
print("Debug fields:", first_page[0x300:0x330].split(b"\0")[0],
      first_page[0x400:0x430].split(b"\0")[0])
