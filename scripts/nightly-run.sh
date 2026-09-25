#!/usr/bin/env bash
# Nightly 單 API 執行體。CI workflow 只調這一行——action 會把多行 script
# 拆開逐行餵給 sh -c 跑，if/fi 多行寫法直接 Syntax error（2026-09-24 實測），
# 所以邏輯一律收進本檔。
# 用法：bash scripts/nightly-run.sh <api> <connected_filter> <run_e2e true|false>
set -u

API="$1"
FILTER="${2:-}"
RUN_E2E="${3:-false}"

# 整輪佔住模擬器：有人（本地調試）先佔就直接失敗，不硬跑互踩。
OWNER="ci-${GITHUB_RUN_ID:-manual}"
bash "$(dirname "$0")/emu-lock.sh" acquire "$OWNER" || exit 1
trap 'bash "$(dirname "$0")/emu-lock.sh" release "$OWNER"' EXIT

have_ct=0
have_et=0

echo "== preflight: device & network =="
adb wait-for-device
# 同機若已有別台模擬器（本地調試）會搶 port／吃 RAM，直接報錯不硬跑。
if [ "$(adb devices | grep -c emulator)" -gt 1 ]; then
  echo "已有其他 emulator 在跑，先關掉再跑 CI（見 AGENTS 多開警告）"
  adb devices
  exit 1
fi
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
  # Maestro 裝過就跳過（~/.maestro 持久，不重複下載傷磁碟）。
  if [ ! -x "$HOME/.maestro/bin/maestro" ]; then
    curl -fsSL "https://get.maestro.mobile.dev" | bash
  fi
  if bash scripts/gradle.sh assembleDebug \
    && adb install -r android/app/build/outputs/apk/debug/app-debug.apk; then
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
