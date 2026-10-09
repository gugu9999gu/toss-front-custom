from pathlib import Path
import subprocess,tempfile
root=Path(__file__).resolve().parents[1]
temporary=root/'build/gesture-tests';temporary.mkdir(parents=True,exist_ok=True)
with tempfile.TemporaryDirectory(dir=temporary) as output:
    subprocess.run(['javac','--release','8','-encoding','UTF-8','-d',output,*[str(root/p) for p in ['record-android/src/dev/tossfront/record/HandGestureCore.java','record-android/src/dev/tossfront/record/GestureInfluence.java','record-android/src/dev/tossfront/record/PinchTrail.java','record-android/src/dev/tossfront/record/CameraPixels.java','record-android/src/dev/tossfront/record/HandMotionTracker.java','tests/RecordGestureTest.java','tests/PinchTrailTest.java','tests/CameraPixelsTest.java','tests/HandMotionTrackerTest.java']]],check=True)
    subprocess.run(['java','-cp',output,'dev.tossfront.record.RecordGestureTest'],check=True)
    subprocess.run(['java','-cp',output,'dev.tossfront.record.CameraPixelsTest'],check=True)
    subprocess.run(['java','-cp',output,'dev.tossfront.record.PinchTrailTest'],check=True)
    subprocess.run(['java','-cp',output,'dev.tossfront.record.HandMotionTrackerTest'],check=True)
    subprocess.run(['javac','--release','8','-encoding','UTF-8','-d',output,str(root/'record-android/src/dev/tossfront/record/GestureVolume.java'),str(root/'tests/GestureVolumeTest.java')],check=True)
    subprocess.run(['java','-cp',output,'dev.tossfront.record.GestureVolumeTest'],check=True)
    subprocess.run(['javac','--release','8','-encoding','UTF-8','-classpath',output,'-d',output,str(root/'record-android/src/dev/tossfront/record/HandDisplay.java'),str(root/'tests/HandDisplayTest.java')],check=True)
    subprocess.run(['java','-cp',output,'dev.tossfront.record.HandDisplayTest'],check=True)
    subprocess.run(['javac','--release','8','-encoding','UTF-8','-d',output,str(root/'record-android/src/dev/tossfront/record/HandDetectionSchedule.java'),str(root/'tests/HandDetectionScheduleTest.java')],check=True)
    subprocess.run(['java','-cp',output,'dev.tossfront.record.HandDetectionScheduleTest'],check=True)
