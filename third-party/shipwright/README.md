# Shipwright sources

Zoot downloads pinned source archives and applies the patches in `patches/series`.
The prepared source lives in `build/dependencies/shipwright`; it is build output,
not a working copy. Keep changes here, not in that generated directory.

## Origins

| Source | Repository | Commit |
| --- | --- | --- |
| Shipwright | [HarbourMasters/Shipwright](https://github.com/HarbourMasters/Shipwright) | `ff0209e76f01cf816806cd1d8521373744d1dc8e` |
| libultraship | [kenix3/libultraship](https://github.com/kenix3/libultraship) | `62e973aeb4a53ad4d22bb91e2d9373ecdfcd246c` |
| Torch | [HarbourMasters/Torch](https://github.com/HarbourMasters/Torch) | `72960ca16f4723f96aaf4037b76072c230af8c11` |

libultraship follows Shipwright's recorded submodule commit, not its independent
2.0 branch. Torch was updated separately after checking OoT extraction parity.
`sources.cmake` contains the archive URLs and SHA-256 checksums.
The asset tools also fetch pinned spdlog, yaml-cpp, tinyxml2 and zlib sources.
spdlog uses its bundled fmt rather than a machine-installed version. The native
compiler, SDK and system libraries remain build prerequisites, not pinned inputs.

## Patches

1. **Game integration:** the existing Wasm game adaptations and tools-only build.
2. **Audio:** the existing mixer optimisations.
3. **Fast3D:** the existing Wasm renderer and reference-renderer changes.
4. **Archive builds:** pinned native dependencies and version metadata without Git.
5. **Guest compatibility:** keep existing graphics-pool sizes and fixed audio settings.
6. **Torch dependencies:** omit the Banjo-Kazooie audio importer dependencies in an OoT-only build.
7. **Magic meter:** avoid reading past the extracted fill texture when loading its HUD tile.
8. **Texture paths:** check cached resource names when a font or UI buffer is reused.
9. **Navi hints:** load hint scripts from the archive instead of the unused ROM loader.
10. **Scene reuse:** preserve cached commands, respect shorter object lists and reset door markers.

The first three patches preserve the previous source modifications, including
reference-renderer code not used by the guest. The fourth only changes CMake.
Upstream licences remain in the downloaded sources. The shared
[third-party notices](../THIRD_PARTY_NOTICES.txt) retain the guest library notices
and credits and are packaged with both apps.

The migration excludes local `imgui.ini`, `shipofharkinian.json`, `mods/`, packed
`.o2r` files, generated `soh/properties.h` and shader copies under
`soh/assets/custom/shaders/`. Shaders are staged from their original sources during
asset preparation. Upstream workflow/build scripts and `soh/src/boot/build.c.in`
missing from the old copy are restored from the pinned archive. Gradle Sync omits
upstream Git control files. None of these are Zoot game-source changes.

## Build

```shell
./gradlew prepareShipwright       # Fetch and patch sources only
./gradlew buildGuest              # Build Wasm; no ROM required
./gradlew prepareGameAssets       # Also extract a supported local ROM
./gradlew buildGuest --offline    # Reuse the same prepared recipe
bash tools/test-guest.sh          # Guest adapters, backgrounds, language and saves
```

`prepareRomAssets` runs `buildAssetTools`, `packSupportArchive`, `selectRom`,
`extractRomAssets` and `writeGameLanguage`. Renaming the ROM, using a byte-swapped
dump or changing `-PgameLanguage` does not extract the ROM again. Tasks that read
the ROM never use the build cache.

Git, CMake 3.26+ and Ninja are required. Guest compilation also needs WASI-capable
Clang and its libraries; see the root README. Normal Android and iOS builds schedule
these tasks automatically. Gradle configuration and IDE sync do not fetch sources.

Unchanged builds skip preparation. Changed pins or patches rebuild from pristine
sources, so removing a patch removes its effects. Hash mismatches and failed
patches stop the build. Offline builds require a successful online preparation of
the same recipe; native tools require an online `prepareGameAssets` build too.
Deleting `build/` removes this local download state.

## Updating

For a Zoot change, start from a disposable checkout of the pinned revisions, apply
the existing series and export the new diff with `git diff --binary`. Paths must
be relative to the assembled Shipwright tree, including `libultraship/` or
`torch/` where needed. Give the patch a purpose and upstream-status header, then
add its filename to `patches/series` in application order.

For an upstream update, change the full commit and verified archive checksum,
rebase the patches, and review it separately from Zoot optimisations. Do not use
branch heads or moving tags.

Run the plugin checks, a fresh `buildGuest`, warm/offline builds and the local
Android/iOS release smoke tests. Compare guest imports/exports and executable
changes; compare extracted archive payloads, not ZIP timestamps. CI lints and
validates the plugin and builds the real guest without a ROM. It does not upload game assets.
