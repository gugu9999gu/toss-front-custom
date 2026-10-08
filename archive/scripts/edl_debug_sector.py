"""Program only the reviewed first smraw sector; no arbitrary EDL writes."""
from pathlib import Path
import hashlib
import json
import runpy
import sys

root = Path(__file__).resolve().parent.parent
backup = root / "outputs" / "toss-front2-backup"
prepared = root / "outputs" / "toss-front2-prepared"
assert sys.argv[1:] == ["--apply-reviewed-sector"]
manifest = json.loads((backup / "backup-manifest.json").read_text(encoding="utf-8"))
assert manifest["complete"] and manifest["total_bytes"] == 31977373696
assert {item["lun"] for item in manifest["images"]} == set(range(6))
assert all((backup / item["filename"]).stat().st_size == item["bytes"] for item in manifest["images"])
original = (prepared / "smraw-original-first-sector.bin").read_bytes()
sector_file = prepared / "smraw-debug-first-sector.bin"
expected = bytearray(original)
for offset, value in [(0x300, b"SYSDEBUGMODE=true\0"), (0x400, b"APPDEBUGMODE=true\0")]:
    expected[offset:offset + len(value)] = value
assert len(original) == len(expected) == 4096
assert sector_file.read_bytes() == expected
assert (root / "work" / "smraw-live-before.bin").read_bytes() == original
assert sum(a != b for a, b in zip(original, expected)) == 34
assert hashlib.sha256((root / "work" / "firehose.elf").read_bytes()).hexdigest() == "5f37d7a49275728f4d58c8b972e1e273efad9178e6c49e20cac49114a0b7a217"

repo = root / "work" / "edl"
sys.path.insert(0, str(repo))
from edl.Library.firehose import firehose
from edl.Library.Modules.init import modules

program = firehose.cmd_program
calls = []
def guarded_program(self, physical_partition_number, start_sector, filename, display=True):
    assert not calls, "Only one sector program command is allowed"
    assert physical_partition_number == 0 and start_sector == 1701672
    assert self.cfg.SECTOR_SIZE_IN_BYTES == 4096
    assert Path(filename).resolve() == sector_file.resolve()
    assert Path(filename).read_bytes() == expected
    calls.append(True)
    result = program(self, physical_partition_number, start_sector, filename, display)
    assert result is True, "Firehose rejected the sector write"
    return result

firehose.cmd_program = guarded_program
modules.writeprepare = lambda self: True
modules.addprogram = lambda self: ""
sys.stdout.reconfigure(encoding="utf-8")
sys.argv = [str(repo / "edl.py"), "ws", "1701672", str(sector_file), "--lun=0",
            "--vid=05c6", "--pid=9008", "--memory=ufs", "--loader=" + str(root / "work" / "firehose.elf")]
runpy.run_path(str(repo / "edl.py"), run_name="__main__")
assert len(calls) == 1
