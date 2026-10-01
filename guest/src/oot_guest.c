#include "oot_guest.h"

static OotInputState input = {
    .abi_version = OOT_ABI_VERSION,
    .byte_size = sizeof(OotInputState),
};

static uint32_t initialized;
static uint64_t last_step_microseconds;
static uint32_t language;

#ifdef OOT_SCENE_PROBE
void OotSceneProbe_Init(void);
void OotSceneProbe_BeforeFrame(OotInputState* input);
void OotSceneProbe_AfterFrame(void);
#endif

#ifdef OOT_SHIPWRIGHT_GAME
void Graph_WasmRunFrame(void);
void Main_WasmInit(void);
void OotRenderer_Init(void);
void Audio_Update(void);
void OotShipwright_SetInput(const OotInputState* input);
void OotShipwright_SetTime(uint64_t monotonic_microseconds);
void OotShipwright_SetRandomSeed(uint32_t random_seed);
void OotShipwright_AudioInit(void);
int32_t OotShipwright_AudioStep(uint32_t sample_frames);
void OotShipwright_SetAudioVolumes(float music, float effects, float fanfares);
#endif

uint32_t oot_abi_version(void) {
    return OOT_ABI_VERSION;
}

uint32_t oot_input_buffer(void) {
    return (uint32_t)(uintptr_t)&input;
}

int32_t oot_set_language(uint32_t value) {
    // Shipwright's language IDs: English = 0, Japanese = 3.
    if (value != 0 && value != 3) return -1;
    language = value;
    return 0;
}

uint32_t OotGuest_GetLanguage(void) {
    return language;
}

int32_t oot_start(void) {
    static const OotGuestConfiguration config = {
        .abi_version = OOT_ABI_VERSION,
        .byte_size = sizeof(OotGuestConfiguration),
        .random_seed = 1,
    };
    return oot_init((uint32_t)(uintptr_t)&config);
}

int32_t oot_init(uint32_t config_address) {
    const OotGuestConfiguration* config = (const OotGuestConfiguration*)(uintptr_t)config_address;
    if (config_address == 0 || config->abi_version != OOT_ABI_VERSION ||
        config->byte_size < sizeof(OotGuestConfiguration)) {
        return -1;
    }

    initialized = 1;
    last_step_microseconds = 0;
#ifdef OOT_SHIPWRIGHT_GAME
    OotShipwright_SetRandomSeed(config->random_seed);
    OotRenderer_Init();
    Main_WasmInit();
    OotShipwright_AudioInit();
#endif
#ifdef OOT_SCENE_PROBE
    OotSceneProbe_Init();
#endif
    return 0;
}

int32_t oot_step(uint64_t monotonic_microseconds) {
    if (!initialized || monotonic_microseconds < last_step_microseconds) {
        return -1;
    }

    last_step_microseconds = monotonic_microseconds;
#ifdef OOT_SCENE_PROBE
    OotSceneProbe_BeforeFrame(&input);
#endif
#ifdef OOT_SHIPWRIGHT_GAME
    OotShipwright_SetTime(monotonic_microseconds);
    OotShipwright_SetInput(&input);
    Graph_WasmRunFrame();
    Audio_Update();
#endif
#ifdef OOT_SCENE_PROBE
    OotSceneProbe_AfterFrame();
#endif
    return OOT_STEP_FRAME_PRODUCED;
}

int32_t oot_audio_step(uint32_t sample_frames) {
    if (!initialized) {
        return -1;
    }
#ifdef OOT_SHIPWRIGHT_GAME
    return OotShipwright_AudioStep(sample_frames);
#else
    return (int32_t)sample_frames;
#endif
}

void oot_set_audio_volumes(float music, float effects, float fanfares) {
#ifdef OOT_SHIPWRIGHT_GAME
    OotShipwright_SetAudioVolumes(music, effects, fanfares);
#endif
}

void oot_shutdown(void) {
    initialized = 0;
}
