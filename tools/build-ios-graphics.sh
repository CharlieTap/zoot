#!/bin/bash
set -euo pipefail

sdk="$1"
output="$2"
root="$(cd "$(dirname "$0")/.." && pwd)"
version=v29.0.0.0
if [[ "$sdk" == iphonesimulator ]]; then
    archive=wgpu-ios-aarch64-simulator-release.zip
    checksum=9fa62e69fa13786375ad016d084b472f867c2f9a69b29fa5f9f67b1a31740231
    target=arm64-apple-ios16.0-simulator
else
    archive=wgpu-ios-aarch64-release.zip
    checksum=aad4ea7c293dc3215e2fa16492599fc579455ae04665210816c23f84474e03f1
    target=arm64-apple-ios16.0
fi
mkdir -p "$output"
if [[ ! -f "$output/wgpu/lib/libwgpu_native.a" ]]; then
    curl --fail --location --retry 3 "https://github.com/gfx-rs/wgpu-native/releases/download/$version/$archive" -o "$output/$archive"
    echo "$checksum  $output/$archive" | shasum -a 256 -c -
    unzip -q "$output/$archive" -d "$output/wgpu"
fi
xcrun --sdk "$sdk" clang++ -std=c++17 -O3 -DNDEBUG -target "$target" \
    -isysroot "$(xcrun --sdk "$sdk" --show-sdk-path)" \
    -I "$output/wgpu/include" -I "$root/graphics/backend/webgpu/src/nativeInterop" \
    -c "$root/graphics/backend/webgpu/src/nativeInterop/oot_gpu.cpp" -o "$output/oot_gpu.o"
xcrun ar rcs "$output/libootgpu.a" "$output/oot_gpu.o"
