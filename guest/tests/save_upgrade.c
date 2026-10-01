#include "../src/shipwright_save.c"

SaveContext gSaveContext;
static u8 original[0x21CF8];

__attribute__((export_name("test_upgrade")))
int TestUpgrade(void) {
    u8* bytes = (u8*)&gSaveContext;
    for (size_t i = 0; i < sizeof(original); ++i) original[i] = bytes[i] = (u8)(i * 31);
    if (!UpgradeSave(&gSaveContext, sizeof(original))) return 1;
    if (memcmp(bytes, original, 0x1870) != 0) return 2;
    if (gSaveContext.ship.stats.itemTimestamp[239] || gSaveContext.ship.stats.itemTimestamp[240]) return 3;
    const size_t flagsOffset = offsetof(SaveContext, ship.randomizerInf);
    if (memcmp(bytes + 0x1878, original + 0x1870, flagsOffset - 0x1878) != 0) return 4;
    const u16* oldFlags = (const u16*)(original + flagsOffset - 8);
    for (int bit = 0; bit < RAND_INF_MAX; ++bit) {
        int source = bit - (bit >= RAND_INF_LH_SCARECROWS_SONG) - (bit >= RAND_INF_HAS_SCARECROWS_SONG);
        bool expected = bit != RAND_INF_LH_SCARECROWS_SONG && bit != RAND_INF_HAS_SCARECROWS_SONG &&
                        ((oldFlags[source / 16] >> (source % 16)) & 1);
        if (((gSaveContext.ship.randomizerInf[bit / 16] >> (bit % 16)) & 1) != expected) return 5;
    }
    const size_t end = flagsOffset + sizeof(gSaveContext.ship.randomizerInf);
    if (memcmp(bytes + end, original + end - 8, sizeof(gSaveContext) - end) != 0) return 6;
    if (!UpgradeSave(&gSaveContext, sizeof(gSaveContext))) return 7;
    if (UpgradeSave(&gSaveContext, 1) || UpgradeSave(&gSaveContext, -1)) return 8;
    return 0;
}
