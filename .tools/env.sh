# 專案本地工具鏈：source 這個檔即可（fish/zsh 通用寫法見 README）
#   source .tools/env.sh
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
export ANDROID_HOME="$ROOT/.tools/android-sdk"
export ANDROID_SDK_ROOT="$ANDROID_HOME"
if [ -z "${JAVA_HOME:-}" ]; then
  # 專案本地 Temurin JDK 21（含 javac；系統的 java-21 是 JRE，不能編譯）
  export JAVA_HOME="$ROOT/.tools/jdk-21.0.12.1+1"
fi
export PATH="$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$ANDROID_HOME/build-tools/35.0.0:$PATH"
