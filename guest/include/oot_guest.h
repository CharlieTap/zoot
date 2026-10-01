#ifndef OOT_GUEST_H
#define OOT_GUEST_H

#include <stdint.h>

#define OOT_ABI_VERSION 3u

typedef struct {
    uint32_t abi_version;
    uint32_t byte_size;
    uint32_t asset_manifest_address;
    uint32_t asset_manifest_length;
    uint32_t random_seed;
} OotGuestConfiguration;

typedef struct {
    uint32_t abi_version;
    uint32_t byte_size;
    uint32_t buttons;
    int16_t stick_x;
    int16_t stick_y;
    uint16_t reserved;
} OotInputState;

typedef struct {
    uint32_t byte_size;
    uint32_t resource_size;
} OotResourceInfo;

enum OotStepResult {
    OOT_STEP_NONE = 0,
    OOT_STEP_FRAME_PRODUCED = 1 << 0,
    OOT_STEP_SAVE_DIRTY = 1 << 1,
    OOT_STEP_SCENE_CHANGED = 1 << 2,
    OOT_STEP_SHUTDOWN_REQUESTED = 1 << 3,
};

uint32_t oot_abi_version(void);
uint32_t oot_input_buffer(void);
int32_t oot_start(void);
int32_t oot_set_language(uint32_t language);
uint32_t OotGuest_GetLanguage(void);
int32_t oot_init(uint32_t config_address);
int32_t oot_step(uint64_t monotonic_microseconds);
int32_t oot_audio_step(uint32_t sample_frames);
void oot_set_audio_volumes(float music, float effects, float fanfares);
void oot_shutdown(void);

#endif
