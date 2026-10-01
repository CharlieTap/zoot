# Zoot guest

One Wasm32 reactor containing the stripped Shipwright game, libultraship's Fast3D
interpreter, texture conversion, the WebGPU shader generator, and Shipwright's C audio mixer.

The guest sends decoded vertex batches, RGBA textures, WGSL and PCM to the host.
It does **not** send Fast3D display lists or audio command lists to Kotlin.

`src/renderer.cpp` implements libultraship's rendering interface. `src/shader.cpp`
generates WGSL inside Wasm. The small headers in `shims/` replace desktop services;
they do not replace the Fast3D decoder. The inherited game compatibility stubs
disable Shipwright enhancements and desktop integration, not vanilla game logic.

Build with `./gradlew buildGuest` from the repository root.

Gradle prepares pinned Shipwright, libultraship and Torch sources automatically.
Their revisions and our patches live in `third-party/shipwright/`; the generated
source tree is under `build/dependencies/shipwright/`. Edit the patches, not the
generated tree. A guest-only build does not require a ROM.

The app build normally uses Gradle's `buildGuest` task and packages its output
from `build/game-assets/`. `oot_set_language(0)` selects English and
`oot_set_language(3)` selects Japanese before `oot_start()`. All other fixed
Shipwright settings remain folded to their defaults. The ROM preparation task
supplies the language in `language.txt`; neither language needs a separate Wasm.

Room backgrounds are decoded from JPEG to N64 RGBA5551 inside Wasm using the
vendored scalar stb_image decoder. The room code replaces the cached JPEG with
its pixels, so decoding happens once per resource, not every frame.

Run `bash tools/test-guest.sh` after `./gradlew prepareShipwright` for the guest
adapter tests. They cover cache lookup, rendering, shaders, background decoding,
language selection and save I/O/upgrade without a ROM. Native Clang and Node.js
are needed in addition to the guest build tools. Save tests use a temporary
directory and never touch player saves.
