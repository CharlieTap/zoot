// Rebuild: clang --target=wasm32-wasip1 -O2 -mexec-model=reactor guest.c -o guest.wasm
// Profiling fixture: add -DPROFILE and use -o guest-profile.wasm.
#include <stdint.h>
#include <stdio.h>

#define EXPORT(name) __attribute__((export_name(#name)))

__attribute__((import_module("test"), import_name("initialise"))) int host_initialise(void);
__attribute__((import_module("test"), import_name("input"))) int host_input(uint32_t pointer);

static int initialised;
static int language;
static int started;
static uint32_t input[5];
static float volumes[3];

#ifdef PROFILE
__attribute__((import_module("oot_profile"), import_name("fast3d"))) void host_profile(int phase);
static int entrance;

EXPORT(oot_trace_entrance) void oot_trace_entrance(int value) {
    host_profile(0);
    entrance = value;
    host_profile(1);
}
#endif

__attribute__((constructor)) static void initialise(void) {
    if (host_initialise()) __builtin_trap();
    initialised++;
}

EXPORT(oot_abi_version) int oot_abi_version(void) { return initialised == 1 ? 3 : -1; }
EXPORT(oot_input_buffer) uint32_t oot_input_buffer(void) { return (uint32_t)input; }

EXPORT(oot_set_language) int oot_set_language(int value) {
    if (initialised != 1 || started) return -1;
    language = value;
    return 0;
}

EXPORT(oot_start) int oot_start(void) {
    if (language != 1 || started) return -1;
    FILE* save = fopen("/saves/codegen-test.bin", "wb");
    if (!save) return -2;
    const uint8_t expected[] = {1, 2, 3, 4};
    if (fwrite(expected, 1, sizeof(expected), save) != sizeof(expected)) return -3;
    fclose(save);
    save = fopen("/saves/codegen-test.bin", "rb");
    if (!save) return -4;
    for (unsigned i = 0; i < sizeof(expected); i++) {
        if (fgetc(save) != expected[i]) return -5;
    }
    fclose(save);
    puts("guest stdout");
    fputs("guest stderr\n", stderr);
    started = 1;
    return 0;
}

EXPORT(oot_step) int oot_step(uint64_t timestamp) {
#ifdef PROFILE
    if (timestamp == UINT64_MAX - 1) return entrance;
#endif
    if (timestamp == UINT64_MAX) {
        return (int)(volumes[0] * 10) * 100 + (int)(volumes[1] * 10) * 10 + (int)(volumes[2] * 10);
    }
    return (int)timestamp + host_input((uint32_t)input);
}

EXPORT(oot_audio_step) int oot_audio_step(int frames) { return frames; }

EXPORT(oot_set_audio_volumes) void oot_set_audio_volumes(float music, float effects, float fanfares) {
    volumes[0] = music;
    volumes[1] = effects;
    volumes[2] = fanfares;
}
