"""Verify a downloaded APK before installation. Does not download or install anything."""
from pathlib import Path
import hashlib
import json
import re
import subprocess
import sys
import zipfile

sys.stdout.reconfigure(encoding="utf-8")
apk = Path(sys.argv[1]).resolve()
expected_package = sys.argv[2]
expected_cert_sha256 = sys.argv[3].lower()
sdk = Path.home() / "AppData/Local/Android/Sdk"
tools = sdk / "build-tools/36.0.0"
assert zipfile.is_zipfile(apk), "Not an APK/ZIP file"

def run(args):
    result = subprocess.run([str(item) for item in args], capture_output=True, timeout=60)
    assert result.returncode == 0, result.stderr.decode("utf-8", errors="replace")
    return result.stdout.decode("utf-8", errors="replace")

signature = run(["java", "-jar", tools / "lib/apksigner.jar", "verify", "--verbose", "--print-certs", apk])
certificates = re.findall(r"^Signer #\d+ certificate SHA-256 digest: ([0-9a-f]+)", signature, re.MULTILINE)
assert expected_cert_sha256 in certificates, "Signing certificate mismatch"
manifest = run([tools / "aapt.exe", "dump", "badging", apk])
package = re.search(r"package: name='([^']+)'", manifest).group(1)
assert package == expected_package, "Package name mismatch"
minimum = int(re.search(r"sdkVersion:'(\d+)'", manifest).group(1))
assert minimum <= 33, "Requires newer Android than this device"
report = {"apk": str(apk), "package": package, "bytes": apk.stat().st_size,
          "sha256": hashlib.sha256(apk.read_bytes()).hexdigest(),
          "certificates_sha256": certificates, "minimum_sdk": minimum,
          "signature_verification": signature, "manifest": manifest}
apk.with_suffix(".verification.json").write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")
print(json.dumps({key: report[key] for key in ["apk", "package", "bytes", "sha256", "certificates_sha256", "minimum_sdk"]}, indent=2))
