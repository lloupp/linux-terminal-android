#!/usr/bin/env bash
set -euo pipefail
# Build reviewed, pinned PRoot and talloc for APK-native executable packaging.
project_dir=$(cd "$(dirname "$0")/.." && pwd)
build_dir="$project_dir/build/proot"
ndk_dir="${ANDROID_NDK_HOME:-${ANDROID_SDK_ROOT:?Set ANDROID_SDK_ROOT}/ndk/26.1.10909125}"
toolchain_dir="$ndk_dir/toolchains/llvm/prebuilt/linux-x86_64/bin"
mkdir -p "$build_dir"
fetch() {
  local url=$1 file=$2 digest=$3
  if ! test -f "$file"; then curl -fsSL --retry 2 "$url" -o "$file"; fi
  printf '%s  %s\n' "$digest" "$file" | sha256sum -c -
}
fetch https://codeload.github.com/termux/proot/tar.gz/a179d3e8a4e045aaa1fb8cc3284f23509d96d353 "$build_dir/proot.tar.gz" f25791d84702daedcdfd5fb0d9d5247892463b225266df36611e1fa0c90ba272
fetch https://www.samba.org/ftp/talloc/talloc-2.4.2.tar.gz "$build_dir/talloc.tar.gz" 85ecf9e465e20f98f9950a52e9a411e14320bc555fa257d87697b7e7a9b1d8a6
tar -xzf "$build_dir/proot.tar.gz" -C "$build_dir"
tar -xzf "$build_dir/talloc.tar.gz" -C "$build_dir"
cat > "$build_dir/replace.h" <<'HEADER'
#pragma once
#define _GNU_SOURCE 1
#include <stdio.h>
#include <stdlib.h>
#include <stdint.h>
#include <stdbool.h>
#include <string.h>
#include <stdarg.h>
#include <errno.h>
#include <limits.h>
#include <unistd.h>
#include <sys/types.h>
#define TALLOC_BUILD_VERSION_MAJOR 2
#define TALLOC_BUILD_VERSION_MINOR 4
#define TALLOC_BUILD_VERSION_RELEASE 2
#define HAVE_CONSTRUCTOR_ATTRIBUTE 1
#define HAVE_GETAUXVAL 1
#define HAVE_SYS_AUXV_H 1
#define HAVE_INTPTR_T 1
#define HAVE_VA_COPY 1
#define PRINTF_ATTRIBUTE(a,b) __attribute__((format(printf,a,b)))
#define MIN(a,b) ((a)<(b)?(a):(b))
#define MAX(a,b) ((a)>(b)?(a):(b))
#define discard_const_p(type,ptr) ((type *)(uintptr_t)(ptr))
#define ZERO_STRUCT(x) memset(&(x),0,sizeof(x))
HEADER
for abi in ${PROOT_ABIS:-arm64-v8a armeabi-v7a x86_64 x86}; do
  case "$abi" in
    arm64-v8a) triple=aarch64-linux-android24 ;;
    armeabi-v7a) triple=armv7a-linux-androideabi24 ;;
    x86_64) triple=x86_64-linux-android24 ;;
    x86) triple=i686-linux-android24 ;;
    *) exit 1 ;;
  esac
  output_dir="$build_dir/$abi"
  mkdir -p "$output_dir" "$project_dir/app/src/main/jniLibs/$abi"
  rm -rf "$output_dir/src"
  cp -a "$build_dir/proot-a179d3e8a4e045aaa1fb8cc3284f23509d96d353/src" "$output_dir/"
  patch -d "$output_dir" -p1 < "$project_dir/scripts/patches/android-x86_64-fork.patch"
  # Missing string declarations in the pinned upstream source; no behavior change.
  sed -i '/#include <stdlib.h>/a #include <string.h>' "$output_dir/src/extension/ashmem_memfd/ashmem_memfd.c"
  sed -i 's/-static -nostdlib /-static -nostdlib -Wl,-z,max-page-size=16384 /' "$output_dir/src/GNUmakefile"
  # Equivalent symbol offsets with POSIX awk (the upstream script requires gawk).
  cat > "$output_dir/src/loader/loader-info.awk" <<'AWK'
$8 == "pokedata_workaround" { p = ("0x" $2) + 0 }
$8 == "_start" { s = ("0x" $2) + 0 }
END { print "#include <unistd.h>"; print "const ssize_t offset_to_pokedata_workaround=" (p-s) ";" }
AWK
  "$toolchain_dir/$triple-clang" -fPIC -O2 -I"$build_dir" -I"$build_dir/talloc-2.4.2" -c "$build_dir/talloc-2.4.2/talloc.c" -o "$output_dir/talloc.o"
  "$toolchain_dir/llvm-ar" rcs "$output_dir/libtalloc.a" "$output_dir/talloc.o"
  make -C "$output_dir/src" -j2 CC="$toolchain_dir/$triple-clang" \
    CPPFLAGS="-I. -D_FILE_OFFSET_BITS=64 -D_GNU_SOURCE -I$build_dir/talloc-2.4.2 -DARG_MAX=131072" \
    LDFLAGS="-L$output_dir -ltalloc -Wl,-z,max-page-size=16384" \
    PROOT_UNBUNDLE_LOADER=/unused HAS_LOADER_32BIT=
  cp "$output_dir/src/proot" "$project_dir/app/src/main/jniLibs/$abi/libproot.so"
  cp "$output_dir/src/loader/loader" "$project_dir/app/src/main/jniLibs/$abi/libproot-loader.so"
done
