#!/data/data/com.termux/files/usr/bin/bash
# RikkaRP — Termux (aarch64) 本机构建脚本
#
# 为什么需要单独一个脚本：
#   AGP 从 Maven 下载的 aapt2 是 x86_64 ELF，aarch64 上无法执行
#   （报 "aapt2 ... Syntax error: word unexpected"）。
#   本脚本用 -P 把 Termux 的 aapt2 传进去，而不把路径写死进 gradle.properties
#   —— 写死会让普通 x86_64 的 CI / 开发机直接失败。
#
# 用法：
#   bash scripts/build-termux.sh                 # 构建 github debug
#   bash scripts/build-termux.sh clean           # 先 clean
#   bash scripts/build-termux.sh release         # 构建 release（需 local.properties 配签名）
set -u

SDK_DIR="${ANDROID_SDK_ROOT:-$HOME/android_sdk}"
AAPT2="$SDK_DIR/build-tools/36.0.0/aapt2"

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

echo "sdk     = $SDK_DIR"
echo "aapt2   = $AAPT2"
echo "task    = $TASK"
echo

ARGS=(
    "$TASK"
    --no-daemon
    "-Pandroid.aapt2FromMavenOverride=$AAPT2"
)

if [ "$DO_CLEAN" = "1" ]; then
    echo "== clean =="
    ./gradlew clean --no-daemon "-Pandroid.aapt2FromMavenOverride=$AAPT2" || exit 1
fi

echo "== build =="
./gradlew "${ARGS[@]}"

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
