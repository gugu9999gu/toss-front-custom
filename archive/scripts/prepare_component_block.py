"""Inspect the installed manifests and record component-level restore states."""
from pathlib import Path
from datetime import datetime
import collections
import json
import re
import shlex
import subprocess
import xml.etree.ElementTree as ET
import zipfile

root = Path(__file__).resolve().parent.parent
prepared = root / "outputs" / "toss-front2-prepared"
adb = r"C:\Users\YOUR_USER\AppData\Local\Android\Sdk\platform-tools\adb.exe"
aapt = r"C:\Users\YOUR_USER\AppData\Local\Android\Sdk\build-tools\36.0.0\aapt.exe"
changes = json.loads((prepared / "stock-home-change.json").read_text(encoding="utf-8"))
target_packages = changes["disabled_packages"]
assert len(target_packages) == 9 and all(p.startswith("com.tossplace.") for p in target_packages)
manifest_dir = root / "work" / "installed-manifests"
manifest_dir.mkdir(exist_ok=True)
raw = subprocess.check_output([adb, "exec-out", "abx2xml", "/data/system/users/0/package-restrictions.xml", "-"], timeout=15)
state = ET.fromstring(raw)
items = {pkg.attrib["name"]: pkg for pkg in state.findall("pkg")}
plan = {"prepared_at": datetime.now().astimezone().isoformat(), "user": 0, "packages": [],
        "device_components_modified": False}
for package in target_packages:
    output = subprocess.check_output([adb, "shell", "pm", "path", package], text=True,
                                     encoding="utf-8", errors="replace", timeout=10)
    paths = [line.removeprefix("package:") for line in output.splitlines() if line.startswith("package:")]
    assert paths and all(path.endswith(".apk") and path.startswith(("/data/app/", "/system_ext/", "/system/", "/product/")) for path in paths)
    components = {}
    for number, path in enumerate(paths):
        binary_xml = subprocess.check_output([adb, "exec-out", shlex.join(["unzip", "-p", path, "AndroidManifest.xml"])], timeout=15)
        assert binary_xml[:4] == b"\x03\x00\x08\x00"
        stub = manifest_dir / f"{package}-{number}.apk"
        with zipfile.ZipFile(stub, "w") as archive:
            archive.writestr("AndroidManifest.xml", binary_xml)
        xml = subprocess.check_output([aapt, "dump", "xmltree", str(stub), "AndroidManifest.xml"],
                                      text=True, encoding="utf-8", errors="replace", timeout=10)
        (manifest_dir / f"{package}-{number}.txt").write_text(xml, encoding="utf-8")
        assert re.search(r'package="' + re.escape(package) + '"', xml)
        active = None
        application_indent = None
        element_count = named_count = 0
        unresolved = []
        active_name = None
        for line in xml.splitlines():
            element = re.match(r"(\s*)E: (\S+)", line)
            if element:
                indent, tag = len(element.group(1)), element.group(2)
                if application_indent is not None and indent <= application_indent:
                    application_indent = None
                if tag == "application":
                    application_indent = indent
                if active and indent <= active[0]:
                    if active_name is None:
                        unresolved.append(active)
                    active = None
                if application_indent is not None and tag in {"activity", "activity-alias", "receiver", "service", "provider"}:
                    active = (indent, tag)
                    active_name = None
                    element_count += 1
            attribute = re.match(r'(\s*)A: [^(]*\(0x01010003\)="([^"]+)"', line)
            if active and attribute and len(attribute.group(1)) == active[0] + 2:
                name = attribute.group(2)
                if name.startswith("."):
                    name = package + name
                elif "." not in name:
                    name = package + "." + name
                components[name] = active[1]
                active_name = name
                named_count += 1
        assert element_count == named_count, f"Manifest name counts differ: {package}: elements={element_count}, names={named_count}, missing={unresolved}"
    assert components, f"No components found: {package}"
    current = items[package]
    explicitly_enabled = {node.attrib["name"] for node in current.findall("enabled-components/item")}
    explicitly_disabled = {node.attrib["name"] for node in current.findall("disabled-components/item")}
    package_record = {"package": package, "components": [{"class": name, "type": kind,
                       "original_state": 1 if name in explicitly_enabled else 2 if name in explicitly_disabled else 0}
                      for name, kind in sorted(components.items())]}
    plan["packages"].append(package_record)
    print(json.dumps({"package": package, "components": len(components),
                      "types": dict(collections.Counter(components.values()))}), flush=True)
plan["component_count"] = sum(len(p["components"]) for p in plan["packages"])
(prepared / "component-block-plan.json").write_text(json.dumps(plan, indent=2), encoding="utf-8")
print(json.dumps({"total_components": plan["component_count"]}), flush=True)
