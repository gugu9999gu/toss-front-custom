"""Build the small Java Android client with an installed Android SDK and JDK."""
from pathlib import Path
import argparse
import hashlib
import json
import os
import secrets
import shutil
import subprocess
import zipfile

ROOT = Path(__file__).resolve().parents[1]

def run(args):
    result = subprocess.run([str(a) for a in args], capture_output=True, text=True,
                            encoding="utf-8", errors="replace", timeout=120)
    if result.returncode:
        raise RuntimeError(result.stdout + result.stderr)
    return result.stdout + result.stderr

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--sdk", type=Path, default=Path(os.environ.get("ANDROID_HOME", Path.home() / "AppData/Local/Android/Sdk")))
    parser.add_argument("--build-tools", default="36.0.0")
    parser.add_argument("--platform", default="android-33")
    args = parser.parse_args()
    tools, android = args.sdk / "build-tools" / args.build_tools, args.sdk / "platforms" / args.platform / "android.jar"
    if not android.is_file(): raise SystemExit("Android SDK platform is missing")
    source, build, output = ROOT / "android", ROOT / "build/android", ROOT / "dist/FrontDeck-1.0.0.apk"
    classes, assets = build / "classes", build / "assets"
    for folder in (classes, assets):
        target = folder.resolve()
        if target != folder or not target.is_relative_to(ROOT):
            raise RuntimeError("Build cleanup target resolves outside its expected workspace path")
        if folder.exists(): shutil.rmtree(target)
        folder.mkdir(parents=True)
    output.parent.mkdir(exist_ok=True)
    for item in (ROOT / "frontdeck/ui").iterdir():
        if item.is_file(): shutil.copy2(item, assets / item.name)
    run(["javac", "--release", "8", "-encoding", "UTF-8", "-classpath", android, "-d", classes, *source.rglob("*.java")])
    jar = build / "classes.jar"
    with zipfile.ZipFile(jar, "w") as archive:
        for item in classes.rglob("*.class"): archive.write(item, item.relative_to(classes).as_posix())
    run(["java", "-cp", tools / "lib/d8.jar", "com.android.tools.r8.D8", "--min-api", "24", "--lib", android, "--output", build, jar])
    suffix = ".exe" if os.name == "nt" else ""
    unsigned, aligned = build / "unsigned.apk", build / "aligned.apk"
    run([tools / ("aapt" + suffix), "package", "-f", "-M", source / "AndroidManifest.xml", "-S", source / "res", "-A", assets, "-I", android, "-F", unsigned])
    with zipfile.ZipFile(unsigned, "a", compression=zipfile.ZIP_DEFLATED) as apk: apk.write(build / "classes.dex", "classes.dex")
    run([tools / ("zipalign" + suffix), "-f", "4", unsigned, aligned])
    keystore, password = build / "frontdeck.keystore", build / "keystore-password.txt"
    if keystore.exists() != password.exists(): raise RuntimeError("Restore both signing files together before building")
    if not keystore.exists():
        password.write_text(secrets.token_urlsafe(32), encoding="utf-8"); password.chmod(0o600)
        run(["keytool", "-genkeypair", "-keystore", keystore, "-alias", "frontdeck", "-storepass:file", password, "-keypass:file", password,
             "-keyalg", "RSA", "-keysize", "2048", "-validity", "3650", "-dname", "CN=FrontDeck local development"])
    signer = ["java", "-jar", tools / "lib/apksigner.jar"]
    run([*signer, "sign", "--ks", keystore, "--ks-key-alias", "frontdeck", "--ks-pass", "file:" + str(password), "--out", output, aligned])
    signature = run([*signer, "verify", "--verbose", "--print-certs", output])
    badging = run([tools / ("aapt" + suffix), "dump", "badging", output])
    report = {"package": "dev.tossfront.deck", "sha256": hashlib.sha256(output.read_bytes()).hexdigest(), "bytes": output.stat().st_size,
              "signature": signature, "badging": badging}
    (build / "verification.json").write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")
    print(json.dumps({"apk": str(output), "sha256": report["sha256"], "bytes": report["bytes"]}, ensure_ascii=False))

if __name__ == "__main__": main()
