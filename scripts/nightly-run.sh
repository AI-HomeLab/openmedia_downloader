#!/usr/bin/env bash
# Nightly 單 API 執行體。CI workflow 只調這一行——action 會把多行 script
# 拆開逐行餵給 sh -c 跑，if/fi 多行寫法直接 Syntax error（2026-09-24 實測），
# 所以邏輯一律收進本檔。
# 用法：bash scripts/nightly-run.sh <api> <connected_filter> <run_e2e true|false>
set -u

API="$1"
FILTER="${2:-}"
RUN_E2E="${3:-false}"

have_ct=0
have_et=0

echo "== preflight: device & network =="
adb wait-for-device
adb shell getprop sys.boot_completed
adb shell getprop ro.build.version.sdk
echo "--- date (TLS 靠它) ---"
adb shell date -u || true
echo "--- ping 外網 ---"
adb shell ping -c 2 -W 8 8.8.8.8 || true
echo "--- DNS (toybox nslookup 若有) ---"
adb shell nslookup youtube.com 2>&1 | head -n 4 || true

echo "== connected tests =="
# shellcheck disable=SC2086
if bash scripts/gradle.sh connectedDebugAndroidTest $FILTER \
  -x :capacitor-cordova-android-plugins:connectedDebugAndroidTest; then
  have_ct=1
fi

if [ "$RUN_E2E" = "true" ]; then
  echo "== assemble + install + e2e =="
  if bash scripts/gradle.sh assembleDebug \
    && adb install -r android/app/build/outputs/apk/debug/app-debug.apk \
    && curl -fsSL "https://get.maestro.mobile.dev" | bash; then
    export PATH="$HOME/.maestro/bin:$PATH"
    if bash scripts/e2e.sh; then
      have_et=1
    fi
  fi
else
  have_et=1
fi

echo "== device logcat (always) =="
adb logcat -d > "/tmp/logcat-api-${API}.txt" || true

if [ "$have_ct" -ne 1 ]; then
  echo "CONNECTED FAILED"
  exit 1
fi
if [ "$have_et" -ne 1 ]; then
  echo "E2E FAILED"
  exit 1
fi
echo "NIGHTLY API $API GREEN"
