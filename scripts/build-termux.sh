#!/data/data/com.termux/files/usr/bin/bash
# RikkaRP — Termux (aarch64) 本机构建脚本
#
# 为什么需要单独一个脚本：
#   1. AGP 从 Maven 下载的 aapt2 是 x86_64 ELF，aarch64 上无法执行
#      （报 "aapt2 ... Syntax error: word unexpected"）。
#      本脚本用 -P 把 Termux 的 aapt2 传进去，而不把路径写死进 gradle.properties
#      —— 写死会让普通 x86_64 的 CI / 开发机直接失败。
#   2. Termux 的 ./gradlew 需要 JAVA_HOME，且 wrapper 声明的是 9.6.0，
#      直接跑会另外下载一整套 Gradle。这里优先用已安装的系统 gradle（9.8.0）。
#
# 用法：
#   bash scripts/build-termux.sh                 # 构建 github debug
#   bash scripts/build-termux.sh clean           # 先 clean
#   bash scripts/build-termux.sh release         # 构建 release（需 local.properties 配签名）
set -u

SDK_DIR="${ANDROID_SDK_ROOT:-$HOME/android_sdk}"
AAPT2="$SDK_DIR/build-tools/36.0.0/aapt2"

# Termux 的 java 不在默认 PATH 里，先补上（./gradlew 需要）
for jh in "$PREFIX/lib/jvm/java-21-openjdk" "$PREFIX/lib/jvm/java-17-openjdk" /data/data/com.termux/files/usr/lib/jvm/java-21-openjdk; do
    if [ -x "$jh/bin/java" ]; then
        export JAVA_HOME="$jh"
        export PATH="$jh/bin:$PATH"
        break
    fi
done

if [ -z "${JAVA_HOME:-}" ] || [ ! -x "$JAVA_HOME/bin/java" ]; then
    echo "错误：找不到 java，请先安装：pkg install openjdk-21"
    exit 1
fi

if [ ! -x "$AAPT2" ]; then
    echo "错误：找不到可执行的 aapt2：$AAPT2"
    echo "请确认 Android SDK 路径，或用 ANDROID_SDK_ROOT 指定。"
    exit 1
fi

cd "$(dirname "$0")/.." || exit 1

TASK=":app:assembleGithubDebug"
DO_CLEAN=0
for arg in "$@"; do
    case "$arg" in
        clean)   DO_CLEAN=1 ;;
        release) TASK=":app:assembleGithubRelease" ;;
        *)       echo "未知参数：$arg"; exit 2 ;;
    esac
done

# 优先用系统 gradle（自带 java 配置，且版本与本机缓存一致）
if command -v gradle >/dev/null 2>&1; then
    GRADLE="gradle"
else
    GRADLE="./gradlew"
fi

echo "java_home = $JAVA_HOME"
echo "sdk       = $SDK_DIR"
echo "aapt2     = $AAPT2"
echo "gradle    = $GRADLE"
echo "task      = $TASK"
echo

if [ "$DO_CLEAN" = "1" ]; then
    echo "== clean =="
    $GRADLE clean --no-daemon "-Pandroid.aapt2FromMavenOverride=$AAPT2" || exit 1
fi

echo "== build =="
$GRADLE "$TASK" --no-daemon "-Pandroid.aapt2FromMavenOverride=$AAPT2"

rc=$?
echo
if [ "$rc" = "0" ]; then
    echo "== 产物 =="
    ls -lh app/build/outputs/apk/github/debug/*.apk 2>/dev/null \
        | awk '{print "  " $5, $9}'
else
    echo "== 构建失败 rc=$rc =="
fi
exit "$rc"
