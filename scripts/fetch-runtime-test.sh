#!/usr/bin/env bash
set -euo pipefail
project_dir=$(cd "$(dirname "$0")/.." && pwd)
file="$project_dir/app/src/androidTest/assets/alpine-x86.tar.gz"
mkdir -p "$(dirname "$file")"
if ! test -f "$file"; then
  curl -fsSL --retry 2 https://dl-cdn.alpinelinux.org/alpine/v3.23/releases/x86_64/alpine-minirootfs-3.23.0-x86_64.tar.gz -o "$file"
fi
printf 'ce8f782f1628d046fb6360eff880b898e5205ed91106d9d14ff4fcb97431bbde  %s\n' "$file" | sha256sum -c -
