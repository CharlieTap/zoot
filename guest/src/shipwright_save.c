#include "global.h"
#include "soh/SaveManager.h"

#include <fcntl.h>
#include <stddef.h>
#include <string.h>
#include <sys/stat.h>
#include <unistd.h>

static SaveContext saveSlots[3];
static SaveFileMetaInfo saveMetadata[3];
static bool saveSlotValid[3];

static const char* const savePaths[] = {
    "/saves/save-0.bin", "/saves/save-1.bin", "/saves/save-2.bin",
};

static bool UpgradeSave(SaveContext* save, int32_t length) {
    if (length == sizeof(*save)) return true;
    // Before the September 2026 upstream update: 239 timestamps and two fewer flags.
    const size_t oldSize = 0x21CF8;
    const size_t oldTail = 0x1870;
    const size_t added = 2 * sizeof(u32);
    _Static_assert(sizeof(SaveContext) == 0x21D00, "Review raw save compatibility when updating Shipwright");
    _Static_assert(offsetof(SaveContext, ship.stats.itemTimestamp) + 239 * sizeof(u32) == 0x1870,
                   "Legacy timestamps must keep their offsets");
    if (length != oldSize) return false;
    u8* bytes = (u8*)save;
    memmove(bytes + oldTail + added, bytes + oldTail, oldSize - oldTail);
    memset(bytes + oldTail, 0, added);
    u16* flags = save->ship.randomizerInf;
    for (int bit = RAND_INF_MAX - 1; bit >= RAND_INF_LH_SCARECROWS_SONG; --bit) {
        const int source = bit - 1 - (bit >= RAND_INF_HAS_SCARECROWS_SONG);
        const bool value = bit != RAND_INF_LH_SCARECROWS_SONG && bit != RAND_INF_HAS_SCARECROWS_SONG &&
                           ((flags[source / 16] >> (source % 16)) & 1);
        flags[bit / 16] = (flags[bit / 16] & ~(1u << (bit % 16))) | ((u16)value << (bit % 16));
    }
    return true;
}

static int32_t ReadSave(uint32_t slot, void* destination, uint32_t capacity) {
    const int fd = open(savePaths[slot], O_RDONLY);
    if (fd < 0) return -1;

    struct stat info;
    if (fstat(fd, &info) != 0 || info.st_size > capacity) {
        close(fd);
        return -1;
    }

    uint32_t total = 0;
    while (total < capacity) {
        const ssize_t count = read(fd, (char*)destination + total, capacity - total);
        if (count < 0) {
            close(fd);
            return -1;
        }
        if (count == 0) break;
        total += count;
    }
    close(fd);
    return total;
}

static int32_t WriteSave(uint32_t slot, const void* source, uint32_t length) {
    const int fd = open(savePaths[slot], O_WRONLY | O_CREAT | O_TRUNC, 0600);
    if (fd < 0) return -1;

    uint32_t total = 0;
    while (total < length) {
        const ssize_t count = write(fd, (const char*)source + total, length - total);
        if (count <= 0) {
            close(fd);
            return -1;
        }
        total += count;
    }
    return close(fd) == 0 ? (int32_t)total : -1;
}

