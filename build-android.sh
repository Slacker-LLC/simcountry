#!/usr/bin/env bash
# Build SimCountry without Gradle: javac + d8 + aapt2 + zipalign + apksigner.
#
#   export ANDROID_HOME=$HOME/Android/Sdk
#   ./build-android.sh
#
# BT / AJ override the SDK locations. On an aarch64 host the x86_64 aapt2 and
# zipalign from the official build-tools are run through qemu-user-static.
set -euo pipefail

ROOT=$(cd "$(dirname "$0")" && pwd)
BT=${BT:-"$ANDROID_HOME/build-tools/34.0.0"}
AJ=${AJ:-"$ANDROID_HOME/platforms/android-36/android.jar"}
API="$ROOT/libs/api-82.jar"
OUT="$ROOT/out"

for f in "$BT/aapt2" "$BT/d8" "$BT/zipalign" "$BT/apksigner" "$AJ" "$API"; do
  [ -e "$f" ] || { echo "missing: $f" >&2; exit 1; }
done

# build-tools ships x86_64 binaries only. Wrap through qemu when needed.
wrap() {
  if "$1" version >/dev/null 2>&1 || "$1" -h >/dev/null 2>&1; then
    printf '%s\n' "$1"
    return
  fi
  local emu=${QEMU:-qemu-x86_64-static}
  local emu_bin=${emu%% *}
  command -v "$emu_bin" >/dev/null 2>&1 || {
    echo "cannot run $1 on this host; install qemu-user-static or set QEMU" >&2
    exit 1
  }
  printf '%s %s\n' "$emu" "$1"
}
AAPT2=$(wrap "$BT/aapt2")
ZIPALIGN=$(wrap "$BT/zipalign")

rm -rf "$OUT"
mkdir -p "$OUT/classes" "$OUT/dex"

echo "== aapt2 compile =="
rm -rf "$OUT/res"
mkdir -p "$OUT/res"
$AAPT2 compile --dir "$ROOT/res" -o "$OUT/res"
RES_ARGS=""
for flat in "$OUT/res"/*.flat; do
  RES_ARGS="$RES_ARGS -R $flat"
done

echo "== aapt2 link (also emits R.java) =="
mkdir -p "$OUT/gen"
$AAPT2 link -o "$OUT/base.apk" -I "$AJ" \
  --manifest "$ROOT/AndroidManifest.xml" -A "$ROOT/assets" $RES_ARGS \
  --auto-add-overlay --java "$OUT/gen" \
  --min-sdk-version 27 --target-sdk-version 34 \
  --version-code 2 --version-name 1.1.0

echo "== javac =="
# shellcheck disable=SC2046
javac -source 8 -target 8 -nowarn -bootclasspath "$AJ" -cp "$API" \
  -d "$OUT/classes" $(find "$ROOT/src" "$OUT/gen" -name '*.java')

echo "== d8 =="
# shellcheck disable=SC2046
"$BT/d8" --lib "$AJ" --min-api 27 --output "$OUT/dex" \
  $(find "$OUT/classes" -name '*.class')

echo "== pack dex =="
cd "$OUT"
python3 - <<'PY'
import shutil, zipfile
shutil.copy('base.apk', 'unsigned.apk')
z = zipfile.ZipFile('unsigned.apk', 'a', zipfile.ZIP_DEFLATED)
z.write('dex/classes.dex', 'classes.dex')
z.close()
PY

echo "== align =="
$ZIPALIGN -f -p 4 "$OUT/unsigned.apk" "$OUT/aligned.apk"

KS="$ROOT/release.keystore"
if [ ! -f "$KS" ]; then
  echo "no release.keystore, generating a throwaway one"
  keytool -genkeypair -keystore "$KS" -alias simcountry -keyalg RSA -keysize 2048 \
    -validity 10000 -storepass android -keypass android -dname "CN=simcountry, O=local"
fi

echo "== sign =="
"$BT/apksigner" sign --ks "$KS" --ks-pass pass:android --key-pass pass:android \
  --ks-key-alias simcountry --out "$ROOT/SimCountry.apk" "$OUT/aligned.apk"

ls -l "$ROOT/SimCountry.apk"
