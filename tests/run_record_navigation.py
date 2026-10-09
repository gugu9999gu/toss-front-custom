from pathlib import Path
import subprocess,tempfile
root=Path(__file__).resolve().parents[1]
src=root/'record-android/src/dev/tossfront/record'
with tempfile.TemporaryDirectory() as output:
    subprocess.run(['javac','--release','8','-encoding','UTF-8','-d',output,str(src/'LibraryCore.java'),str(src/'HistoryQueue.java'),str(root/'tests/RecordNavigationTest.java')],check=True)
    subprocess.run(['java','-cp',output,'dev.tossfront.record.RecordNavigationTest'],check=True)
