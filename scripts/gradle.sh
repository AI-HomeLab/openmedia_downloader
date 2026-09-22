#!/usr/bin/env bash
# 統一 Gradle 入口：所有 Android 測試/打包一律經由 pnpm android:* 呼叫此腳本。
# 不直接跑系統 gradle、不跳過 JAVA_HOME / ANDROID_HOME 檢查。
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# 專案本地 SDK（.tools/android-sdk）優先於全域設定：在本專案內免 export 也能跑。
if [ -d "$ROOT/.tools/android-sdk/platforms" ]; then
  export ANDROID_HOME="$ROOT/.tools/android-sdk"
  export ANDROID_SDK_ROOT="$ANDROID_HOME"
  export PATH="$ANDROID_HOME/platform-tools:$PATH"
fi

if [ ! -f "$ROOT/android/gradlew" ]; then
  echo "error: 找不到 android/gradlew（scaffold 尚未落地，見 README〈專案結構〉）" >&2
  exit 2
fi

if [ -z "${ANDROID_HOME:-}" ] || [ ! -d "${ANDROID_HOME}" ]; then
  echo "error: ANDROID_HOME 未指向可用 SDK（先跑 pnpm setup）" >&2
  exit 2
fi

# Capacitor 8 建議 JDK 21（AGP 8.x 最低 JDK 17）。已設定 JAVA_HOME 就尊重它。
if [ -z "${JAVA_HOME:-}" ]; then
  for candidate in \
    "$ROOT/.tools/jdk-21" \
    /usr/lib/jvm/java-21-openjdk-amd64 \
    /usr/lib/jvm/java-17-openjdk-amd64; do
    # 要有 javac 才算數（系統的 java-21 常是 JRE，會在編譯期炸）
    if [ -x "$candidate/bin/java" ] && [ -x "$candidate/bin/javac" ]; then
      export JAVA_HOME="$candidate"
      break
    fi
  done
fi

# Gradle 用戶目錄收進專案內（wrapper 發行版快取也在這，不污染家目錄）。
if [ -z "${GRADLE_USER_HOME:-}" ]; then
  export GRADLE_USER_HOME="$ROOT/.tools/.gradle"
fi

echo "JAVA_HOME=${JAVA_HOME:-（未設定，用系統預設 java）}"
"${JAVA_HOME:-/usr}/bin/java" -version 2>&1 | head -n 1 || true

# Gradle 以 cwd 為專案目錄：切進 android/ 再跑 wrapper。
cd "$ROOT/android"
exec ./gradlew "$@"
