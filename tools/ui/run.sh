#!/bin/bash
set -x
PKG=com.artykulacja.app
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell pm grant $PKG android.permission.RECORD_AUDIO
adb shell pm grant $PKG android.permission.POST_NOTIFICATIONS
adb shell am start -n $PKG/.ui.MainActivity
sleep 4
adb shell am force-stop $PKG
adb shell run-as $PKG mkdir -p files/models
for f in kokoro.onnx phoneme.onnx voices.bin voices.json phoneme_vocab.json; do
  adb push models/$f /data/local/tmp/$f
  adb shell "cat /data/local/tmp/$f | run-as $PKG sh -c 'cat > files/models/$f'"
  adb shell "echo 1 | run-as $PKG sh -c 'cat > files/models/$f.ok'"
  adb shell rm /data/local/tmp/$f
done
adb shell "echo 1 | run-as $PKG sh -c 'cat > files/models/.ready'"
adb shell "echo 1 | run-as $PKG sh -c 'cat > files/debug_fake_mic'"
adb shell run-as $PKG ls -la files/models
adb logcat -c
python3 tools/ui/tour.py
adb logcat -d > shots/logcat.txt
grep -E "FATAL|AndroidRuntime|Exception" shots/logcat.txt | head -80 > shots/errors.txt
cat shots/errors.txt
