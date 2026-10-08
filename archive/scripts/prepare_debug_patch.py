from pathlib import Path
import hashlib
import json

root = Path(__file__).resolve().parent.parent
original_path = root / "outputs" / "toss-front2-backup" / "smraw-original.bin"
original = original_path.read_bytes()
assert len(original) == 64 * 1024 * 1024
modified = bytearray(original)
fields = []
for offset, value in [(0x300, b"SYSDEBUGMODE=true\0"), (0x400, b"APPDEBUGMODE=true\0")]:
    assert original[offset:offset + len(value)] == bytes(len(value)), "Expected an empty debug field"
    modified[offset:offset + len(value)] = value
    fields.append({"offset_hex": hex(offset), "value": value[:-1].decode("ascii"), "nul_terminated": True})
changes = [i for i, (a, b) in enumerate(zip(original, modified)) if a != b]
assert changes == [offset + i for offset, value in [(0x300, b"SYSDEBUGMODE=true"),
                                                   (0x400, b"APPDEBUGMODE=true")]
                   for i in range(len(value))]
prepared_dir = root / "outputs" / "toss-front2-prepared"
prepared_dir.mkdir(exist_ok=True)
patched_path = prepared_dir / "smraw-debug.bin"
patched_path.write_bytes(modified)
report = {"device_written": False, "partition": "smraw", "lun": 0,
          "start_sector": 1701672, "sector_size": 4096, "size_bytes": len(original),
          "original_sha256": hashlib.sha256(original).hexdigest(),
          "patched_sha256": hashlib.sha256(modified).hexdigest(),
          "changed_bytes": len(changes), "fields": fields,
          "all_other_bytes_preserved": True,
          "source": "https://gall.dcinside.com/mgallery/board/view/?id=sff&no=1722612",
          "expected_effect": "Enable vendor debug mode and retain USB ADB after startup; verify on device after applying"}
(prepared_dir / "smraw-patch-review.json").write_text(json.dumps(report, indent=2), encoding="utf-8")
print(json.dumps(report, indent=2))
