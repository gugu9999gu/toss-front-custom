from pathlib import Path
import subprocess, tempfile

root=Path(__file__).resolve().parents[1]
src=root/'record-android/src/dev/tossfront/record'
with tempfile.TemporaryDirectory() as output:
    subprocess.run(['javac','--release','8','-encoding','UTF-8','-d',output,
                    str(src/'TimerPlan.java'),str(src/'ResponseMath.java'),str(src/'SpectrumData.java'),
                    str(root/'tests/RecordFeaturesTest.java')],check=True)
    subprocess.run(['java','-cp',output,'dev.tossfront.record.RecordFeaturesTest'],check=True)
