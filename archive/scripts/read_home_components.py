from pathlib import Path
import json
import re

root = Path(__file__).resolve().parent.parent
path = root / "work" / "stock-apps" / "Launcher3QuickStep-manifest.txt"
components = []
block = []
for line in path.read_text(encoding="utf-8").splitlines() + ["      E: end"]:
    if re.match(r"^      E: ", line):
        if any("android.intent.category.HOME" in attribute for attribute in block):
            names = [re.search(r'android:name.*="([^"]+)"', attribute).group(1)
                     for attribute in block if attribute.startswith("        A: android:name")]
            components.append({"component": names[0] if names else None, "home": True,
                               "attributes": [attribute.strip() for attribute in block
                                              if attribute.startswith("        A:")
                                              and any(key in attribute for key in
                                                      ["android:enabled", "android:exported", "android:permission"])]})
        block = []
    else:
        block.append(line)
report = {"package": "com.android.launcher3", "components": components}
(root / "outputs" / "toss-front2-prepared" / "stock-home-components.json").write_text(
    json.dumps(report, indent=2), encoding="utf-8")
print(json.dumps(report, indent=2))
