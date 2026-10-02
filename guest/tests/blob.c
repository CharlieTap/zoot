#include "../src/shipwright_resources.c"
#include <assert.h>

uint64_t OotResource_Hash(const char* path) {
    assert(0 && "Blob decoding must not resolve another resource");
    return 0;
}

int32_t Host_ResourceInfo(uint64_t id, OotResourceInfo* info) {
    assert(0 && "Blob decoding must not load another resource");
    return -1;
}

int32_t Host_ResourceRead(uint64_t id, uint32_t offset, void* destination, uint32_t length) {
    assert(0 && "Blob decoding must not read another resource");
    return -1;
}

__attribute__((export_name("test_blob")))
void TestBlob(void) {
    // A blob has a 64-byte resource header, a byte count, then its payload.
    uint8_t bytes[72] = {
        [4] = 'B', 'L', 'B', 'O',
        [64] = 4, 0, 0, 0,
        [68] = 0xE0, 0, 0x5F, 0,
    };
    ResourceEntry entry = { .bytes = bytes, .size = sizeof(bytes) };
    assert(ResourceData(&entry) == bytes + 68);
    assert(memcmp(ResourceData(&entry), "\xE0\x00\x5F\x00", 4) == 0);
}
