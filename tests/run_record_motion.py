from pathlib import Path
import subprocess

root = Path(__file__).resolve().parents[1]
output = root / "build/tests/record-motion"
output.mkdir(parents=True, exist_ok=True)
source = root / "record-android/src/dev/tossfront/record"
subprocess.run(["javac", "--release", "8", "-d", str(output), str(source / "RecordMotion.java"), str(source / "SpectrumData.java"), str(root / "tests/RecordMotionTest.java")], check=True)
subprocess.run(["java", "-cp", str(output), "dev.tossfront.record.RecordMotionTest"], check=True)
