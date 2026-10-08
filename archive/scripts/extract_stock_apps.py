from pathlib import Path
import contextlib
import io
import json
import re
import runpy
import subprocess

root = Path(__file__).resolve().parent.parent
with contextlib.redirect_stdout(io.StringIO()):
    namespace = runpy.run_path(str(root / "work" / "read_stock_inventory.py"))
output_dir = root / "work" / "stock-apps"
output_dir.mkdir(exist_ok=True)
partition = next(item for item in namespace["layout"]["partitions"] if item["name"] == "system_ext_a")
tool = Path(r"C:\Users\YOUR_USER\AppData\Local\Android\Sdk\build-tools\36.0.0\aapt.exe")
prefixes = ("Launcher3QuickStep", "Settings", "SunmiWelcome_", "app-device-management_release_",
            "app-tossplace_release_", "app-tossplace-installer_release_", "DeviceSettings-release-")
report = []
with namespace["raw_path"].open("rb") as raw:
    fs = namespace["Ext4Reader"](raw, partition)
    directories = fs.directory(fs.lookup("priv-app"))
    for name, number in directories.items():
        if not name.startswith(prefixes):
            continue
        for filename, inode in fs.directory(number).items():
            if not filename.endswith(".apk"):
                continue
            apk = output_dir / (name + ".apk")
            apk.write_bytes(fs.file_bytes(inode, maximum=256 * 1024 * 1024))
            badging = subprocess.check_output([str(tool), "dump", "badging", str(apk)], text=True, encoding="utf-8")
            manifest = subprocess.check_output([str(tool), "dump", "xmltree", str(apk), "AndroidManifest.xml"],
                                               text=True, encoding="utf-8")
            (output_dir / (name + "-manifest.txt")).write_text(manifest, encoding="utf-8")
            report.append({"source_directory": name,
                           "package": re.search(r"package: name='([^']+)'", badging).group(1),
                           "launchable_activity": (re.search(r"launchable-activity: name='([^']+)'", badging).group(1)
                                                   if "launchable-activity:" in badging else None),
                           "declares_home_category": "android.intent.category.HOME" in manifest,
                           "declares_boot_receiver": "android.intent.action.BOOT_COMPLETED" in manifest,
                           "declares_device_admin_receiver": "android.app.device_admin" in manifest})
(root / "outputs" / "toss-front2-backup" / "stock-app-metadata.json").write_text(
    json.dumps(report, indent=2), encoding="utf-8")
print(json.dumps(report, indent=2))
