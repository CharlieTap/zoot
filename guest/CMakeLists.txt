cmake_minimum_required(VERSION 3.26)
project(oot_guest LANGUAGES C CXX)

set(CMAKE_C_STANDARD 23)
set(CMAKE_CXX_STANDARD 20)

option(OOT_BUILD_SHIPWRIGHT "Compile the stripped Shipwright game sources into the guest" ON)
option(OOT_TRACE_STUBS "Count calls to stripped Shipwright compatibility stubs" OFF)
option(OOT_WASM_DIAGNOSTICS "Emit low-frequency guest checkpoints through a host import" OFF)
option(OOT_PROFILE "Expose coarse Fast3D timing markers" OFF)
option(OOT_RENDER_TRACE "Capture one selected Fast3D frame" OFF)
option(OOT_AUDIO_STATS "Count mixer paths and decoded-block cache hits" OFF)
option(OOT_SCENE_PROBE "Boot a disposable scene from /saves/scene-probe.txt for rendering checks" OFF)

add_executable(oot_guest src/oot_guest.c)
target_include_directories(oot_guest PRIVATE include)
target_compile_definitions(oot_guest PRIVATE OOT_WASM_GUEST=1)

if(OOT_BUILD_SHIPWRIGHT)
    set(SHIPWRIGHT_SOURCE_DIR "" CACHE PATH "Prepared Shipwright sources")
    if(NOT EXISTS "${SHIPWRIGHT_SOURCE_DIR}/soh/include/z64.h")
        message(FATAL_ERROR "Build with ./gradlew buildGuest to prepare Shipwright sources.")
    endif()
    set(SHIPWRIGHT_DIR "${SHIPWRIGHT_SOURCE_DIR}/soh")
    include("${SHIPWRIGHT_DIR}/../CMake/soh-cvars.cmake")
    file(GLOB_RECURSE SHIPWRIGHT_SOURCES CONFIGURE_DEPENDS "${SHIPWRIGHT_DIR}/src/*.c")
    list(FILTER SHIPWRIGHT_SOURCES EXCLUDE REGEX "/src/dmadata/")
    list(FILTER SHIPWRIGHT_SOURCES EXCLUDE REGEX "/src/elf_message/")
    list(FILTER SHIPWRIGHT_SOURCES EXCLUDE REGEX "/src/libultra/(io|libc|os|rmon)/")
    list(FILTER SHIPWRIGHT_SOURCES EXCLUDE REGEX "/src/libultra/gu/(cosf|lookat|lookathil|perspective|position|sinf|sqrtf|us2dex)\\.c$")

    add_library(shipwright_game OBJECT ${SHIPWRIGHT_SOURCES})
    target_sources(shipwright_game PRIVATE "${SHIPWRIGHT_DIR}/soh/gu_pc.c" "${SHIPWRIGHT_DIR}/soh/mixer.c")
    set_property(SOURCE "${SHIPWRIGHT_DIR}/soh/mixer.c" APPEND PROPERTY COMPILE_DEFINITIONS
        $<$<BOOL:${OOT_AUDIO_STATS}>:OOT_AUDIO_STATS=1>)
    target_include_directories(
        shipwright_game
        PRIVATE
        include
        "${SHIPWRIGHT_DIR}/include"
        "${SHIPWRIGHT_DIR}/src"
        "${SHIPWRIGHT_DIR}/assets"
        "${SHIPWRIGHT_DIR}"
        "${SHIPWRIGHT_DIR}/../libultraship/include"
    )
    target_compile_definitions(
        shipwright_game
        PRIVATE
        OOT_WASM_GUEST=1
        # Match the default-only bridge in shipwright_platform.c, so Clang can fold its callers.
        OOT_FIXED_CVARS=1
        F3DEX_GBI_2=1
        LOG_LEVEL_GAME_PRINTS=0
        $<$<BOOL:${OOT_WASM_DIAGNOSTICS}>:OOT_WASM_DIAGNOSTICS=1>
    )
    target_compile_options(
        shipwright_game
        PRIVATE
        -O3
        -ffp-contract=off
        -fno-fast-math
        -Wno-incompatible-pointer-types
        -Wno-int-conversion
        -Wno-unknown-pragmas
    )
    target_sources(
        oot_guest
        PRIVATE
        src/shipwright_actor_db.c
        src/shipwright_background.c
        src/shipwright_platform.c
        src/shipwright_resources.c
        src/shipwright_vanilla.c
        src/shipwright_save.c
        generated/shipwright_stubs.c
        generated/shipwright_stub_registry.c
        $<TARGET_OBJECTS:shipwright_game>
    )
    if(OOT_SCENE_PROBE)
        target_sources(oot_guest PRIVATE tests/scene_probe.c)
        target_compile_definitions(oot_guest PRIVATE OOT_SCENE_PROBE=1)
        target_link_options(oot_guest PRIVATE -Wl,--export=oot_scene_probe_state)
    endif()
    target_include_directories(
        oot_guest
        PRIVATE
        "${SHIPWRIGHT_DIR}/include"
        "${SHIPWRIGHT_DIR}/src"
        "${SHIPWRIGHT_DIR}/assets"
        "${SHIPWRIGHT_DIR}"
        "${SHIPWRIGHT_DIR}/../libultraship/include"
        "${SHIPWRIGHT_DIR}/../torch/lib/n64graphics"
    )
    target_compile_definitions(
        oot_guest
        PRIVATE
        OOT_SHIPWRIGHT_GAME=1
        F3DEX_GBI_2=1
        OOT_TRACE_STUBS=$<BOOL:${OOT_TRACE_STUBS}>
        OOT_RENDER_TRACE=$<BOOL:${OOT_RENDER_TRACE}>
        OOT_PROFILE=$<BOOL:${OOT_PROFILE}>
        $<$<BOOL:${OOT_WASM_DIAGNOSTICS}>:OOT_WASM_DIAGNOSTICS=1>
    )
    add_library(fast3d OBJECT
        "${SHIPWRIGHT_DIR}/../libultraship/src/fast/interpreter.cpp"
        "${SHIPWRIGHT_DIR}/../libultraship/src/fast/debug/GfxDebugger.cpp"
        "${SHIPWRIGHT_DIR}/../libultraship/src/ship/utils/StrHash64.cpp"
        src/renderer.cpp src/resources_bridge.cpp src/shader.cpp)
    target_include_directories(fast3d BEFORE PRIVATE shims include "${SHIPWRIGHT_DIR}/../libultraship/include")
    target_compile_definitions(fast3d PRIVATE F3DEX_GBI_2=1 CVAR_INTERNAL_RESOLUTION="Resolution" CVAR_MSAA_VALUE="Msaa" OOT_PROFILE=$<BOOL:${OOT_PROFILE}> OOT_RENDER_TRACE=$<BOOL:${OOT_RENDER_TRACE}>)
    target_compile_options(fast3d PRIVATE -O3 -fno-exceptions -fno-rtti -ffp-contract=off)
    target_sources(oot_guest PRIVATE $<TARGET_OBJECTS:fast3d>)
    set_target_properties(oot_guest PROPERTIES LINKER_LANGUAGE CXX)
    target_link_options(oot_guest PRIVATE -mexec-model=reactor -Wl,--allow-undefined)