static void InitNormalSave(void) {
    const u8 fileNum = gSaveContext.fileNum;
    const u8 language = gSaveContext.language;
    const u8 audioSetting = gSaveContext.audioSetting;
    const u8 zTargetSetting = gSaveContext.zTargetSetting;
    const s16 gameMode = gSaveContext.gameMode;
    const s32 linkAge = gSaveContext.linkAge;

    memset(&gSaveContext, 0, sizeof(gSaveContext));
    gSaveContext.fileNum = fileNum;
    gSaveContext.language = language;
    gSaveContext.audioSetting = audioSetting;
    gSaveContext.zTargetSetting = zTargetSetting;
    gSaveContext.gameMode = gameMode;
    gSaveContext.linkAge = linkAge;
    gSaveContext.seqId = (u8)NA_BGM_DISABLED;
    gSaveContext.natureAmbienceId = NATURE_ID_DISABLED;
    gSaveContext.forcedSeqId = NA_BGM_GENERAL_SFX;
    gSaveContext.nextCutsceneIndex = 0xFFEF;
    gSaveContext.nextDayTime = 0xFFFF;
    gSaveContext.dogIsLost = true;
    gSaveContext.nextTransitionType = TRANS_NEXT_TYPE_DEFAULT;
    gSaveContext.prevHudVisibilityMode = 50;

    memset(gSaveContext.playerName, 0xDF, sizeof(gSaveContext.playerName));
    gSaveContext.healthCapacity = 0x30;
    gSaveContext.health = 0x30;
    gSaveContext.magic = 0x30;
    gSaveContext.savedSceneNum = SCENE_LINKS_HOUSE;

    memset(gSaveContext.childEquips.buttonItems, ITEM_NONE, sizeof(gSaveContext.childEquips.buttonItems));
    memset(gSaveContext.childEquips.cButtonSlots, SLOT_NONE, sizeof(gSaveContext.childEquips.cButtonSlots));
    memset(gSaveContext.adultEquips.buttonItems, ITEM_NONE, sizeof(gSaveContext.adultEquips.buttonItems));
    memset(gSaveContext.adultEquips.cButtonSlots, SLOT_NONE, sizeof(gSaveContext.adultEquips.cButtonSlots));
    memset(gSaveContext.equips.buttonItems, ITEM_NONE, sizeof(gSaveContext.equips.buttonItems));
    memset(gSaveContext.equips.cButtonSlots, SLOT_NONE, sizeof(gSaveContext.equips.cButtonSlots));
    gSaveContext.equips.equipment = 0x1100;

    memset(gSaveContext.inventory.items, ITEM_NONE, sizeof(gSaveContext.inventory.items));
    memset(gSaveContext.inventory.dungeonKeys, 0xFF, sizeof(gSaveContext.inventory.dungeonKeys));
    gSaveContext.inventory.equipment = 0x1100;

    gSaveContext.horseData.scene = SCENE_HYRULE_FIELD;
    gSaveContext.horseData.pos.x = -1840;
    gSaveContext.horseData.pos.y = 72;
    gSaveContext.horseData.pos.z = 5497;
    gSaveContext.horseData.angle = -0x6AD9;
    gSaveContext.infTable[29] = 1;
    gSaveContext.sceneFlags[5].swch = 0x40000000;
    gSaveContext.ship.backupFW = gSaveContext.fw;
    gSaveContext.ship.pendingSale = ITEM_NONE;
    gSaveContext.ship.pendingSaleMod = MOD_NONE;
    gSaveContext.ship.maskMemory = PLAYER_MASK_NONE;
    gSaveContext.ship.quest.id = QUEST_NORMAL;
}

