#!/usr/bin/env bash
# 單機模擬器互斥鎖：本地調試 vs CI 同機開模擬器會搶 port／吃 RAM／adb 互踢。
# 用法：
#   emu-lock.sh acquire <owner> [pid]  # 拿到回 0；被活著的 owner 佔走回 1＋印出誰
#   emu-lock.sh release <owner>        # 只清自己的鎖，別人的不動
# pid 預設 $$：短命 shell（如 emulator.sh up 結束就走）必須顯式傳常駐 pid
# （如 qemu 本體），否則鎖立刻被視為殘留搶走。
# 設計：mkdir 原子性搶鎖；owner 檔記 pid＋時間，pid 死了或超過 3 小時視為殘留可搶
# （nightly job 上限 90 分鐘，3 小時一定是殘留）。
set -uo pipefail

LOCKDIR="${OMD_EMU_LOCKDIR:-/tmp/omd-emu.lock}"
STALE_SECS=10800

lock_owner() {
  cat "$LOCKDIR/owner" 2>/dev/null || echo ""
}

lock_age() {
  local now ts
  now=$(date +%s)
  ts=$(cat "$LOCKDIR/ts" 2>/dev/null || echo 0)
  echo $((now - ts))
}

lock_pid_alive() {
  local pid
  pid=$(cat "$LOCKDIR/pid" 2>/dev/null || echo "")
  [ -n "$pid" ] && kill -0 "$pid" 2>/dev/null
}

try_acquire() {
  local owner="$1" pid="$2"
  if mkdir "$LOCKDIR" 2>/dev/null; then
    echo "$owner" > "$LOCKDIR/owner"
    echo "$pid" > "$LOCKDIR/pid"
    date +%s > "$LOCKDIR/ts"
    return 0
  fi
  return 1
}

case "${1:-}" in
  acquire)
    owner="${2:-unknown}"
    pid="${3:-$$}"
    if try_acquire "$owner" "$pid"; then
      exit 0
    fi
    # 被佔：殘留（pid 死或超時）就清掉重搶一次
    if ! lock_pid_alive || [ "$(lock_age)" -gt "$STALE_SECS" ]; then
      old="$(lock_owner)"
      rm -rf "$LOCKDIR"
      if try_acquire "$owner" "$pid"; then
        echo "清掉殘留鎖（原 owner：$old）" >&2
        exit 0
      fi
    fi
    echo "模擬器被佔用中（owner：$(lock_owner)）。CI 跑時本地不要開模擬器，反之亦然。" >&2
    exit 1
    ;;
  release)
    owner="${2:-unknown}"
    if [ "$(lock_owner)" = "$owner" ]; then
      rm -rf "$LOCKDIR"
    else
      echo "不是你的鎖（owner：$(lock_owner)），不動。" >&2
    fi
    exit 0
    ;;
  refresh)
    # 開機完成後把短命 pid 換成 qemu 本體 pid；非 owner 不動。
    owner="${2:-unknown}"
    pid="${3:-$$}"
    if [ "$(lock_owner)" = "$owner" ]; then
      echo "$pid" > "$LOCKDIR/pid"
      date +%s > "$LOCKDIR/ts"
    fi
    exit 0
    ;;
  *)
    echo "用法：emu-lock.sh acquire|release <owner>" >&2
    exit 2
    ;;
esac
