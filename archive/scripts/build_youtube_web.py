from pathlib import Path
import hashlib
import json
import secrets
import subprocess
import zipfile

root = Path(__file__).resolve().parent.parent
source = root / "work/youtube-web"
build = source / "build"
build.mkdir(exist_ok=True)
classes = build / "classes"
classes.mkdir(exist_ok=True)
sdk = Path.home() / "AppData/Local/Android/Sdk"
tools = sdk / "build-tools/36.0.0"
android = sdk / "platforms/android-33/android.jar"
output = root / "outputs/toss-front2-prepared/YouTube-Web-1.0.apk"

def run(args):
    result = subprocess.run([str(arg) for arg in args], capture_output=True, text=True,
                            encoding="utf-8", errors="replace", timeout=60)
    if result.returncode:
        raise RuntimeError(result.stdout + result.stderr)
    return result.stdout + result.stderr

run(["javac", "--release", "8", "-encoding", "UTF-8", "-classpath", android, "-d", classes,
     source / "src/local/tossfront/youtubeweb/YouTubeWebActivity.java"])
class_jar = build / "classes.jar"
with zipfile.ZipFile(class_jar, "w") as jar:
    for path in classes.rglob("*.class"):
        jar.write(path, path.relative_to(classes).as_posix())
run(["java", "-cp", tools / "lib/d8.jar", "com.android.tools.r8.D8", "--min-api", "24",
     "--lib", android, "--output", build, class_jar])
unsigned = build / "unsigned.apk"
run([tools / "aapt.exe", "package", "-f", "-M", source / "AndroidManifest.xml", "-S", source / "res",
     "-I", android, "-F", unsigned])
with zipfile.ZipFile(unsigned, "a", compression=zipfile.ZIP_DEFLATED) as apk:
    apk.write(build / "classes.dex", "classes.dex")
aligned = build / "aligned.apk"
run([tools / "zipalign.exe", "-f", "4", unsigned, aligned])
keystore = build / "youtube-web.keystore"
password_file = build / "keystore-password.txt"
if not keystore.exists():
    password_file.write_text(secrets.token_urlsafe(32), encoding="utf-8")
    run(["keytool", "-genkeypair", "-keystore", keystore, "-alias", "youtubeweb",
         "-storepass:file", password_file, "-keypass:file", password_file,
         "-keyalg", "RSA", "-keysize", "2048", "-validity", "3650",
         "-dname", "CN=Local YouTube Web Launcher"])
run(["java", "-jar", tools / "lib/apksigner.jar", "sign", "--ks", keystore,
     "--ks-key-alias", "youtubeweb", "--ks-pass", "file:" + str(password_file),
     "--out", output, aligned])
verification = run(["java", "-jar", tools / "lib/apksigner.jar", "verify", "--verbose", "--print-certs", output])
manifest = run([tools / "aapt.exe", "dump", "badging", output])
report = {"apk": str(output), "bytes": output.stat().st_size,
          "sha256": hashlib.sha256(output.read_bytes()).hexdigest(),
          "package": "local.tossfront.youtubeweb", "official_youtube_app": False,
          "website": "https://m.youtube.com/", "signature_verification": verification,
          "manifest": manifest}
(build / "verification.json").write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")
print(json.dumps({key: report[key] for key in ["apk", "bytes", "sha256", "package"]}, ensure_ascii=True))
print(verification)
print(manifest)