static void InitDebugSave(void) {
    static const u8 playerName[] = { 0xB6, 0xB3, 0xB8, 0xB5, 0xDF, 0xDF, 0xDF, 0xDF };
    static const u8 buttonItems[] = { ITEM_SWORD_MASTER, ITEM_BOW,  ITEM_BOMB, ITEM_OCARINA_FAIRY,
                                      ITEM_NONE,         ITEM_NONE, ITEM_NONE, ITEM_NONE };
    static const u8 cButtonSlots[] = { SLOT_BOW, SLOT_BOMB, SLOT_OCARINA, SLOT_NONE,
                                       SLOT_NONE, SLOT_NONE, SLOT_NONE };
    static const u8 items[] = {
        ITEM_STICK,     ITEM_NUT,           ITEM_BOMB,         ITEM_BOW,         ITEM_ARROW_FIRE,
        ITEM_DINS_FIRE, ITEM_SLINGSHOT,     ITEM_OCARINA_FAIRY, ITEM_BOMBCHU,      ITEM_HOOKSHOT,
        ITEM_ARROW_ICE, ITEM_FARORES_WIND,  ITEM_BOOMERANG,     ITEM_LENS,         ITEM_BEAN,
        ITEM_HAMMER,    ITEM_ARROW_LIGHT,   ITEM_NAYRUS_LOVE,   ITEM_BOTTLE,       ITEM_POTION_RED,
        ITEM_POTION_GREEN, ITEM_POTION_BLUE, ITEM_POCKET_EGG, ITEM_WEIRD_EGG,
    };
    static const s8 ammo[] = { 50, 50, 10, 30, 1, 1, 30, 1, 50, 1, 1, 1, 1, 1, 1, 1 };
    static const u8 dungeonItems[] = { 7, 7, 7, 7, 7, 7, 7, 7, 7, 7 };

    InitNormalSave();
    memcpy(gSaveContext.playerName, playerName, sizeof(playerName));
    gSaveContext.healthCapacity = 0xE0;
    gSaveContext.health = 0xE0;
    gSaveContext.magic = 0x30;
    gSaveContext.rupees = 150;
    gSaveContext.swordHealth = 8;
    gSaveContext.isMagicAcquired = true;
    gSaveContext.savedSceneNum = 0x51;

    memcpy(gSaveContext.equips.buttonItems, buttonItems, sizeof(buttonItems));
    memcpy(gSaveContext.equips.cButtonSlots, cButtonSlots, sizeof(cButtonSlots));
    gSaveContext.equips.equipment = 0x1122;
    memcpy(gSaveContext.inventory.items, items, sizeof(items));
    memcpy(gSaveContext.inventory.ammo, ammo, sizeof(ammo));
    gSaveContext.inventory.equipment = 0x7777;
    gSaveContext.inventory.upgrades = 0x125249;
    gSaveContext.inventory.questItems = 0x1E3FFFF;
    memcpy(gSaveContext.inventory.dungeonItems, dungeonItems, sizeof(dungeonItems));
    memset(gSaveContext.inventory.dungeonKeys, 8, sizeof(gSaveContext.inventory.dungeonKeys));

    gSaveContext.infTable[0] |= 0x5009;
    gSaveContext.infTable[29] = 0;
    gSaveContext.eventChkInf[0] |= 0x123F;
    gSaveContext.eventChkInf[8] |= 1;
    gSaveContext.eventChkInf[12] |= 0x10;
    if (gSaveContext.linkAge == 1) {
        gSaveContext.equips.buttonItems[0] = ITEM_SWORD_KOKIRI;
        gSaveContext.equips.buttonItems[1] = ITEM_SLINGSHOT;
        gSaveContext.equips.cButtonSlots[0] = SLOT_SLINGSHOT;
        gSaveContext.equips.equipment = (gSaveContext.equips.equipment & ~0xF) | EQUIP_VALUE_SWORD_KOKIRI;
        gSaveContext.equips.equipment =
            (gSaveContext.equips.equipment & ~(0xF << 4)) | (EQUIP_VALUE_SHIELD_DEKU << 4);
    }
    gSaveContext.entranceIndex = ENTR_HYRULE_FIELD_PAST_BRIDGE_SPAWN;
    gSaveContext.sceneFlags[5].swch = 0x40000000;
}

void Save_InitFile(int isDebug) {
    if (isDebug) {
        InitDebugSave();
    } else {
        InitNormalSave();
    }
}

