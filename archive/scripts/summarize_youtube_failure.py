from pathlib import Path
import json
import re
import sys

root = Path(__file__).resolve().parent.parent
phase = sys.argv[1] if len(sys.argv) > 1 else "playback-progress"
path = root / ("outputs/toss-front2-prepared/official-youtube-" + phase + ".json")
r = json.loads(path.read_text(encoding="utf-8"))
lines = r["logs"]["out"].splitlines()
matches = []
for line in lines:
    if r["pid"]["out"] in line and re.search(r"error|exception|decoder|403|404|timeout", line, re.I):
        if "http" not in line.lower() and len(line) < 800:
            matches.append(line)
print("YouTube errors, with URL-bearing lines omitted:")
print("\n".join(matches[-45:]))
print("Relevant audio lines:")
print("\n".join(x for x in r["audio"]["out"].splitlines() if "10128" in x or r["pid"]["out"] in x)[-2000:])
