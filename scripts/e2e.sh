#!/usr/bin/env bash
# E2E 包裝：清場 → 跑 Maestro 全流 → 斷言 MediaStore 真有檔案。
# flow 只斷言 UI 文字；這裡斷言位元組真的落地（存在＋>100KB＋恰好一份；
# 多一份就代表重複下單，是 regression）。
#
# 實作注意：`content ... --where LIKE '%...'` 的 % 在這條鏈會被吃掉，
# 一律全表列出後本地過濾、用 _id 精確刪除。
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
# 本地 .tools 優先；CI 沒有 .tools 就認 runner 的 ANDROID_HOME。
if [ -d "$ROOT/.tools/android-sdk/platform-tools" ]; then
  export ANDROID_AVD_HOME="$ROOT/.tools/.android/avd"
  export ANDROID_SDK_HOME="$ROOT/.tools/.android"
  export ANDROID_HOME="$ROOT/.tools/android-sdk"
  export PATH="$ANDROID_HOME/platform-tools:$HOME/.maestro/bin:$PATH"
else
  export PATH="$HOME/.maestro/bin:$PATH"
fi

URI="content://media/external_primary/downloads"

# 清場：我們的測試檔家族（子字串比對）。只清這些，不碰使用者檔案。
WIPE_FAMILIES=(
  "SoundHelix-Song-1"
  "Big_Buck_Bunny_360_10s_2MB"
  "Big_Buck_Bunny_360_10s_1MB"
  "Big_Buck_Bunny_720_10s_30MB"
  "Big Buck Bunny 60fps"
  "不同类型的人表白被拒后的不同回复"
)
# 斷言：family|副檔名，每組跑完恰好一份。
# 檔名規則（Downloader.toCleanName）：成品一律乾淨標題，dl- 只留快取中間檔；
# 改命名規則時同步改這裡。
EXPECT=(
  "SoundHelix-Song-1|.mp3"
  "Big_Buck_Bunny_360_10s_2MB|.mp4"
  "Big Buck Bunny 60fps|.mp4"
  "Big Buck Bunny 60fps|.mp3"
  "不同类型的人表白被拒后的不同回复|.mp4"
)
MIN_SIZE=100000

mc_list() {
  # adb shell 會吃掉迴圈的 stdin，一律 < /dev/null（否則 wipe 刪一筆就 EOF）。
  # 輸出是 CRLF：$ 錨點會被 \r 打掉，先 tr 清掉（review F3）。
  adb shell 'content query --uri '"$URI"' --projection _id:_display_name:_size' < /dev/null | tr -d '\r'
}

mc_delete_id() {
  # _id 是整數欄，不加引號（加了會型別對不上、靜默刪 0 筆）。
  adb shell 'content delete --uri '"$URI"' --where _id='"$1" < /dev/null > /dev/null
}

echo "== e2e 清場 =="
# App 必須先裝好（connected 跑完有時會把主 APK 卸掉；沒裝就秒死，不要燒 5 分鐘 timeout 才發現）。
if ! adb shell pm list packages 2>/dev/null | grep -q "package:com.openmedia.downloader$"; then
  echo "FAIL: com.openmedia.downloader 未安裝，先裝 APK 再跑"
  exit 1
fi
while IFS= read -r row; do
  id="$(echo "$row" | grep -oE "_id=[0-9]+" | cut -d= -f2 || true)"
  name="$(echo "$row" | sed 's/.*_display_name=//; s/, _size.*//' || true)"
  if [ -z "${id:-}" ]; then
    continue
  fi
  for fam in "${WIPE_FAMILIES[@]}"; do
    if [[ "$name" == *"$fam"* ]]; then
      mc_delete_id "$id" || true
      break
    fi
  done
done < <(mc_list | grep "^Row" || true)
echo "清場後殘留：$(mc_list | grep -cE "SoundHelix|Big_Buck_Bunny|Big Buck Bunny" || true)（應為 0）"

echo "== maestro test e2e/ =="
maestro test "$ROOT/e2e/"

echo "== 斷言檔案落地 =="
fail=0
LIST="$(mc_list)"
for spec in "${EXPECT[@]}"; do
  fam="${spec%%|*}"
  ext="${spec##*|}"
  hits="$(echo "$LIST" | grep -F "$fam" | grep -F "$ext" || true)"
  n="$(echo "$hits" | grep -c "^Row" || true)"
  if [ "$n" -ne 1 ]; then
    echo "FAIL: [$fam*$ext] 預期恰好 1 檔，實際 $n 檔"
    echo "$hits" || true
    fail=1
    continue
  fi
  echo "OK: $hits"
  size="$(echo "$hits" | grep -oE "_size=[0-9]+" | cut -d= -f2)"
  if [ -z "$size" ] || [ "$size" -lt "$MIN_SIZE" ]; then
    echo "FAIL: [$fam*$ext] 檔案過小（${size:-未知} < $MIN_SIZE）"
    fail=1
  fi
done
if [ "$fail" -ne 0 ]; then
  echo "e2e 檔案斷言失敗"
  exit 1
fi
echo "e2e 全過（含檔案落地斷言）"
