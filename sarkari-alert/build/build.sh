#!/bin/bash
# Sarkari Alert — full APK build without Android SDK/Gradle.
# Toolchain (see tools.sh): aapt2 (npm aaptjs3), ECJ (liferay jar), dx (AOSP source,
# self-compiled), android.jar (Sable/android-platforms), JRE (pypi jdk4py).
set -e
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
source "$ROOT/build/tools.sh"
APP="$ROOT/app"
OUT="$ROOT/build/out"
rm -rf "$OUT"
mkdir -p "$OUT/classes" "$OUT/gen"

echo "[1/5] aapt2 compile resources"
"$AAPT2" compile --dir "$APP/res" -o "$OUT/res.zip"

echo "[2/5] aapt2 link -> base.apk + R.java"
"$AAPT2" link -o "$OUT/base.apk" -I "$ANDROID_JAR" \
    --manifest "$APP/AndroidManifest.xml" \
    --java "$OUT/gen" -A "$APP/assets" --auto-add-overlay \
    "$OUT/res.zip"

echo "[3/5] ECJ compile java"
find "$APP/src" "$OUT/gen" -name "*.java" | sort > "$OUT/sources.txt"
"$JAVA" -jar "$ECJ" -source 1.7 -target 1.7 -nowarn -encoding UTF-8 \
    -bootclasspath "$ANDROID_JAR" \
    -d "$OUT/classes" @"$OUT/sources.txt"

echo "[4/5] dx -> classes.dex"
(cd "$OUT/classes" && zip -qr "$OUT/classes.jar" .)
"$JAVA" -cp "$DX_JAR" com.android.dx.command.Main --dex \
    --min-sdk-version=24 --output="$OUT/classes.dex" "$OUT/classes.jar"

echo "[5/5] package + sign v2"
PY=/tmp/jenv/bin/python
if [ ! -f "$KEY_DIR/release.key.pem" ]; then
    "$PY" "$ROOT/build/package_sign.py" genkey "$KEY_DIR"
fi
"$PY" "$ROOT/build/package_sign.py" pack "$OUT/base.apk" "$OUT/classes.dex" \
    "$KEY_DIR/release.key.pem" "$KEY_DIR/release.cert.der" "$OUT/SarkariAlert.apk"
"$PY" "$ROOT/build/package_sign.py" verify "$OUT/SarkariAlert.apk"
"$AAPT2" dump badging "$OUT/SarkariAlert.apk" | head -12
echo "DONE: $OUT/SarkariAlert.apk"
