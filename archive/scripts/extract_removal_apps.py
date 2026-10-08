from pathlib import Path
import ast
import json
import struct

root = Path(__file__).resolve().parent.parent
source = ast.parse((root / "work/read_stock_inventory.py").read_text(encoding="utf-8"))
definition = next(x for x in source.body if isinstance(x, ast.ClassDef) and x.name == "Ext4Reader")
namespace = {"struct": struct}
exec(compile(ast.Module(body=[definition], type_ignores=[]), "reader", "exec"), namespace)
backup = root / "outputs/toss-front2-backup"
layout = json.loads((backup / "logical-partitions.json").read_text(encoding="utf-8"))
out = root / "work/vendor-framework"
with (backup / "ufs-lun0.bin").open("rb") as raw:
    for name in ["system_a", "system_ext_a", "vendor_a"]:
        part = next(x for x in layout["partitions"] if x["name"] == name)
        fs = namespace["Ext4Reader"](raw, part)
        paths = []
        if name == "system_a":
            paths.extend(["system/framework/framework-res.apk", "system/bin/auxtoolservice"])
        if name == "system_ext_a":
            for filename in fs.directory(fs.lookup("priv-app")):
                if any(s in filename for s in ["MinicatManager", "SunmiWelcome", "EventLogger", "SpUpdater", "TossplaceHardwareService", "app-tossplace-appcenter"]):
                    for child in fs.directory(fs.lookup("priv-app/" + filename)):
                        if child.endswith(".apk"):
                            paths.append("priv-app/" + filename + "/" + child)
        for directory in ["etc/init", "system/etc/init", "etc/init/hw", "system/etc/init/hw"]:
            try:
                entries = fs.directory(fs.lookup(directory))
            except KeyError:
                continue
            for filename in entries:
                if any(s in filename for s in ["usb", "spupdater", "spbridge"]) or filename == "init.rc":
                    paths.append(directory + "/" + filename)
        for path in paths:
            target = out / (name + "-" + Path(path).name)
            if target.exists():
                continue
            try:
                data = fs.file_bytes(fs.lookup(path), maximum=256*1024*1024)
            except KeyError:
                continue
            target.write_bytes(data)
            print(json.dumps({"path": str(target), "bytes": len(data)}))
