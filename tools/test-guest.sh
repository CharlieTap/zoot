#!/bin/bash
set -euo pipefail

root="$(cd "$(dirname "$0")/.." && pwd)"
source="${1:-$root/build/dependencies/shipwright}"
output="$root/build/guest-tests"
mkdir -p "$output"
cd "$root"

native="${CXX:-clang++}"
native_c="${CC:-clang}"
wasi="${WASI_CLANG:-clang}"
if [[ -z "${WASI_CLANG:-}" && -x /opt/homebrew/opt/llvm/bin/clang ]]; then
    wasi=/opt/homebrew/opt/llvm/bin/clang
fi
includes=(-Iguest/shims -Iguest/include -I"$source/libultraship/include")
flags=(-O2 -std=c++20 -fno-exceptions -fno-rtti -ffunction-sections -fdata-sections -Wno-unknown-attributes)
if [[ "$(uname)" == Darwin ]]; then
    linker=(-Wl,-dead_strip)
else
    linker=(-Wl,--gc-sections -ldl)
fi

"$native" "${flags[@]}" "${includes[@]}" -DF3DEX_GBI_2=1 \
    -DCVAR_INTERNAL_RESOLUTION='"Resolution"' -DCVAR_MSAA_VALUE='"Msaa"' \
    guest/tests/resource_cache_test.cpp "$source/libultraship/src/fast/interpreter.cpp" \
    "${linker[@]}" -o "$output/resource-cache"
"$output/resource-cache"
"$native" "${flags[@]}" "${includes[@]}" guest/tests/renderer_sampler_test.cpp \
    "${linker[@]}" -o "$output/renderer-sampler"
"$output/renderer-sampler"
"$native" "${flags[@]}" "${includes[@]}" guest/tests/shader_test.cpp guest/src/shader.cpp \
    -o "$output/shader"
"$output/shader"

"$native_c" -O2 -I"$source/torch/lib/n64graphics" guest/tests/background.c -lm -o "$output/background"
"$output/background"

"$wasi" --target=wasm32-wasip1 -O3 -Iguest/include \
    guest/tests/language.c guest/src/oot_guest.c \
    -mexec-model=reactor -o "$output/language.wasm"

"$wasi" --target=wasm32-wasip1 -O3 -DF3DEX_GBI_2=1 "${includes[@]}" \
    -I"$source/soh/include" -I"$source/soh" guest/tests/save_io.c \
    -mexec-model=reactor -o "$output/save-io.wasm"

"$wasi" --target=wasm32-wasip1 -O3 -DF3DEX_GBI_2=1 "${includes[@]}" \
    -I"$source/soh/include" -I"$source/soh" guest/tests/save_upgrade.c \
    -mexec-model=reactor -Wl,--export-memory -o "$output/save-upgrade.wasm"

"$wasi" --target=wasm32-wasip1 -O3 -DF3DEX_GBI_2=1 "${includes[@]}" \
    -I"$source/soh/include" -I"$source/soh" guest/tests/blob.c \
    -mexec-model=reactor -o "$output/blob.wasm"

"$wasi" --target=wasm32-wasip1 -O3 -DF3DEX_GBI_2=1 "${includes[@]}" \
    -I"$source/soh/include" -I"$source/soh" guest/tests/resources.c \
    -mexec-model=reactor -o "$output/resources.wasm"

"$wasi" --target=wasm32-wasip1 -O3 -DF3DEX_GBI_2=1 -DLOG_LEVEL_GAME_PRINTS=6 "${includes[@]}" \
    -I"$source/soh/include" -I"$source/soh" -I"$source/soh/src" guest/tests/scenes.c \
    -mexec-model=reactor -Wl,--allow-undefined -o "$output/scenes.wasm"
node guest/tests/run.mjs "$output"
