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
# "test video" 一條同時覆蓋 Shorts 成品和 "Bilibili test video"（子字串）。
WIPE_FAMILIES=(
  "SoundHelix-Song-1"
  "Big_Buck_Bunny_360_10s_2MB"
  "Big_Buck_Bunny_360_10s_1MB"
  "Big_Buck_Bunny_720_10s_30MB"
  "test video"
)
# 斷言：family|副檔名|最小位元組，每組跑完恰好一份。
# 檔名規則（Downloader.toCleanName）：成品一律乾淨標題，dl- 只留快取中間檔；
# 改命名規則時同步改這裡。
# 比對是精確檔名（_display_name 全等）：自家短片都叫 test video，
# 子字串會把 Shorts 和 B 站成品混在一起數。
# 門檻：3 秒短片只有幾十 KB（實測 Shorts mp4 25KB／mp3 13KB／B 站 8KB），
# 檔頭有效性由 connected 的 ftyp 斷言覆蓋，這裡只擋空檔和重複下單。
EXPECT=(
  "SoundHelix-Song-1|.mp3|100000"
  "Big_Buck_Bunny_360_10s_2MB|.mp4|100000"
  "test video|.mp4|15000"
  "test video|.mp3|8000"
  "Bilibili test video|.mp4|5000"
)

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
echo "清場後殘留：$(mc_list | grep -cE "SoundHelix|Big_Buck_Bunny|test video" || true)（應為 0）"

echo "== maestro test e2e/ =="
maestro test "$ROOT/e2e/"

echo "== 斷言檔案落地 =="
fail=0
LIST="$(mc_list)"
for spec in "${EXPECT[@]}"; do
  fam="${spec%%|*}"
  rest="${spec#*|}"
  ext="${rest%%|*}"
  min="${rest##*|}"
  hits="$(echo "$LIST" | grep -F "_display_name=${fam}${ext}," || true)"
  n="$(echo "$hits" | grep -c "^Row" || true)"
  if [ "$n" -ne 1 ]; then
    echo "FAIL: [$fam*$ext] 預期恰好 1 檔，實際 $n 檔"
    echo "$hits" || true
    fail=1
    continue
  fi
  echo "OK: $hits"
  size="$(echo "$hits" | grep -oE "_size=[0-9]+" | cut -d= -f2)"
  if [ -z "$size" ] || [ "$size" -lt "$min" ]; then
    echo "FAIL: [$fam*$ext] 檔案過小（${size:-未知} < $min）"
    fail=1
  fi
done
if [ "$fail" -ne 0 ]; then
  echo "e2e 檔案斷言失敗"
  exit 1
fi
echo "e2e 全過（含檔案落地斷言）"