static void UpdateSaveMetadata(int fileNum, const SaveContext* save) {
    SaveFileMetaInfo* metadata = &saveMetadata[fileNum];

    memset(metadata, 0, sizeof(*metadata));
    metadata->valid = true;
    metadata->deaths = save->deaths;
    memcpy(metadata->playerName, save->playerName, sizeof(metadata->playerName));
    metadata->healthCapacity = save->healthCapacity;
    metadata->questItems = save->inventory.questItems;
    metadata->defense = save->isDoubleDefenseAcquired;
    metadata->health = save->health;
    metadata->requiresMasterQuest = save->ship.quest.id == QUEST_MASTER;
    metadata->requiresOriginal = save->ship.quest.id == QUEST_NORMAL;
    metadata->randoSave = save->ship.quest.id == QUEST_RANDOMIZER;
    memcpy(metadata->inventoryItems, save->inventory.items, sizeof(metadata->inventoryItems));
    metadata->equipment = save->inventory.equipment;
    metadata->upgrades = save->inventory.upgrades;
    metadata->isMagicAcquired = save->isMagicAcquired;
    metadata->isDoubleMagicAcquired = save->isDoubleMagicAcquired;
    metadata->rupees = save->rupees;
    metadata->gsTokens = save->inventory.gsTokens;
    metadata->isDoubleDefenseAcquired = save->isDoubleDefenseAcquired;
    metadata->filenameLanguage = save->ship.filenameLanguage;
}

void Save_Init(void) {
    for (int fileNum = 0; fileNum < ARRAY_COUNT(saveSlots); fileNum++) {
        const int32_t bytes = ReadSave(fileNum, &saveSlots[fileNum], sizeof(saveSlots[fileNum]));
        saveSlotValid[fileNum] = UpgradeSave(&saveSlots[fileNum], bytes);
        if (saveSlotValid[fileNum]) {
            UpdateSaveMetadata(fileNum, &saveSlots[fileNum]);
        }
    }
}

SaveFileMetaInfo* Save_GetSaveMetaInfo(int fileNum) {
    if (fileNum < 0 || fileNum >= ARRAY_COUNT(saveMetadata)) {
        return &saveMetadata[0];
    }
    return &saveMetadata[fileNum];
}

void Save_SaveFile(void) {
    const int fileNum = gSaveContext.fileNum;
    if (fileNum < 0 || fileNum >= ARRAY_COUNT(saveSlots)) {
        return;
    }

    saveSlots[fileNum] = gSaveContext;
    saveSlotValid[fileNum] = true;
    UpdateSaveMetadata(fileNum, &saveSlots[fileNum]);
    WriteSave(fileNum, &saveSlots[fileNum], sizeof(saveSlots[fileNum]));
}

void Save_LoadFile(void) {
    const int fileNum = gSaveContext.fileNum;
    if (fileNum < 0 || fileNum >= ARRAY_COUNT(saveSlots) || !saveSlotValid[fileNum]) {
        return;
    }

    const s16 gameMode = gSaveContext.gameMode;
    const u8 language = gSaveContext.language;
    const u8 audioSetting = gSaveContext.audioSetting;
    const u8 zTargetSetting = gSaveContext.zTargetSetting;

    gSaveContext = saveSlots[fileNum];
    gSaveContext.fileNum = fileNum;
    gSaveContext.gameMode = gameMode;
    gSaveContext.language = language;
    gSaveContext.audioSetting = audioSetting;
    gSaveContext.zTargetSetting = zTargetSetting;
}

void Save_CopyFile(int from, int to) {
    if (from < 0 || from >= ARRAY_COUNT(saveSlots) || to < 0 || to >= ARRAY_COUNT(saveSlots) ||
        !saveSlotValid[from]) {
        return;
    }

    saveSlots[to] = saveSlots[from];
    saveSlots[to].fileNum = to;
    saveMetadata[to] = saveMetadata[from];
    saveSlotValid[to] = true;
    WriteSave(to, &saveSlots[to], sizeof(saveSlots[to]));
}

void Save_DeleteFile(int fileNum) {
    if (fileNum < 0 || fileNum >= ARRAY_COUNT(saveSlots)) {
        return;
    }

    memset(&saveSlots[fileNum], 0, sizeof(saveSlots[fileNum]));
    memset(&saveMetadata[fileNum], 0, sizeof(saveMetadata[fileNum]));
    saveSlotValid[fileNum] = false;
    WriteSave(fileNum, NULL, 0);
}

void Save_SaveGlobal(void) {
}
