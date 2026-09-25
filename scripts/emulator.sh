#!/usr/bin/env bash
# 模擬器腳本控制入口（免 Android Studio，cmdline-tools 就是同一套 emulator）：
#   bash scripts/emulator.sh up [29|33|35]   # 開機並等到 boot 完成（預設 29）
#   bash scripts/emulator.sh down            # 關掉所有本地模擬器
#   bash scripts/emulator.sh status          # 列出裝置
# AVD 家目錄收在 .tools/.android（不污染家目錄）。
# 前提：SETUP_EMULATOR=1 pnpm setup 裝好 emulator + 映像。
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
export ANDROID_AVD_HOME="$ROOT/.tools/.android/avd"
export ANDROID_SDK_HOME="$ROOT/.tools/.android"
export ANDROID_HOME="$ROOT/.tools/android-sdk"
export PATH="$ANDROID_HOME/emulator:$ANDROID_HOME/platform-tools:$PATH"

API="${2:-29}"
AVD="omd-$API"

case "${1:-status}" in
  up)
    # CI 跑時本地不開機（同機互踩血淚史，見 emu-lock.sh）。
    # 以 qemu 本體 pid 佔鎖（up 腳本自己會先退，用 $$ 沒意義）。
    EMU_PID="$(pgrep -f "qemu.*-avd $AVD" | head -n 1 || true)"
    if [ -z "$EMU_PID" ]; then
      EMU_PID="$$"
    fi
    bash "$(dirname "${BASH_SOURCE[0]}")/emu-lock.sh" acquire "local" "$EMU_PID" || exit 1
    if ! adb devices | grep -q "emulator-"; then
      setsid emulator -avd "$AVD" -no-window -no-audio -no-boot-anim \
        -memory 3072 -gpu swiftshader_indirect -no-snapshot \
        < /dev/null > /tmp/opencode-emu-$AVD.log 2>&1 &
      disown || true
    fi
    adb wait-for-device
    for _ in $(seq 1 60); do
      if [ "$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = "1" ]; then
        adb devices
        # 開完把鎖換到 qemu 本體 pid（up 腳本退了鎖才不會變殘留）。
        QEMU_PID="$(pgrep -f "qemu.*-avd $AVD" | head -n 1 || true)"
        if [ -n "$QEMU_PID" ]; then
          bash "$(dirname "${BASH_SOURCE[0]}")/emu-lock.sh" refresh "local" "$QEMU_PID"
        fi
        exit 0
      fi
      sleep 5
    done
    echo "error: $AVD 5 分鐘內沒開完，看 /tmp/opencode-emu-$AVD.log" >&2
    exit 1
    ;;
  down)
    adb devices | grep -oE "emulator-[0-9]+" | while read -r d; do
      adb -s "$d" emu kill || true
    done
    pkill -f "qemu-system.*$AVD" || true
    bash "$(dirname "${BASH_SOURCE[0]}")/emu-lock.sh" release "local" || true
    echo "emulators down"
    ;;
  *)
    adb devices
    ;;
esac
