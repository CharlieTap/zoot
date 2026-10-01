#pragma once
#include <stdint.h>
#ifdef __cplusplus
extern "C" {
#endif
typedef struct OotGpu OotGpu;
OotGpu *oot_gpu_create(void *metal_layer, int width, int height);
void oot_gpu_destroy(OotGpu *gpu);
void oot_gpu_shader(OotGpu *gpu, int id, const char *source, int stride, const int *components, int count);
void oot_gpu_texture(OotGpu *gpu, int id, const void *bytes, int width, int height);
void oot_gpu_delete_texture(OotGpu *gpu, int id);
void oot_gpu_target(OotGpu *gpu, int id, int width, int height);
void oot_gpu_begin(OotGpu *gpu);
void oot_gpu_clear(OotGpu *gpu, int id, int color, int depth);
void oot_gpu_draw(OotGpu *gpu, const void *state, const void *vertices, int size, int count);
void oot_gpu_copy(OotGpu *gpu, int destination, int source);
void oot_gpu_upscaler(OotGpu *gpu, const char *source, int linear);
void oot_gpu_end(OotGpu *gpu);
void oot_gpu_read(OotGpu *gpu, int id, int width, int height, void *destination);
int oot_gpu_depth(OotGpu *gpu, int id, float x, float y);
#ifdef __cplusplus
}
#endif
