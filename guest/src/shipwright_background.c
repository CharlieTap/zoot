#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>

#define STB_IMAGE_IMPLEMENTATION
#define STBI_ONLY_JPEG
#define STBI_NO_STDIO
#define STBI_NO_SIMD
#include "stb_image.h"

char* ResourceMgr_LoadJPEG(char* data, size_t dataSize) {
    // Room_DrawBackground2D copies this back over the JPEG, so each resource is decoded once.
    static uint8_t background[320 * 240 * 2];
    int width, height, components;
    uint8_t* pixels = stbi_load_from_memory((const uint8_t*)data, (int)dataSize,
                                          &width, &height, &components, STBI_rgb_alpha);
    if (pixels == NULL || width != 320 || height != 240 || dataSize != sizeof(background)) {
        fprintf(stderr, "Invalid room background JPEG\n");
        abort();
    }

    // Fast3D reads RGBA5551 textures in N64 byte order.
    for (size_t i = 0; i < 320 * 240; i++) {
        const uint8_t* pixel = pixels + i * 4;
        uint16_t rgba = ((pixel[0] >> 3) << 11) | ((pixel[1] >> 3) << 6) |
                        ((pixel[2] >> 3) << 1) | (pixel[3] != 0);
        background[i * 2] = rgba >> 8;
        background[i * 2 + 1] = rgba;
    }
    stbi_image_free(pixels);
    return (char*)background;
}
