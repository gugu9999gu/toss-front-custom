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
    parser.add_argument("--app", choices=("deck", "audio", "record", "youtubeweb"), default="deck")
    args = parser.parse_args()
    tools, android = args.sdk / "build-tools" / args.build_tools, args.sdk / "platforms" / args.platform / "android.jar"
    if not android.is_file(): raise SystemExit("Android SDK platform is missing")
    configurations = {
        "deck": ("FrontDeck", "dev.tossfront.deck", "frontdeck", "android", "android", "1.0.0", "24"),
        "audio": ("FrontAudio", "dev.tossfront.audio", "frontaudio", "audio-android", "audio", "1.0.0", "26"),
        "record": ("FrontRecord", "dev.tossfront.record", "frontrecord", "record-android", "record", "1.6.0", "26"),
        "youtubeweb": ("YouTube-Web", "local.tossfront.youtubeweb", "youtubeweb", "youtube-web-android", "youtubeweb", "2.3", "26"),
    }
    name, package, alias, source_name, build_name, version, minimum = configurations[args.app]
    source = ROOT / source_name
    build = ROOT / "build" / build_name
    output = ROOT / "dist" / (name + "-" + version + ".apk")
    classes, assets = build / "classes", build / "assets"
    for folder in (classes, assets):
        target = folder.resolve()
        if target != folder or not target.is_relative_to(ROOT):
            raise RuntimeError("Build cleanup target resolves outside its expected workspace path")
        if folder.exists(): shutil.rmtree(target)
        folder.mkdir(parents=True)
    output.parent.mkdir(exist_ok=True)
    if args.app == "deck":
        for item in (ROOT / "frontdeck/ui").iterdir():
            if item.is_file(): shutil.copy2(item, assets / item.name)
    if (source / "assets").is_dir():
        shutil.copytree(source / "assets", assets, dirs_exist_ok=True)
    dependencies, native = [], []
    if args.app == "record":
        from record_vision import prepare
        dependencies, native = prepare(build, assets)
    classpath = os.pathsep.join(str(p) for p in [android, *dependencies])
    run(["javac", "--release", "8", "-encoding", "UTF-8", "-classpath", classpath, "-d", classes, *source.rglob("*.java")])
    jar = build / "classes.jar"
    with zipfile.ZipFile(jar, "w") as archive:
        for item in classes.rglob("*.class"): archive.write(item, item.relative_to(classes).as_posix())
    for dex in build.glob("classes*.dex"):
        if not dex.resolve().is_relative_to(build.resolve()):
            raise RuntimeError("DEX cleanup target resolves outside its build directory")
        dex.unlink()
    run(["java", "-cp", tools / "lib/d8.jar", "com.android.tools.r8.D8", "--min-api", minimum, "--lib", android, "--output", build, jar, *dependencies])
    suffix = ".exe" if os.name == "nt" else ""
    unsigned, aligned = build / "unsigned.apk", build / "aligned.apk"
    run([tools / ("aapt" + suffix), "package", "-f", "-M", source / "AndroidManifest.xml", "-S", source / "res", "-A", assets, "-I", android, "-F", unsigned])
    with zipfile.ZipFile(unsigned, "a", compression=zipfile.ZIP_DEFLATED) as apk:
        for dex in build.glob('classes*.dex'): apk.write(dex, dex.name)
        for filename, data in native: apk.writestr(filename, data)
    run([tools / ("zipalign" + suffix), "-f", "4", unsigned, aligned])
    keystore, password = build / (alias + ".keystore"), build / "keystore-password.txt"
    if keystore.exists() != password.exists(): raise RuntimeError("Restore both signing files together before building")
    if not keystore.exists():
        password.write_text(secrets.token_urlsafe(32), encoding="utf-8"); password.chmod(0o600)
        run(["keytool", "-genkeypair", "-keystore", keystore, "-alias", alias, "-storepass:file", password, "-keypass:file", password,
             "-keyalg", "RSA", "-keysize", "2048", "-validity", "3650", "-dname", "CN=" + name + " local development"])
    signer = ["java", "-jar", tools / "lib/apksigner.jar"]
    run([*signer, "sign", "--ks", keystore, "--ks-key-alias", alias, "--ks-pass", "file:" + str(password), "--out", output, aligned])
    signature = run([*signer, "verify", "--verbose", "--print-certs", output])
    badging = run([tools / ("aapt" + suffix), "dump", "badging", output])
    report = {"package": package, "sha256": hashlib.sha256(output.read_bytes()).hexdigest(), "bytes": output.stat().st_size,
              "signature": signature, "badging": badging}
    (build / "verification.json").write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")
    print(json.dumps({"apk": str(output), "sha256": report["sha256"], "bytes": report["bytes"]}, ensure_ascii=False))

if __name__ == "__main__": main()
