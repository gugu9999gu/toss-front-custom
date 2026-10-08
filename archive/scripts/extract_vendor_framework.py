from pathlib import Path
import ast
import json
import struct

root = Path(__file__).resolve().parent.parent
source = ast.parse((root / "work/read_stock_inventory.py").read_text(encoding="utf-8"))
reader_definition = next(item for item in source.body if isinstance(item, ast.ClassDef) and item.name == "Ext4Reader")
namespace = {"struct": struct}
exec(compile(ast.Module(body=[reader_definition], type_ignores=[]), "ext4_reader", "exec"), namespace)
Ext4Reader = namespace["Ext4Reader"]
backup = root / "outputs/toss-front2-backup"
layout = json.loads((backup / "logical-partitions.json").read_text(encoding="utf-8"))
out = root / "work/vendor-framework"
out.mkdir(exist_ok=True)
report = {}
with (backup / "ufs-lun0.bin").open("rb") as raw:
    for name in ["system_a", "system_ext_a", "vendor_a", "product_a"]:
        partition = next(item for item in layout["partitions"] if item["name"] == name)
        fs = Ext4Reader(raw, partition)
        part = {}
        for path in ["framework", "system/framework", "priv-app", "system/priv-app", "app", "system/app", "etc/init", "system/etc/init"]:
            try:
                entries = fs.directory(fs.lookup(path))
            except KeyError:
                continue
            part[path] = list(entries)
            for filename, inode in entries.items():
                if filename in {"framework.jar", "services.jar"} or (filename.endswith(".jar") and any(term in filename.lower() for term in ["sunmi", "toss", "customer"])):
                    (out / (name + "-" + filename)).write_bytes(fs.file_bytes(inode, maximum=256*1024*1024))
                if filename in {"SystemUI", "SystemUI.apk"}:
                    if filename.endswith(".apk"):
                        (out / (name + "-" + filename)).write_bytes(fs.file_bytes(inode, maximum=256*1024*1024))
                    else:
                        for child, child_inode in fs.directory(inode).items():
                            if child.endswith(".apk"):
                                (out / (name + "-" + child)).write_bytes(fs.file_bytes(child_inode, maximum=256*1024*1024))
                if path.endswith("init") and any(term in filename.lower() for term in ["toss", "sunmi", "customer", "minicat"]):
                    (out / (name + "-" + filename)).write_bytes(fs.file_bytes(inode))
        report[name] = part
(out / "inventory.json").write_text(json.dumps(report, indent=2), encoding="utf-8")
print(json.dumps(report, indent=2))
print("Extracted:", [path.name for path in out.iterdir()])
