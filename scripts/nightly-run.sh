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
# 上輪 `emu kill` 沒殺掉的孤兒 qemu（adb 斷線但行程還在：佔 port／吃 RAM，
# 還會讓 runner 收尾等行程結束等到天荒地老——2026-09-25 API 29 實測）。
# 只動 `-avd test`（CI 專用名，本地調試的 omd-* 絕不碰）；且只殺「adb 已看不到
# ＋活超過 5 分鐘」的（剛開機的健康模擬器 adbd 還沒上來，不能誤殺）。
for q in $(pgrep -f "qemu-system.*-avd test" 2>/dev/null || true); do
  age="$(ps -o etimes= -p "$q" 2>/dev/null | tr -d ' ' || echo 0)"
  if [ "${age:-0}" -gt 300 ] && ! adb devices 2>/dev/null | grep -q "emulator-"; then
    echo "收孤兒 qemu（pid $q，已活 ${age}s 但 adb 看不到）"
    kill -9 "$q" || true
  fi
done
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

echo "== settle: 等開機風暴過去（dexopt＋首屏 WebView 冷啟動會把 Launcher 餓到 ANR，蓋住後面所有 flow） =="
for _ in $(seq 1 24); do
  [ "$(adb shell getprop dev.bootcomplete 2>/dev/null | tr -d '\r')" = "1" ] && break
  sleep 5
done < /dev/null
# Launcher 行程起來＋靜置 20 秒再開測；ANR 對話框由 e2e.sh 的 sweep 處理。
for _ in $(seq 1 24); do
  adb shell "pidof com.google.android.apps.nexuslauncher || pidof com.android.launcher3" < /dev/null 2>/dev/null | grep -q "[0-9]" && break
  sleep 5
done < /dev/null
sleep 20

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
