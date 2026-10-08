"""Run the pinned EDL client with only partition-read commands enabled."""
from pathlib import Path
import runpy
import sys

root = Path(__file__).resolve().parent
allowed = {"printgpt", "gpt", "r", "rl", "rf", "rs", "getstorageinfo"}
if len(sys.argv) < 2 or sys.argv[1] not in allowed:
    raise SystemExit("Only EDL read commands are allowed: " + ", ".join(sorted(allowed)))
if any(arg.startswith(("--vid", "--pid", "--loader")) for arg in sys.argv[2:]):
    raise SystemExit("USB identifiers and loader are fixed for this device preparation.")
repo = root / "edl"
sys.path.insert(0, str(repo))
sys.stdout.reconfigure(encoding="utf-8")
sys.argv = [str(repo / "edl.py"), *sys.argv[1:],
            "--vid=05c6", "--pid=9008", "--memory=ufs",
            "--loader=" + str(root / "firehose.elf")]
runpy.run_path(str(repo / "edl.py"), run_name="__main__")
