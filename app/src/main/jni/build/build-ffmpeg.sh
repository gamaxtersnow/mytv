#!/usr/bin/env bash
#
# FFmpeg 静态库编译脚本（ExoPlayer FFmpeg extension 专用）
# 完全独立，不依赖外部 ijkplayer-gsy 目录
#
# 用法：
#   cd /Users/yaodanning/my-tv
#   app/src/main/jni/build/build-ffmpeg.sh

set -e

FF_ARCH="arm64"
FF_ANDROID_PLATFORM="android-21"
FF_CROSS_PREFIX="aarch64-linux-android"
FF_TOOLCHAIN_NAME="${FF_CROSS_PREFIX}-4.9"

PROJECT_ROOT="/Users/yaodanning/my-tv"
FF_SOURCE="${PROJECT_ROOT}/app/src/main/jni/ffmpeg"
BUILD_DIR="${PROJECT_ROOT}/app/src/main/jni/build"
FF_PREFIX="${BUILD_DIR}/output"
FF_TOOLCHAIN_PATH="${BUILD_DIR}/toolchain"
PROJECT_LIBS="${FF_SOURCE}/android-libs/arm64-v8a"

NDK_PATH="/Users/yaodanning/Library/Android/sdk/ndk-r16b-temp/android-ndk-r16b"

# 使用 GNU Make 4.x 替代 macOS 自带的 3.81（3.81 编译 FFmpeg 会 segfault）
if command -v gmake &> /dev/null; then
    MAKE_CMD="gmake"
    echo "[+] Using gmake $(gmake --version | head -1)"
else
    MAKE_CMD="make"
    echo "[!] WARNING: gmake not found, using system make (may segfault on macOS)"
    echo "    Fix: brew install make"
fi

# 检查 NDK
if [ ! -d "$NDK_PATH" ]; then
    echo "[!] ERROR: NDK not found at $NDK_PATH"
    echo "    Download NDK r16b from: https://developer.android.com/ndk/downloads/older_releases"
    exit 1
fi

export ANDROID_NDK="$NDK_PATH"

echo "================================"
echo "FFmpeg Static Library Build"
echo "ARCH: $FF_ARCH (arm64-v8a)"
echo "NDK:  $ANDROID_NDK"
echo "Source: $FF_SOURCE"
echo "Target: ExoPlayer FFmpeg extension"
echo "================================"

#--------------------
echo ""
echo "--------------------"
echo "[*] check ffmpeg source"
echo "--------------------"
if [ ! -f "${FF_SOURCE}/configure" ]; then
    echo "[!] ERROR: FFmpeg source not found at ${FF_SOURCE}"
    echo "    Please ensure ffmpeg/ directory contains the FFmpeg source code."
    exit 1
fi

#--------------------
echo ""
echo "--------------------"
echo "[*] make NDK standalone toolchain"
echo "--------------------"
FF_SYSROOT="$FF_TOOLCHAIN_PATH/sysroot"

if [ ! -f "$FF_TOOLCHAIN_PATH/touch" ]; then
    $ANDROID_NDK/build/tools/make-standalone-toolchain.sh \
        --install-dir=$FF_TOOLCHAIN_PATH \
        --platform=$FF_ANDROID_PLATFORM \
        --toolchain=$FF_TOOLCHAIN_NAME
    touch "$FF_TOOLCHAIN_PATH/touch"
    echo "[+] Toolchain created"
else
    echo "[*] Toolchain already exists, reusing"
fi

export PATH=$FF_TOOLCHAIN_PATH/bin/:$PATH
export CC="${FF_CROSS_PREFIX}-gcc"
export LD="${FF_CROSS_PREFIX}-ld"
export AR="${FF_CROSS_PREFIX}-ar"
export RANLIB="${FF_CROSS_PREFIX}-ranlib"
export STRIP="${FF_CROSS_PREFIX}-strip"

#--------------------
echo ""
echo "--------------------"
echo "[*] configure ffmpeg"
echo "--------------------"

FF_CFLAGS="-O3 -Wall -pipe \
    -std=c99 \
    -ffast-math \
    -fstack-protector-strong \
    -fstrict-aliasing -Werror=strict-aliasing \
    -Wno-psabi -Wa,--noexecstack \
    -DANDROID -DNDEBUG"

FF_CFG_FLAGS="--arch=aarch64 --enable-yasm"
FF_EXTRA_LDFLAGS="-Wl,-Bsymbolic -Wl,-z,max-page-size=16384 -Wl,-z,common-page-size=16384"

# Load decoder/parser configuration
export COMMON_FF_CFG_FLAGS=
. "${BUILD_DIR}/ffmpeg-config.sh"
FF_CFG_FLAGS="$FF_CFG_FLAGS $COMMON_FF_CFG_FLAGS"

# Standard options
FF_CFG_FLAGS="$FF_CFG_FLAGS --prefix=$FF_PREFIX"
FF_CFG_FLAGS="$FF_CFG_FLAGS --cross-prefix=${FF_CROSS_PREFIX}-"
FF_CFG_FLAGS="$FF_CFG_FLAGS --enable-cross-compile"
FF_CFG_FLAGS="$FF_CFG_FLAGS --target-os=android"
FF_CFG_FLAGS="$FF_CFG_FLAGS --enable-jni"
FF_CFG_FLAGS="$FF_CFG_FLAGS --enable-mediacodec"
FF_CFG_FLAGS="$FF_CFG_FLAGS --enable-pic"
FF_CFG_FLAGS="$FF_CFG_FLAGS --enable-asm"
FF_CFG_FLAGS="$FF_CFG_FLAGS --enable-inline-asm"
FF_CFG_FLAGS="$FF_CFG_FLAGS --enable-optimizations"
FF_CFG_FLAGS="$FF_CFG_FLAGS --enable-debug"
FF_CFG_FLAGS="$FF_CFG_FLAGS --enable-small"
FF_CFG_FLAGS="$FF_CFG_FLAGS --disable-x86asm"
FF_CFG_FLAGS="$FF_CFG_FLAGS --disable-zlib"

mkdir -p "$FF_PREFIX"

cd "$FF_SOURCE"

# Force reconfigure if config.h exists
if [ -f "./config.h" ]; then
    echo "[*] Removing stale config for reconfigure"
    $MAKE_CMD distclean > /dev/null 2>&1 || true
    rm -f config.h config.mak
    rm -f ffbuild/config.* ffbuild/.config
fi

which $CC
./configure $FF_CFG_FLAGS \
    --extra-cflags="$FF_CFLAGS" \
    --extra-ldflags="$FF_EXTRA_LDFLAGS"

#--------------------
echo ""
echo "--------------------"
echo "[*] compile ffmpeg"
echo "--------------------"
$MAKE_CMD -j$(sysctl -n hw.ncpu 2>/dev/null || echo 4)
make install

#--------------------
echo ""
echo "--------------------"
echo "[*] copy static libraries to project"
echo "--------------------"
mkdir -p "$PROJECT_LIBS"
cp "${FF_PREFIX}/lib/libavcodec.a" "$PROJECT_LIBS/"
cp "${FF_PREFIX}/lib/libavutil.a" "$PROJECT_LIBS/"
cp "${FF_PREFIX}/lib/libswresample.a" "$PROJECT_LIBS/"

echo "[+] Static libraries copied to: $PROJECT_LIBS"
ls -la "$PROJECT_LIBS"

echo ""
echo "================================"
echo "[+] FFmpeg build completed!"
echo "================================"
echo ""
echo "Next step: rebuild the APK"
echo "  cd ${PROJECT_ROOT}"
echo "  ./gradlew app:assembleDebug"
