#include "../src/shipwright_background.c"
#include <assert.h>
#include <string.h>

#define STB_IMAGE_WRITE_IMPLEMENTATION
#define STBI_WRITE_NO_STDIO
#include "stb_image_write.h"

static uint8_t jpeg[320 * 240 * 2];
static uint8_t pixels[320 * 240 * 3];
static size_t jpegSize;

static void WriteJpeg(void* context, void* data, int length) {
    (void)context;
    assert(jpegSize + length <= sizeof(jpeg));
    memcpy(jpeg + jpegSize, data, length);
    jpegSize += length;
}

int main(void) {
    for (size_t i = 0; i < sizeof(pixels); i += 3) pixels[i] = 255;
    assert(stbi_write_jpg_to_func(WriteJpeg, NULL, 320, 240, 3, pixels, 100));
    const uint8_t* decoded = (const uint8_t*)ResourceMgr_LoadJPEG((char*)jpeg, sizeof(jpeg));
    for (size_t i = 0; i < sizeof(jpeg); i += 2) {
        assert(decoded[i] == 0xf8 && decoded[i + 1] == 0x01);
    }
}
