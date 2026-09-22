#!/usr/bin/env bash
# pnpm setup：一鍵建置專案本地工具鏈（冪等，已存在的會跳過）。
# 裝的東西全在 .tools/（不進版控、不碰系統）：
#   .tools/jdk-21            -> Temurin JDK 21（有 javac；已有可用 JDK 21 則跳過下載）
#   .tools/android-sdk/      -> cmdline-tools + platform-tools + platforms;android-35 + build-tools;35.0.0
# 限定 Linux x86_64。裝完下一步：corepack pnpm install，然後 pnpm android:test。
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TOOLS="$ROOT/.tools"
SDK="$TOOLS/android-sdk"

# ---- 版本 pin（升級改這裡） ----
CMDLINE_ZIP="commandlinetools-linux-11076708_latest.zip"
SDK_PACKAGES=("platform-tools" "platforms;android-35" "build-tools;35.0.0")

if [ "$(uname -s)" != "Linux" ] || [ "$(uname -m)" != "x86_64" ]; then
  echo "error: setup 只支援 Linux x86_64（現在是 $(uname -s)/$(uname -m)）" >&2
  exit 2
fi
for cmd in curl unzip tar; do
  command -v "$cmd" >/dev/null || { echo "error: 缺少 $cmd，先裝它" >&2; exit 2; }
done

has_javac() { [ -x "$1/bin/java" ] && [ -x "$1/bin/javac" ]; }

# ---- 1. JDK 21（含 javac）：系統有就用系統的，沒有才下載 ----
JDK=""
for candidate in "${JAVA_HOME:-}" /usr/lib/jvm/java-21-openjdk-amd64 "$TOOLS/jdk-21"; do
  [ -n "$candidate" ] && has_javac "$candidate" && { JDK="$candidate"; break; }
done

if [ -z "$JDK" ]; then
  echo "==> 下載 Temurin JDK 21 到 .tools/…"
  mkdir -p "$TOOLS"
  curl -L -o "$TOOLS/jdk21.tar.gz" \
    "https://api.adoptium.net/v3/binary/latest/21/ga/linux/x64/jdk/hotspot/normal/eclipse"
  tar -xzf "$TOOLS/jdk21.tar.gz" -C "$TOOLS"
  rm "$TOOLS/jdk21.tar.gz"
  extracted="$(ls -d "$TOOLS"/jdk-21* | grep -v '^.*jdk-21$' | head -n 1)"
  ln -sfn "$(basename "$extracted")" "$TOOLS/jdk-21"
  JDK="$TOOLS/jdk-21"
else
  echo "==> JDK 21 已有（$JDK），跳過下載"
fi
export JAVA_HOME="$JDK"
"$JDK/bin/java" -version 2>&1 | head -n 1

# ---- 2. Android SDK：缺件才補 ----
need_sdk=false
[ -d "$SDK/platforms/android-35" ] || need_sdk=true
[ -d "$SDK/build-tools/35.0.0" ] || need_sdk=true
[ -x "$SDK/platform-tools/adb" ] || need_sdk=true

if [ "$need_sdk" = true ]; then
  echo "==> 下載 cmdline-tools 並安裝 SDK 套件…"
  mkdir -p "$SDK/cmdline-tools"
  if [ ! -x "$SDK/cmdline-tools/latest/bin/sdkmanager" ]; then
    curl -L -o "$TOOLS/cmdline-tools.zip" \
      "https://dl.google.com/android/repository/$CMDLINE_ZIP"
    unzip -q -o "$TOOLS/cmdline-tools.zip" -d "$SDK/cmdline-tools/"
    rm "$TOOLS/cmdline-tools.zip"
    mv "$SDK/cmdline-tools/cmdline-tools" "$SDK/cmdline-tools/latest"
  fi
  # pipefail 下 yes 收 SIGPIPE 會回 141：這行關掉，管線狀態 = sdkmanager 本人。
  set +o pipefail
  yes | "$SDK/cmdline-tools/latest/bin/sdkmanager" \
    --sdk_root="$SDK" --install "${SDK_PACKAGES[@]}" >/dev/null
  set -o pipefail
else
  echo "==> Android SDK 已有，跳過下載"
fi

# ---- 3. 驗收 ----
echo "==> 驗收"
"$SDK/cmdline-tools/latest/bin/sdkmanager" --sdk_root="$SDK" --list_installed 2>/dev/null \
  | grep -E "build-tools|platform-tools|platforms" || true
"$SDK/platform-tools/adb" version 2>&1 | head -n 1
echo "OK：下一步 corepack pnpm install，然後 pnpm android:test"