endif()
target_compile_options(oot_guest PRIVATE -O3 -ffp-contract=off -fno-fast-math)
target_link_options(
    oot_guest
    PRIVATE
    $<$<NOT:$<BOOL:${OOT_BUILD_SHIPWRIGHT}>>:-nostdlib>
    -Wl,--no-entry
    -Wl,--export-memory
    -Wl,--export=oot_abi_version
    -Wl,--export=oot_input_buffer
    -Wl,--export=oot_init
    -Wl,--export=oot_start
    -Wl,--export=oot_set_language
    -Wl,--export=oot_step
    -Wl,--export=oot_audio_step
    -Wl,--export=oot_set_audio_volumes
    -Wl,--export=oot_shutdown
    $<$<BOOL:${OOT_RENDER_TRACE}>:-Wl,--export=oot_trace_frame>
    $<$<OR:$<BOOL:${OOT_RENDER_TRACE}>,$<BOOL:${OOT_PROFILE}>>:-Wl,--export=oot_trace_entrance>
    -Wl,--export=oot_stub_count
    -Wl,--export=oot_stub_calls
    -Wl,--export=oot_stub_name
    -Wl,--export=oot_stub_reset
    -Wl,--initial-memory=16777216
    -Wl,--max-memory=268435456
    -Wl,-Map=${CMAKE_CURRENT_BINARY_DIR}/oot.map
    $<$<CONFIG:RelWithDebInfo>:-g>
)

set_target_properties(oot_guest PROPERTIES OUTPUT_NAME oot SUFFIX .wasm)
