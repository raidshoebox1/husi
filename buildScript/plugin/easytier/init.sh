#!/usr/bin/env bash

source "buildScript/init/env.sh"

export AR=$ANDROID_AR
export LD=$ANDROID_LD

# EasyTier depends on pnet / network-interface, which call getifaddrs/freeifaddrs.
# Those symbols only exist in Bionic since Android API 24 (the app's minSdk is 24),
# so link against API 24 instead of the default 21 to resolve them.
export ANDROID_ARM_CC=${ANDROID_ARM_CC/21-clang/24-clang}
export ANDROID_ARM_CXX=${ANDROID_ARM_CXX/21-clang/24-clang}
export ANDROID_ARM64_CC=${ANDROID_ARM64_CC/21-clang/24-clang}
export ANDROID_ARM64_CXX=${ANDROID_ARM64_CXX/21-clang/24-clang}
export ANDROID_X86_CC=${ANDROID_X86_CC/21-clang/24-clang}
export ANDROID_X86_CXX=${ANDROID_X86_CXX/21-clang/24-clang}
export ANDROID_X86_64_CC=${ANDROID_X86_64_CC/21-clang/24-clang}
export ANDROID_X86_64_CXX=${ANDROID_X86_64_CXX/21-clang/24-clang}

ndkVer=$(grep Pkg.Revision $ANDROID_NDK_HOME/source.properties)
ndkVer=${ndkVer#*= }
ndkVer=${ndkVer%%.*}

export CARGO_NDK_MAJOR_VERSION=$ndkVer
export RUST_ANDROID_GRADLE_PYTHON_COMMAND=python3
export RUST_ANDROID_GRADLE_LINKER_WRAPPER_PY=$SRC_ROOT/buildScript/rust-linker/linker-wrapper.py
export RUST_ANDROID_GRADLE_CC_LINK_ARG=""
export BINDGEN_EXTRA_CLANG_ARGS=--sysroot=$ANDROID_NDK_HOME/toolchains/llvm/prebuilt/linux-x86_64/sysroot/

CURR="plugin/easytier"
CURR_PATH="$SRC_ROOT/$CURR"

ROOT="$CURR_PATH/src/main/jniLibs"
OUTPUT="easytier"
LIB_OUTPUT="lib$OUTPUT.so"

cd $CURR_PATH/src/main/rust/easytier
