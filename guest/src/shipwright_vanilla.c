#include "global.h"
#include "soh/Enhancements/game-interactor/GameInteractor.h"
#include "soh/Enhancements/game-interactor/GameInteractor_Hooks.h"
#include "soh/Enhancements/item-tables/ItemTableTypes.h"
#include "soh/Enhancements/randomizer/randomizerTypes.h"
#include "soh/Enhancements/randomizer/randomizerEnums/RandomizerMiscEnums.h"
#include "soh/GameVersions.h"
#include "soh/OTRGlobals.h"
#include "soh/ResourceManagerHelpers.h"
#include "soh/frame_interpolation.h"
#include "soh/Enhancements/FileSelectEnhancements.h"
#include <libultraship/bridge/resourcebridge.h>

extern int gMapLoading;

s32 Ship_GetActorSpawnObjectIndex(PlayState* play, s16 objectId, s16 actorId) {
    s32 index = Object_GetIndex(&play->objectCtx, objectId);
    if (index < 0 && GameInteractor_Should(VB_SPAWN_ACTOR_WITHOUT_OBJECT, !gMapLoading, actorId)) {
        return 0;
    }
    return index;
}

// The guest ships the original quest only, without the randomizer worker.
bool Randomizer_IsGenerating(void) { return false; }
void Randomizer_WaitForGeneration(void) {}
bool SohFileSelect_IsQuestHidden(u8 quest) { return quest != QUEST_NORMAL; }
u8 SohFileSelect_CountVisibleQuests(void) { return 1; }

__attribute__((import_module("oot_graphics"), import_name("prepare_depth_query")))
void Host_PrepareDepthQuery(float x, float y);

__attribute__((import_module("oot_graphics"), import_name("read_depth_query")))
uint32_t Host_ReadDepthQuery(float x, float y);

    static const GetItemEntry vanillaItemTable[] = {
        // clang-format off
        GET_ITEM(ITEM_BOMBS_5,          OBJECT_GI_BOMB_1,        GID_BOMB,             0x32, 0x59, CHEST_ANIM_SHORT, ITEM_CATEGORY_JUNK,            MOD_NONE, GI_BOMBS_5),
        GET_ITEM(ITEM_NUTS_5,           OBJECT_GI_NUTS,          GID_NUTS,             0x34, 0x0C, CHEST_ANIM_SHORT, ITEM_CATEGORY_JUNK,            MOD_NONE, GI_NUTS_5),
        GET_ITEM(ITEM_BOMBCHU,          OBJECT_GI_BOMB_2,        GID_BOMBCHU,          0x33, 0x80, CHEST_ANIM_SHORT, ITEM_CATEGORY_MAJOR,           MOD_NONE, GI_BOMBCHUS_10),
        GET_ITEM(ITEM_BOW,              OBJECT_GI_BOW,           GID_BOW,              0x31, 0x80, CHEST_ANIM_LONG,  ITEM_CATEGORY_MAJOR,           MOD_NONE, GI_BOW),
        GET_ITEM(ITEM_SLINGSHOT,        OBJECT_GI_PACHINKO,      GID_SLINGSHOT,        0x30, 0x80, CHEST_ANIM_LONG,  ITEM_CATEGORY_MAJOR,           MOD_NONE, GI_SLINGSHOT),
        GET_ITEM(ITEM_BOOMERANG,        OBJECT_GI_BOOMERANG,     GID_BOOMERANG,        0x35, 0x80, CHEST_ANIM_LONG,  ITEM_CATEGORY_MAJOR,           MOD_NONE, GI_BOOMERANG),
        GET_ITEM(ITEM_STICK,            OBJECT_GI_STICK,         GID_STICK,            0x37, 0x0D, CHEST_ANIM_SHORT, ITEM_CATEGORY_JUNK,            MOD_NONE, GI_STICKS_1),
        GET_ITEM(ITEM_HOOKSHOT,         OBJECT_GI_HOOKSHOT,      GID_HOOKSHOT,         0x36, 0x80, CHEST_ANIM_LONG,  ITEM_CATEGORY_MAJOR,           MOD_NONE, GI_HOOKSHOT),
        GET_ITEM(ITEM_LONGSHOT,         OBJECT_GI_HOOKSHOT,      GID_LONGSHOT,         0x4F, 0x80, CHEST_ANIM_LONG,  ITEM_CATEGORY_MAJOR,           MOD_NONE, GI_LONGSHOT),
        GET_ITEM(ITEM_LENS,             OBJECT_GI_GLASSES,       GID_LENS,             0x39, 0x80, CHEST_ANIM_LONG,  ITEM_CATEGORY_MAJOR,           MOD_NONE, GI_LENS),
        GET_ITEM(ITEM_LETTER_ZELDA,     OBJECT_GI_LETTER,        GID_LETTER_ZELDA,     0x69, 0x80, CHEST_ANIM_LONG,  ITEM_CATEGORY_MAJOR,           MOD_NONE, GI_LETTER_ZELDA),
        GET_ITEM(ITEM_OCARINA_TIME,     OBJECT_GI_OCARINA,       GID_OCARINA_TIME,     0x3A, 0x80, CHEST_ANIM_LONG,  ITEM_CATEGORY_MAJOR,           MOD_NONE, GI_OCARINA_OOT),
        GET_ITEM(ITEM_HAMMER,           OBJECT_GI_HAMMER,        GID_HAMMER,           0x38, 0x80, CHEST_ANIM_LONG,  ITEM_CATEGORY_MAJOR,           MOD_NONE, GI_HAMMER),
        GET_ITEM(ITEM_COJIRO,           OBJECT_GI_NIWATORI,      GID_COJIRO,           0x02, 0x80, CHEST_ANIM_LONG,  ITEM_CATEGORY_MAJOR,           MOD_NONE, GI_COJIRO),
        GET_ITEM(ITEM_BOTTLE,           OBJECT_GI_BOTTLE,        GID_BOTTLE,           0x42, 0x80, CHEST_ANIM_LONG,  ITEM_CATEGORY_MAJOR,           MOD_NONE, GI_BOTTLE),
        GET_ITEM(ITEM_POTION_RED,       OBJECT_GI_LIQUID,        GID_POTION_RED,       0x43, 0x80, CHEST_ANIM_LONG,  ITEM_CATEGORY_JUNK,            MOD_NONE, GI_POTION_RED),
        GET_ITEM(ITEM_POTION_GREEN,     OBJECT_GI_LIQUID,        GID_POTION_GREEN,     0x44, 0x80, CHEST_ANIM_LONG,  ITEM_CATEGORY_JUNK,            MOD_NONE, GI_POTION_GREEN),
        GET_ITEM(ITEM_POTION_BLUE,      OBJECT_GI_LIQUID,        GID_POTION_BLUE,      0x45, 0x80, CHEST_ANIM_LONG,  ITEM_CATEGORY_JUNK,            MOD_NONE, GI_POTION_BLUE),
        GET_ITEM(ITEM_FAIRY,            OBJECT_GI_BOTTLE,        GID_BOTTLE,           0x46, 0x80, CHEST_ANIM_LONG,  ITEM_CATEGORY_JUNK,            MOD_NONE, GI_FAIRY),
        GET_ITEM(ITEM_MILK_BOTTLE,      OBJECT_GI_MILK,          GID_MILK,             0x98, 0x80, CHEST_ANIM_LONG,  ITEM_CATEGORY_MAJOR,           MOD_NONE, GI_MILK_BOTTLE),
        GET_ITEM(ITEM_LETTER_RUTO,      OBJECT_GI_BOTTLE_LETTER, GID_LETTER_RUTO,      0x99, 0x80, CHEST_ANIM_LONG,  ITEM_CATEGORY_MAJOR,           MOD_NONE, GI_LETTER_RUTO),
        GET_ITEM(ITEM_BEAN,             OBJECT_GI_BEAN,          GID_BEAN,             0x48, 0x80, CHEST_ANIM_SHORT, ITEM_CATEGORY_MAJOR,           MOD_NONE, GI_BEAN),
        GET_ITEM(ITEM_MASK_SKULL,       OBJECT_GI_SKJ_MASK,      GID_MASK_SKULL,       0x10, 0x80, CHEST_ANIM_LONG,  ITEM_CATEGORY_MAJOR,           MOD_NONE, GI_MASK_SKULL),
        GET_ITEM(ITEM_MASK_SPOOKY,      OBJECT_GI_REDEAD_MASK,   GID_MASK_SPOOKY,      0x11, 0x80, CHEST_ANIM_LONG,  ITEM_CATEGORY_MAJOR,           MOD_NONE, GI_MASK_SPOOKY),
        GET_ITEM(ITEM_CHICKEN,          OBJECT_GI_NIWATORI,      GID_CHICKEN,          0x48, 0x80, CHEST_ANIM_LONG,  ITEM_CATEGORY_MAJOR,           MOD_NONE, GI_CHICKEN),
        GET_ITEM(ITEM_MASK_KEATON,      OBJECT_GI_KI_TAN_MASK,   GID_MASK_KEATON,      0x12, 0x80, CHEST_ANIM_LONG,  ITEM_CATEGORY_MAJOR,           MOD_NONE, GI_MASK_KEATON),
        GET_ITEM(ITEM_MASK_BUNNY,       OBJECT_GI_RABIT_MASK,    GID_MASK_BUNNY,       0x13, 0x80, CHEST_ANIM_LONG,  ITEM_CATEGORY_MAJOR,           MOD_NONE, GI_MASK_BUNNY),
        GET_ITEM(ITEM_MASK_TRUTH,       OBJECT_GI_TRUTH_MASK,    GID_MASK_TRUTH,       0x17, 0x80, CHEST_ANIM_LONG,  ITEM_CATEGORY_MAJOR,           MOD_NONE, GI_MASK_TRUTH),
        GET_ITEM(ITEM_POCKET_EGG,       OBJECT_GI_EGG,           GID_EGG,              0x01, 0x80, CHEST_ANIM_LONG,  ITEM_CATEGORY_MAJOR,           MOD_NONE, GI_POCKET_EGG),
        GET_ITEM(ITEM_POCKET_CUCCO,     OBJECT_GI_NIWATORI,      GID_CHICKEN,          0x48, 0x80, CHEST_ANIM_LONG,  ITEM_CATEGORY_MAJOR,           MOD_NONE, GI_POCKET_CUCCO),
        GET_ITEM(ITEM_ODD_MUSHROOM,     OBJECT_GI_MUSHROOM,      GID_ODD_MUSHROOM,     0x03, 0x80, CHEST_ANIM_LONG,  ITEM_CATEGORY_MAJOR,           MOD_NONE, GI_ODD_MUSHROOM),
        GET_ITEM(ITEM_ODD_POTION,       OBJECT_GI_POWDER,        GID_ODD_POTION,       0x04, 0x80, CHEST_ANIM_LONG,  ITEM_CATEGORY_MAJOR,           MOD_NONE, GI_ODD_POTION),
        GET_ITEM(ITEM_SAW,              OBJECT_GI_SAW,           GID_SAW,              0x05, 0x80, CHEST_ANIM_LONG,  ITEM_CATEGORY_MAJOR,           MOD_NONE, GI_SAW),
        GET_ITEM(ITEM_SWORD_BROKEN,     OBJECT_GI_BROKENSWORD,   GID_SWORD_BROKEN,     0x08, 0x80, CHEST_ANIM_LONG,  ITEM_CATEGORY_MAJOR,           MOD_NONE, GI_SWORD_BROKEN),
        GET_ITEM(ITEM_PRESCRIPTION,     OBJECT_GI_PRESCRIPTION,  GID_PRESCRIPTION,     0x09, 0x80, CHEST_ANIM_LONG,  ITEM_CATEGORY_MAJOR,           MOD_NONE, GI_PRESCRIPTION),
        GET_ITEM(ITEM_FROG,             OBJECT_GI_FROG,          GID_FROG,             0x0D, 0x80, CHEST_ANIM_LONG,  ITEM_CATEGORY_MAJOR,           MOD_NONE, GI_FROG),
        GET_ITEM(ITEM_EYEDROPS,         OBJECT_GI_EYE_LOTION,    GID_EYEDROPS,         0x0E, 0x80, CHEST_ANIM_LONG,  ITEM_CATEGORY_MAJOR,           MOD_NONE, GI_EYEDROPS),
        GET_ITEM(ITEM_CLAIM_CHECK,      OBJECT_GI_TICKETSTONE,   GID_CLAIM_CHECK,      0x0A, 0x80, CHEST_ANIM_LONG,  ITEM_CATEGORY_MAJOR,           MOD_NONE, GI_CLAIM_CHECK),
        GET_ITEM(ITEM_SWORD_KOKIRI,     OBJECT_GI_SWORD_1,       GID_SWORD_KOKIRI,     0xA4, 0x80, CHEST_ANIM_LONG,  ITEM_CATEGORY_MAJOR,           MOD_NONE, GI_SWORD_KOKIRI),
        GET_ITEM(ITEM_SWORD_BGS,        OBJECT_GI_LONGSWORD,     GID_SWORD_BGS,        0x4B, 0x80, CHEST_ANIM_LONG,  ITEM_CATEGORY_MAJOR,           MOD_NONE, GI_SWORD_KNIFE),
        GET_ITEM(ITEM_SHIELD_DEKU,      OBJECT_GI_SHIELD_1,      GID_SHIELD_DEKU,      0x4C, 0xA0, CHEST_ANIM_SHORT, ITEM_CATEGORY_LESSER,          MOD_NONE, GI_SHIELD_DEKU),
        GET_ITEM(ITEM_SHIELD_HYLIAN,    OBJECT_GI_SHIELD_2,      GID_SHIELD_HYLIAN,    0x4D, 0xA0, CHEST_ANIM_SHORT, ITEM_CATEGORY_LESSER,          MOD_NONE, GI_SHIELD_HYLIAN),
        GET_ITEM(ITEM_SHIELD_MIRROR,    OBJECT_GI_SHIELD_3,      GID_SHIELD_MIRROR,    0x4E, 0x80, CHEST_ANIM_LONG,  ITEM_CATEGORY_MAJOR,           MOD_NONE, GI_SHIELD_MIRROR),
        GET_ITEM(ITEM_TUNIC_GORON,      OBJECT_GI_CLOTHES,       GID_TUNIC_GORON,      0x50, 0xA0, CHEST_ANIM_LONG,  ITEM_CATEGORY_LESSER,          MOD_NONE, GI_TUNIC_GORON),
        GET_ITEM(ITEM_TUNIC_ZORA,       OBJECT_GI_CLOTHES,       GID_TUNIC_ZORA,       0x51, 0xA0, CHEST_ANIM_LONG,  ITEM_CATEGORY_LESSER,          MOD_NONE, GI_TUNIC_ZORA),
        GET_ITEM(ITEM_BOOTS_IRON,       OBJECT_GI_BOOTS_2,       GID_BOOTS_IRON,       0x53, 0x80, CHEST_ANIM_LONG,  ITEM_CATEGORY_MAJOR,           MOD_NONE, GI_BOOTS_IRON),
        GET_ITEM(ITEM_BOOTS_HOVER,      OBJECT_GI_HOVERBOOTS,    GID_BOOTS_HOVER,      0x54, 0x80, CHEST_ANIM_LONG,  ITEM_CATEGORY_MAJOR,           MOD_NONE, GI_BOOTS_HOVER),
        GET_ITEM(ITEM_QUIVER_40,        OBJECT_GI_ARROWCASE,     GID_QUIVER_40,        0x56, 0x80, CHEST_ANIM_LONG,  ITEM_CATEGORY_LESSER,          MOD_NONE, GI_QUIVER_40),
        GET_ITEM(ITEM_QUIVER_50,        OBJECT_GI_ARROWCASE,     GID_QUIVER_50,        0x57, 0x80, CHEST_ANIM_LONG,  ITEM_CATEGORY_LESSER,          MOD_NONE, GI_QUIVER_50),
        GET_ITEM(ITEM_BOMB_BAG_20,      OBJECT_GI_BOMBPOUCH,     GID_BOMB_BAG_20,      0x58, 0x80, CHEST_ANIM_LONG,  ITEM_CATEGORY_MAJOR,           MOD_NONE, GI_BOMB_BAG_20),
        GET_ITEM(ITEM_BOMB_BAG_30,      OBJECT_GI_BOMBPOUCH,     GID_BOMB_BAG_30,      0x59, 0x80, CHEST_ANIM_LONG,  ITEM_CATEGORY_LESSER,          MOD_NONE, GI_BOMB_BAG_30),
        GET_ITEM(ITEM_BOMB_BAG_40,      OBJECT_GI_BOMBPOUCH,     GID_BOMB_BAG_40,      0x5A, 0x80, CHEST_ANIM_LONG,  ITEM_CATEGORY_LESSER,          MOD_NONE, GI_BOMB_BAG_40),
        GET_ITEM(ITEM_GAUNTLETS_SILVER, OBJECT_GI_GLOVES,        GID_GAUNTLETS_SILVER, 0x5B, 0x80, CHEST_ANIM_LONG,  ITEM_CATEGORY_MAJOR,           MOD_NONE, GI_GAUNTLETS_SILVER),
        GET_ITEM(ITEM_GAUNTLETS_GOLD,   OBJECT_GI_GLOVES,        GID_GAUNTLETS_GOLD,   0x5C, 0x80, CHEST_ANIM_LONG,  ITEM_CATEGORY_MAJOR,           MOD_NONE, GI_GAUNTLETS_GOLD),
        GET_ITEM(ITEM_SCALE_SILVER,     OBJECT_GI_SCALE,         GID_SCALE_SILVER,     0xCD, 0x80, CHEST_ANIM_LONG,  ITEM_CATEGORY_MAJOR,           MOD_NONE, GI_SCALE_SILVER),
        GET_ITEM(ITEM_SCALE_GOLDEN,     OBJECT_GI_SCALE,         GID_SCALE_GOLDEN,     0xCE, 0x80, CHEST_ANIM_LONG,  ITEM_CATEGORY_MAJOR,           MOD_NONE, GI_SCALE_GOLDEN),
        GET_ITEM(ITEM_STONE_OF_AGONY,   OBJECT_GI_MAP,           GID_STONE_OF_AGONY,   0x68, 0x80, CHEST_ANIM_LONG,  ITEM_CATEGORY_MAJOR,           MOD_NONE, GI_STONE_OF_AGONY),
        GET_ITEM(ITEM_GERUDO_CARD,      OBJECT_GI_GERUDO,        GID_GERUDO_CARD,      0x7B, 0x80, CHEST_ANIM_LONG,  ITEM_CATEGORY_MAJOR,           MOD_NONE, GI_GERUDO_CARD),
        GET_ITEM(ITEM_OCARINA_FAIRY,    OBJECT_GI_OCARINA_0,     GID_OCARINA_FAIRY,    0x4A, 0x80, CHEST_ANIM_LONG,  ITEM_CATEGORY_MAJOR,           MOD_NONE, GI_OCARINA_FAIRY),
        GET_ITEM(ITEM_SEEDS,            OBJECT_GI_SEED,          GID_SEEDS,            0xDC, 0x50, CHEST_ANIM_SHORT, ITEM_CATEGORY_JUNK,            MOD_NONE, GI_SEEDS_5),
        GET_ITEM(ITEM_HEART_CONTAINER,  OBJECT_GI_HEARTS,        GID_HEART_CONTAINER,  0xC6, 0x80, CHEST_ANIM_LONG,  ITEM_CATEGORY_HEALTH,          MOD_NONE, GI_HEART_CONTAINER),
        GET_ITEM(ITEM_HEART_PIECE_2,    OBJECT_GI_HEARTS,        GID_HEART_PIECE,      0xC2, 0x80, CHEST_ANIM_LONG,  ITEM_CATEGORY_HEALTH,          MOD_NONE, GI_HEART_PIECE),
        GET_ITEM(ITEM_KEY_BOSS,         OBJECT_GI_BOSSKEY,       GID_KEY_BOSS,         0xC7, 0x80, CHEST_ANIM_LONG,  ITEM_CATEGORY_BOSS_KEY,        MOD_NONE, GI_KEY_BOSS),
        GET_ITEM(ITEM_COMPASS,          OBJECT_GI_COMPASS,       GID_COMPASS,          0x67, 0x80, CHEST_ANIM_LONG,  ITEM_CATEGORY_LESSER,          MOD_NONE, GI_COMPASS),
        GET_ITEM(ITEM_DUNGEON_MAP,      OBJECT_GI_MAP,           GID_DUNGEON_MAP,      0x66, 0x80, CHEST_ANIM_LONG,  ITEM_CATEGORY_LESSER,          MOD_NONE, GI_MAP),
        GET_ITEM(ITEM_KEY_SMALL,        OBJECT_GI_KEY,           GID_KEY_SMALL,        0x60, 0x80, CHEST_ANIM_SHORT, ITEM_CATEGORY_SMALL_KEY,       MOD_NONE, GI_KEY_SMALL),
        GET_ITEM(ITEM_MAGIC_SMALL,      OBJECT_GI_MAGICPOT,      GID_MAGIC_SMALL,      0x52, 0x6F, CHEST_ANIM_SHORT, ITEM_CATEGORY_JUNK,            MOD_NONE, GI_MAGIC_SMALL),
        GET_ITEM(ITEM_MAGIC_LARGE,      OBJECT_GI_MAGICPOT,      GID_MAGIC_LARGE,      0x52, 0x6E, CHEST_ANIM_SHORT, ITEM_CATEGORY_JUNK,            MOD_NONE, GI_MAGIC_LARGE),
        GET_ITEM(ITEM_WALLET_ADULT,     OBJECT_GI_PURSE,         GID_WALLET_ADULT,     0x5E, 0x80, CHEST_ANIM_LONG,  ITEM_CATEGORY_MAJOR,           MOD_NONE, GI_WALLET_ADULT),
        GET_ITEM(ITEM_WALLET_GIANT,     OBJECT_GI_PURSE,         GID_WALLET_GIANT,     0x5F, 0x80, CHEST_ANIM_LONG,  ITEM_CATEGORY_MAJOR,           MOD_NONE, GI_WALLET_GIANT),
        GET_ITEM(ITEM_WEIRD_EGG,        OBJECT_GI_EGG,           GID_EGG,              0x9A, 0x80, CHEST_ANIM_LONG,  ITEM_CATEGORY_MAJOR,           MOD_NONE, GI_WEIRD_EGG),
        GET_ITEM(ITEM_HEART,            OBJECT_GI_HEART,         GID_HEART,            0x55, 0x80, CHEST_ANIM_LONG,  ITEM_CATEGORY_JUNK,            MOD_NONE, GI_HEART),
        GET_ITEM(ITEM_ARROWS_SMALL,     OBJECT_GI_ARROW,         GID_ARROWS_SMALL,     0xE6, 0x48, CHEST_ANIM_SHORT, ITEM_CATEGORY_JUNK,            MOD_NONE, GI_ARROWS_SMALL),
        GET_ITEM(ITEM_ARROWS_MEDIUM,    OBJECT_GI_ARROW,         GID_ARROWS_MEDIUM,    0xE6, 0x49, CHEST_ANIM_SHORT, ITEM_CATEGORY_JUNK,            MOD_NONE, GI_ARROWS_MEDIUM),
        GET_ITEM(ITEM_ARROWS_LARGE,     OBJECT_GI_ARROW,         GID_ARROWS_LARGE,     0xE6, 0x4A, CHEST_ANIM_SHORT, ITEM_CATEGORY_JUNK,            MOD_NONE, GI_ARROWS_LARGE),
        GET_ITEM(ITEM_RUPEE_GREEN,      OBJECT_GI_RUPY,          GID_RUPEE_GREEN,      0x6F, 0x00, CHEST_ANIM_SHORT, ITEM_CATEGORY_JUNK,            MOD_NONE, GI_RUPEE_GREEN),
        GET_ITEM(ITEM_RUPEE_BLUE,       OBJECT_GI_RUPY,          GID_RUPEE_BLUE,       0xCC, 0x01, CHEST_ANIM_SHORT, ITEM_CATEGORY_JUNK,            MOD_NONE, GI_RUPEE_BLUE),
        GET_ITEM(ITEM_RUPEE_RED,        OBJECT_GI_RUPY,          GID_RUPEE_RED,        0xF0, 0x02, CHEST_ANIM_SHORT, ITEM_CATEGORY_JUNK,            MOD_NONE, GI_RUPEE_RED),
        GET_ITEM(ITEM_HEART_CONTAINER,  OBJECT_GI_HEARTS,        GID_HEART_CONTAINER,  0xC6, 0x80, CHEST_ANIM_LONG,  ITEM_CATEGORY_HEALTH,          MOD_NONE, GI_HEART_CONTAINER_2),
        GET_ITEM(ITEM_MILK,             OBJECT_GI_MILK,          GID_MILK,             0x98, 0x80, CHEST_ANIM_LONG,  ITEM_CATEGORY_JUNK,            MOD_NONE, GI_MILK),
        GET_ITEM(ITEM_MASK_GORON,       OBJECT_GI_GOLONMASK,     GID_MASK_GORON,       0x14, 0x80, CHEST_ANIM_LONG,  ITEM_CATEGORY_MAJOR,           MOD_NONE, GI_MASK_GORON),
        GET_ITEM(ITEM_MASK_ZORA,        OBJECT_GI_ZORAMASK,      GID_MASK_ZORA,        0x15, 0x80, CHEST_ANIM_LONG,  ITEM_CATEGORY_MAJOR,           MOD_NONE, GI_MASK_ZORA),
        GET_ITEM(ITEM_MASK_GERUDO,      OBJECT_GI_GERUDOMASK,    GID_MASK_GERUDO,      0x16, 0x80, CHEST_ANIM_LONG,  ITEM_CATEGORY_MAJOR,           MOD_NONE, GI_MASK_GERUDO),
        GET_ITEM(ITEM_BRACELET,         OBJECT_GI_BRACELET,      GID_BRACELET,         0x79, 0x80, CHEST_ANIM_LONG,  ITEM_CATEGORY_MAJOR,           MOD_NONE, GI_BRACELET),
        GET_ITEM(ITEM_RUPEE_PURPLE,     OBJECT_GI_RUPY,          GID_RUPEE_PURPLE,     0xF1, 0x14, CHEST_ANIM_SHORT, ITEM_CATEGORY_JUNK,            MOD_NONE, GI_RUPEE_PURPLE),
        GET_ITEM(ITEM_RUPEE_GOLD,       OBJECT_GI_RUPY,          GID_RUPEE_GOLD,       0xF2, 0x13, CHEST_ANIM_SHORT, ITEM_CATEGORY_LESSER,          MOD_NONE, GI_RUPEE_GOLD),
        GET_ITEM(ITEM_SWORD_BGS,        OBJECT_GI_LONGSWORD,     GID_SWORD_BGS,        0x0C, 0x80, CHEST_ANIM_LONG,  ITEM_CATEGORY_MAJOR,           MOD_NONE, GI_SWORD_BGS),
        GET_ITEM(ITEM_ARROW_FIRE,       OBJECT_GI_M_ARROW,       GID_ARROW_FIRE,       0x70, 0x80, CHEST_ANIM_LONG,  ITEM_CATEGORY_MAJOR,           MOD_NONE, GI_ARROW_FIRE),
        GET_ITEM(ITEM_ARROW_ICE,        OBJECT_GI_M_ARROW,       GID_ARROW_ICE,        0x71, 0x80, CHEST_ANIM_LONG,  ITEM_CATEGORY_MAJOR,           MOD_NONE, GI_ARROW_ICE),
        GET_ITEM(ITEM_ARROW_LIGHT,      OBJECT_GI_M_ARROW,       GID_ARROW_LIGHT,      0x72, 0x80, CHEST_ANIM_LONG,  ITEM_CATEGORY_MAJOR,           MOD_NONE, GI_ARROW_LIGHT),
        GET_ITEM(ITEM_SKULL_TOKEN,      OBJECT_GI_SUTARU,        GID_SKULL_TOKEN,      0xB4, 0x80, CHEST_ANIM_SHORT, ITEM_CATEGORY_SKULLTULA_TOKEN, MOD_NONE, GI_SKULL_TOKEN),
        GET_ITEM(ITEM_DINS_FIRE,        OBJECT_GI_GODDESS,       GID_DINS_FIRE,        0xAD, 0x80, CHEST_ANIM_LONG,  ITEM_CATEGORY_MAJOR,           MOD_NONE, GI_DINS_FIRE),
        GET_ITEM(ITEM_FARORES_WIND,     OBJECT_GI_GODDESS,       GID_FARORES_WIND,     0xAE, 0x80, CHEST_ANIM_LONG,  ITEM_CATEGORY_MAJOR,           MOD_NONE, GI_FARORES_WIND),
        GET_ITEM(ITEM_NAYRUS_LOVE,      OBJECT_GI_GODDESS,       GID_NAYRUS_LOVE,      0xAF, 0x80, CHEST_ANIM_LONG,  ITEM_CATEGORY_MAJOR,           MOD_NONE, GI_NAYRUS_LOVE),
        GET_ITEM(ITEM_BULLET_BAG_30,    OBJECT_GI_DEKUPOUCH,     GID_BULLET_BAG,       0x07, 0x80, CHEST_ANIM_LONG,  ITEM_CATEGORY_LESSER,          MOD_NONE, GI_BULLET_BAG_30),
        GET_ITEM(ITEM_BULLET_BAG_40,    OBJECT_GI_DEKUPOUCH,     GID_BULLET_BAG,       0x07, 0x80, CHEST_ANIM_LONG,  ITEM_CATEGORY_LESSER,          MOD_NONE, GI_BULLET_BAG_40),
        GET_ITEM(ITEM_STICKS_5,         OBJECT_GI_STICK,         GID_STICK,            0x37, 0x0D, CHEST_ANIM_SHORT, ITEM_CATEGORY_JUNK,            MOD_NONE, GI_STICKS_5),
        GET_ITEM(ITEM_STICKS_10,        OBJECT_GI_STICK,         GID_STICK,            0x37, 0x0D, CHEST_ANIM_SHORT, ITEM_CATEGORY_JUNK,            MOD_NONE, GI_STICKS_10),
        GET_ITEM(ITEM_NUTS_5,           OBJECT_GI_NUTS,          GID_NUTS,             0x34, 0x0C, CHEST_ANIM_SHORT, ITEM_CATEGORY_JUNK,            MOD_NONE, GI_NUTS_5_2),
        GET_ITEM(ITEM_NUTS_10,          OBJECT_GI_NUTS,          GID_NUTS,             0x34, 0x0C, CHEST_ANIM_SHORT, ITEM_CATEGORY_JUNK,            MOD_NONE, GI_NUTS_10),
        GET_ITEM(ITEM_BOMB,             OBJECT_GI_BOMB_1,        GID_BOMB,             0x32, 0x59, CHEST_ANIM_SHORT, ITEM_CATEGORY_JUNK,            MOD_NONE, GI_BOMBS_1),
        GET_ITEM(ITEM_BOMBS_10,         OBJECT_GI_BOMB_1,        GID_BOMB,             0x32, 0x59, CHEST_ANIM_SHORT, ITEM_CATEGORY_JUNK,            MOD_NONE, GI_BOMBS_10),
        GET_ITEM(ITEM_BOMBS_20,         OBJECT_GI_BOMB_1,        GID_BOMB,             0x32, 0x59, CHEST_ANIM_SHORT, ITEM_CATEGORY_JUNK,            MOD_NONE, GI_BOMBS_20),
        GET_ITEM(ITEM_BOMBS_30,         OBJECT_GI_BOMB_1,        GID_BOMB,             0x32, 0x59, CHEST_ANIM_SHORT, ITEM_CATEGORY_JUNK,            MOD_NONE, GI_BOMBS_30),
        GET_ITEM(ITEM_SEEDS_30,         OBJECT_GI_SEED,          GID_SEEDS,            0xDC, 0x50, CHEST_ANIM_SHORT, ITEM_CATEGORY_JUNK,            MOD_NONE, GI_SEEDS_30),
        GET_ITEM(ITEM_BOMBCHUS_5,       OBJECT_GI_BOMB_2,        GID_BOMBCHU,          0x33, 0x80, CHEST_ANIM_SHORT, ITEM_CATEGORY_MAJOR,           MOD_NONE, GI_BOMBCHUS_5),
        GET_ITEM(ITEM_BOMBCHUS_20,      OBJECT_GI_BOMB_2,        GID_BOMBCHU,          0x33, 0x80, CHEST_ANIM_SHORT, ITEM_CATEGORY_MAJOR,           MOD_NONE, GI_BOMBCHUS_20),
        GET_ITEM(ITEM_FISH,             OBJECT_GI_FISH,          GID_FISH,             0x47, 0x80, CHEST_ANIM_LONG,  ITEM_CATEGORY_JUNK,            MOD_NONE, GI_FISH),
        GET_ITEM(ITEM_BUG,              OBJECT_GI_INSECT,        GID_BUG,              0x7A, 0x80, CHEST_ANIM_LONG,  ITEM_CATEGORY_JUNK,            MOD_NONE, GI_BUGS),
        GET_ITEM(ITEM_BLUE_FIRE,        OBJECT_GI_FIRE,          GID_BLUE_FIRE,        0x5D, 0x80, CHEST_ANIM_LONG,  ITEM_CATEGORY_JUNK,            MOD_NONE, GI_BLUE_FIRE),
        GET_ITEM(ITEM_POE,              OBJECT_GI_GHOST,         GID_POE,              0x97, 0x80, CHEST_ANIM_LONG,  ITEM_CATEGORY_JUNK,            MOD_NONE, GI_POE),
        GET_ITEM(ITEM_BIG_POE,          OBJECT_GI_GHOST,         GID_BIG_POE,          0xF9, 0x80, CHEST_ANIM_LONG,  ITEM_CATEGORY_JUNK,            MOD_NONE, GI_BIG_POE),
        GET_ITEM(ITEM_KEY_SMALL,        OBJECT_GI_KEY,           GID_KEY_SMALL,        0xF3, 0x80, CHEST_ANIM_SHORT, ITEM_CATEGORY_SMALL_KEY,       MOD_NONE, GI_DOOR_KEY),
        GET_ITEM(ITEM_RUPEE_GREEN,      OBJECT_GI_RUPY,          GID_RUPEE_GREEN,      0xF4, 0x00, CHEST_ANIM_SHORT, ITEM_CATEGORY_JUNK,            MOD_NONE, GI_RUPEE_GREEN_LOSE),
        GET_ITEM(ITEM_RUPEE_BLUE,       OBJECT_GI_RUPY,          GID_RUPEE_BLUE,       0xF5, 0x01, CHEST_ANIM_SHORT, ITEM_CATEGORY_JUNK,            MOD_NONE, GI_RUPEE_BLUE_LOSE),
        GET_ITEM(ITEM_RUPEE_RED,        OBJECT_GI_RUPY,          GID_RUPEE_RED,        0xF6, 0x02, CHEST_ANIM_SHORT, ITEM_CATEGORY_JUNK,            MOD_NONE, GI_RUPEE_RED_LOSE),
        GET_ITEM(ITEM_RUPEE_PURPLE,     OBJECT_GI_RUPY,          GID_RUPEE_PURPLE,     0xF7, 0x14, CHEST_ANIM_SHORT, ITEM_CATEGORY_JUNK,            MOD_NONE, GI_RUPEE_PURPLE_LOSE),
        GET_ITEM(ITEM_HEART_PIECE_2,    OBJECT_GI_HEARTS,        GID_HEART_PIECE,      0xFA, 0x80, CHEST_ANIM_LONG,  ITEM_CATEGORY_HEALTH,          MOD_NONE, GI_HEART_PIECE_WIN),
        GET_ITEM(ITEM_STICK_UPGRADE_20, OBJECT_GI_STICK,         GID_STICK,            0x90, 0x80, CHEST_ANIM_SHORT, ITEM_CATEGORY_LESSER,          MOD_NONE, GI_STICK_UPGRADE_20),
        GET_ITEM(ITEM_STICK_UPGRADE_30, OBJECT_GI_STICK,         GID_STICK,            0x91, 0x80, CHEST_ANIM_SHORT, ITEM_CATEGORY_LESSER,          MOD_NONE, GI_STICK_UPGRADE_30),
        GET_ITEM(ITEM_NUT_UPGRADE_30,   OBJECT_GI_NUTS,          GID_NUTS,             0xA7, 0x80, CHEST_ANIM_SHORT, ITEM_CATEGORY_LESSER,          MOD_NONE, GI_NUT_UPGRADE_30),
        GET_ITEM(ITEM_NUT_UPGRADE_40,   OBJECT_GI_NUTS,          GID_NUTS,             0xA8, 0x80, CHEST_ANIM_SHORT, ITEM_CATEGORY_LESSER,          MOD_NONE, GI_NUT_UPGRADE_40),
        GET_ITEM(ITEM_BULLET_BAG_50,    OBJECT_GI_DEKUPOUCH,     GID_BULLET_BAG_50,    0x6C, 0x80, CHEST_ANIM_LONG,  ITEM_CATEGORY_LESSER,          MOD_NONE, GI_BULLET_BAG_50),
        GET_ITEM_NONE,
        GET_ITEM_NONE,
        GET_ITEM_NONE // GI_MAX - if you need to add to this table insert it before this entry.
        // clang-format on
    };

void FrameInterpolation_RecordMatrixRotateAxis(f32 angle, Vec3f* axis, u8 mode) {
    (void)angle;
    (void)axis;
    (void)mode;
}

float GameInteractor_MovementSpeedMultiplier(void) {
    return 1.0f;
}

GIGravityLevel GameInteractor_GravityLevel(void) {
    return GI_GRAVITY_LEVEL_NORMAL;
}

GILinkSize GameInteractor_GetLinkSize(void) {
    return GI_LINK_SIZE_NORMAL;
}

void GameInteractor_SetLinkSize(GILinkSize size) {
    (void)size;
}

uint8_t GameInteractor_GetRandomBombFuseTimerActive(void) {
    return false;
}

uint8_t GameInteractor_GetDisableLedgeGrabsActive(void) {
    return false;
}

uint8_t GameInteractor_GetRandomWindActive(void) {
    return false;
}

uint8_t GameInteractor_GetRandomBonksActive(void) {
    return false;
}

uint8_t GameInteractor_GetSlipperyFloorActive(void) {
    return false;
}

uint8_t GameInteractor_SecondCollisionUpdate(void) {
    return false;
}

#define EMPTY_HOOK(name, parameters) \
    void name parameters {}

EMPTY_HOOK(GameInteractor_ExecuteOnSceneFlagSet, (int16_t sceneNum, int16_t flagType, int16_t flag))
EMPTY_HOOK(GameInteractor_ExecuteOnSceneFlagUnset, (int16_t sceneNum, int16_t flagType, int16_t flag))
EMPTY_HOOK(GameInteractor_ExecuteOnEquipmentDelete, (int16_t equipmentType, uint16_t equipValue))
EMPTY_HOOK(GameInteractor_ExecuteOnWarpSongLeave, (void))
EMPTY_HOOK(GameInteractor_ExecuteOnBossDefeat, (void* actor))
EMPTY_HOOK(GameInteractor_ExecuteOnDungeonKeyUsedHooks, (uint16_t mapIndex))
EMPTY_HOOK(GameInteractor_ExecuteOnEnemyDefeat, (void* actor))
EMPTY_HOOK(GameInteractor_ExecuteOnShopSlotChangeHooks, (uint8_t cursorIndex, int16_t price))
EMPTY_HOOK(GameInteractor_ExecuteOnPlayerProcessStick, (void))
EMPTY_HOOK(GameInteractor_ExecuteOnPlayerShieldControl, (float* x, float* y))
EMPTY_HOOK(GameInteractor_ExecuteOnPlayerBonk, (void))
EMPTY_HOOK(GameInteractor_ExecuteOnPlayerHoldUpShield, (void))
EMPTY_HOOK(GameInteractor_ExecuteOnPlayerBottleUpdate, (int16_t contents))
EMPTY_HOOK(GameInteractor_ExecuteOnPlayerUpdate, (void))
EMPTY_HOOK(GameInteractor_ExecuteOnPlayerFirstPersonControl, (Player* player))

void ResourceMgr_UnregisterSkeleton(SkelAnime* skelAnime) {
    (void)skelAnime;
}

bool ResourceMgr_IsPalLoaded(void) {
    return false;
}

uint32_t ResourceMgr_GetGamePlatform(int index) {
    (void)index;
    return GAME_PLATFORM_N64;
}

void OTRGetPixelDepthPrepare(float x, float y) {
    Host_PrepareDepthQuery(x, y);
}

uint16_t OTRGetPixelDepth(float x, float y) {
    return (uint16_t)Host_ReadDepthQuery(x, y);
}

void gSPDisplayListOffset(Gfx* packet, Gfx* displayList, int offset) {
    if (ResourceMgr_OTRSigCheck((char*)displayList)) {
        displayList = ResourceMgr_LoadGfxByName((char*)displayList);
    }
    __gSPDisplayList(packet, displayList + offset);
}

void gSPSegmentLoadRes(void* packet, int segment, uintptr_t target) {
    if (ResourceMgr_OTRSigCheck((char*)target)) {
        target = (uintptr_t)ResourceGetDataByName((char*)target);
    }
    __gSPSegment(packet, segment, target);
}

void Entrance_SetEntranceDiscovered(uint16_t entranceIndex, uint8_t reversed) {
    (void)entranceIndex;
    (void)reversed;
}

s16 Entrance_GetOverride(s16 entranceIndex) {
    return entranceIndex;
}

s16 Grotto_OverrideSpecialEntrance(s16 entranceIndex) {
    return entranceIndex;
}

s16 Entrance_OverrideNextIndex(s16 entranceIndex) {
    return entranceIndex;
}

s16 Entrance_OverrideDynamicExit(s16 dynamicExitIndex) {
    static const s16 exits[] = {
        ENTR_DEATH_MOUNTAIN_TRAIL_GREAT_FAIRY_EXIT,
        ENTR_DEATH_MOUNTAIN_CRATER_GREAT_FAIRY_EXIT,
        ENTR_POTION_SHOP_KAKARIKO_1,
        ENTR_KAKARIKO_VILLAGE_OUTSIDE_POTION_SHOP_FRONT,
        ENTR_MARKET_DAY_OUTSIDE_POTION_SHOP,
        ENTR_KAKARIKO_VILLAGE_OUTSIDE_BAZAAR,
        ENTR_MARKET_DAY_OUTSIDE_BAZAAR,
        ENTR_KAKARIKO_VILLAGE_OUTSIDE_SKULKLTULA_HOUSE,
        ENTR_BACK_ALLEY_DAY_OUTSIDE_BOMBCHU_SHOP,
        ENTR_KAKARIKO_VILLAGE_OUTSIDE_SHOOTING_GALLERY,
        ENTR_MARKET_DAY_OUTSIDE_SHOOTING_GALLERY,
        ENTR_ZORAS_FOUNTAIN_OUTSIDE_GREAT_FAIRY,
        ENTR_CASTLE_GROUNDS_GREAT_FAIRY_EXIT,
        ENTR_DESERT_COLOSSUS_GREAT_FAIRY_EXIT,
    };
    if (dynamicExitIndex < 0 || dynamicExitIndex >= (s16)(sizeof(exits) / sizeof(exits[0]))) {
        return dynamicExitIndex;
    }
    return exits[dynamicExitIndex];
}

void Entrance_OverrideGerudoGuardCapture(void) {
    if (LINK_IS_CHILD) {
        gPlayState->nextEntranceIndex = ENTR_GERUDO_VALLEY_1;
    }
}

GetItemEntry ItemTable_Retrieve(int16_t getItemId) {
    if (getItemId <= GI_NONE || getItemId > (int16_t)ARRAY_COUNT(vanillaItemTable)) {
        return (GetItemEntry)GET_ITEM_NONE;
    }
    return vanillaItemTable[getItemId - 1];
}

GetItemEntry ItemTable_RetrieveEntry(s16 tableId, s16 getItemId) {
    if (tableId == MOD_NONE) {
        return ItemTable_Retrieve(getItemId);
    }
    return (GetItemEntry)GET_ITEM_NONE;
}

GetItemEntry Randomizer_GetItemFromKnownCheck(RandomizerCheck check, GetItemID original) {
    (void)check;
    return ItemTable_Retrieve(original);
}

GetItemID RetrieveGetItemIDFromItemID(ItemID itemId) {
    switch (itemId) {
        case ITEM_ARROWS_LARGE:
            return GI_ARROWS_LARGE;
        case ITEM_ARROWS_MEDIUM:
            return GI_ARROWS_MEDIUM;
        case ITEM_ARROWS_SMALL:
            return GI_ARROWS_SMALL;
        case ITEM_ARROW_FIRE:
            return GI_ARROW_FIRE;
        case ITEM_ARROW_ICE:
            return GI_ARROW_ICE;
        case ITEM_ARROW_LIGHT:
            return GI_ARROW_LIGHT;
        case ITEM_BEAN:
            return GI_BEAN;
        case ITEM_BIG_POE:
            return GI_BIG_POE;
        case ITEM_BLUE_FIRE:
            return GI_BLUE_FIRE;
        case ITEM_BOMB:
            return GI_BOMBS_1;
        case ITEM_BOMBCHU:
            return GI_BOMBCHUS_10;
        case ITEM_BOMBCHUS_20:
            return GI_BOMBCHUS_20;
        case ITEM_BOMBCHUS_5:
            return GI_BOMBCHUS_5;
        case ITEM_BOMBS_10:
            return GI_BOMBS_10;
        case ITEM_BOMBS_20:
            return GI_BOMBS_20;
        case ITEM_BOMBS_30:
            return GI_BOMBS_30;
        case ITEM_BOMBS_5:
            return GI_BOMBS_5;
        case ITEM_BOMB_BAG_20:
            return GI_BOMB_BAG_20;
        case ITEM_BOMB_BAG_30:
            return GI_BOMB_BAG_30;
        case ITEM_BOMB_BAG_40:
            return GI_BOMB_BAG_40;
        case ITEM_BOOMERANG:
            return GI_BOOMERANG;
        case ITEM_BOOTS_HOVER:
            return GI_BOOTS_HOVER;
        case ITEM_BOOTS_IRON:
            return GI_BOOTS_IRON;
        case ITEM_BOTTLE:
            return GI_BOTTLE;
        case ITEM_BOW:
            return GI_BOW;
        case ITEM_BRACELET:
            return GI_BRACELET;
        case ITEM_BUG:
            return GI_BUGS;
        case ITEM_BULLET_BAG_30:
            return GI_BULLET_BAG_30;
        case ITEM_BULLET_BAG_40:
            return GI_BULLET_BAG_40;
        case ITEM_BULLET_BAG_50:
            return GI_BULLET_BAG_50;
        case ITEM_CHICKEN:
            return GI_CHICKEN;
        case ITEM_CLAIM_CHECK:
            return GI_CLAIM_CHECK;
        case ITEM_COJIRO:
            return GI_COJIRO;
        case ITEM_COMPASS:
            return GI_COMPASS;
        case ITEM_DINS_FIRE:
            return GI_DINS_FIRE;
        case ITEM_DUNGEON_MAP:
            return GI_MAP;
        case ITEM_EYEDROPS:
            return GI_EYEDROPS;
        case ITEM_FAIRY:
            return GI_FAIRY;
        case ITEM_FARORES_WIND:
            return GI_FARORES_WIND;
        case ITEM_FISH:
            return GI_FISH;
        case ITEM_FROG:
            return GI_FROG;
        case ITEM_GAUNTLETS_GOLD:
            return GI_GAUNTLETS_GOLD;
        case ITEM_GAUNTLETS_SILVER:
            return GI_GAUNTLETS_SILVER;
        case ITEM_GERUDO_CARD:
            return GI_GERUDO_CARD;
        case ITEM_HAMMER:
            return GI_HAMMER;
        case ITEM_HEART:
            return GI_HEART;
        case ITEM_HEART_CONTAINER:
            return GI_HEART_CONTAINER;
        case ITEM_HEART_PIECE_2:
            return GI_HEART_PIECE;
        case ITEM_HOOKSHOT:
            return GI_HOOKSHOT;
        case ITEM_KEY_BOSS:
            return GI_KEY_BOSS;
        case ITEM_KEY_SMALL:
            return GI_DOOR_KEY;
        case ITEM_LENS:
            return GI_LENS;
        case ITEM_LETTER_RUTO:
            return GI_LETTER_RUTO;
        case ITEM_LETTER_ZELDA:
            return GI_LETTER_ZELDA;
        case ITEM_LONGSHOT:
            return GI_LONGSHOT;
        case ITEM_MAGIC_LARGE:
            return GI_MAGIC_LARGE;
        case ITEM_MAGIC_SMALL:
            return GI_MAGIC_SMALL;
        case ITEM_MASK_BUNNY:
            return GI_MASK_BUNNY;
        case ITEM_MASK_GERUDO:
            return GI_MASK_GERUDO;
        case ITEM_MASK_GORON:
            return GI_MASK_GORON;
        case ITEM_MASK_KEATON:
            return GI_MASK_KEATON;
        case ITEM_MASK_SKULL:
            return GI_MASK_SKULL;
        case ITEM_MASK_SPOOKY:
            return GI_MASK_SPOOKY;
        case ITEM_MASK_TRUTH:
            return GI_MASK_TRUTH;
        case ITEM_MASK_ZORA:
            return GI_MASK_ZORA;
        case ITEM_MILK:
            return GI_MILK;
        case ITEM_MILK_BOTTLE:
            return GI_MILK_BOTTLE;
        case ITEM_NAYRUS_LOVE:
            return GI_NAYRUS_LOVE;
        case ITEM_NUT:
            return GI_NUTS_5;
        case ITEM_NUTS_10:
            return GI_NUTS_10;
        case ITEM_NUTS_5:
            return GI_NUTS_5;
        case ITEM_NUT_UPGRADE_30:
            return GI_NUT_UPGRADE_30;
        case ITEM_NUT_UPGRADE_40:
            return GI_NUT_UPGRADE_40;
        case ITEM_OCARINA_FAIRY:
            return GI_OCARINA_FAIRY;
        case ITEM_OCARINA_TIME:
            return GI_OCARINA_OOT;
        case ITEM_ODD_MUSHROOM:
            return GI_ODD_MUSHROOM;
        case ITEM_ODD_POTION:
            return GI_ODD_POTION;
        case ITEM_POCKET_CUCCO:
            return GI_POCKET_CUCCO;
        case ITEM_POCKET_EGG:
            return GI_POCKET_EGG;
        case ITEM_POE:
            return GI_POE;
        case ITEM_POTION_BLUE:
            return GI_POTION_BLUE;
        case ITEM_POTION_GREEN:
            return GI_POTION_GREEN;
        case ITEM_POTION_RED:
            return GI_POTION_RED;
        case ITEM_PRESCRIPTION:
            return GI_PRESCRIPTION;
        case ITEM_QUIVER_40:
            return GI_QUIVER_40;
        case ITEM_QUIVER_50:
            return GI_QUIVER_50;
        case ITEM_RUPEE_BLUE:
            return GI_RUPEE_BLUE;
        case ITEM_RUPEE_GOLD:
            return GI_RUPEE_GOLD;
        case ITEM_RUPEE_GREEN:
            return GI_RUPEE_GREEN;
        case ITEM_RUPEE_PURPLE:
            return GI_RUPEE_PURPLE;
        case ITEM_RUPEE_RED:
            return GI_RUPEE_RED;
        case ITEM_SAW:
            return GI_SAW;
        case ITEM_SCALE_GOLDEN:
            return GI_SCALE_GOLDEN;
        case ITEM_SCALE_SILVER:
            return GI_SCALE_SILVER;
        case ITEM_SEEDS:
            return GI_SEEDS_5;
        case ITEM_SEEDS_30:
            return GI_SEEDS_30;
        case ITEM_SHIELD_DEKU:
            return GI_SHIELD_DEKU;
        case ITEM_SHIELD_HYLIAN:
            return GI_SHIELD_HYLIAN;
        case ITEM_SHIELD_MIRROR:
            return GI_SHIELD_MIRROR;
        case ITEM_SKULL_TOKEN:
            return GI_SKULL_TOKEN;
        case ITEM_SLINGSHOT:
            return GI_SLINGSHOT;
        case ITEM_STICK:
            return GI_STICKS_1;
        case ITEM_STICKS_10:
            return GI_STICKS_10;
        case ITEM_STICKS_5:
            return GI_STICKS_5;
        case ITEM_STICK_UPGRADE_20:
            return GI_STICK_UPGRADE_20;
        case ITEM_STICK_UPGRADE_30:
            return GI_STICK_UPGRADE_30;
        case ITEM_STONE_OF_AGONY:
            return GI_STONE_OF_AGONY;
        case ITEM_SWORD_BGS:
            return GI_SWORD_BGS;
        case ITEM_SWORD_BROKEN:
            return GI_SWORD_BROKEN;
        case ITEM_SWORD_KOKIRI:
            return GI_SWORD_KOKIRI;
        case ITEM_TUNIC_GORON:
            return GI_TUNIC_GORON;
        case ITEM_TUNIC_ZORA:
            return GI_TUNIC_ZORA;
        case ITEM_WALLET_ADULT:
            return GI_WALLET_ADULT;
        case ITEM_WALLET_GIANT:
            return GI_WALLET_GIANT;
        case ITEM_WEIRD_EGG:
            return GI_WEIRD_EGG;
        default:
            return GI_MAX;
    }
}

RandomizerGet RetrieveRandomizerGetFromItemID(ItemID itemId) {
    switch (itemId) {
        case ITEM_SONG_MINUET:
            return RG_MINUET_OF_FOREST;
        case ITEM_SONG_BOLERO:
            return RG_BOLERO_OF_FIRE;
        case ITEM_SONG_SERENADE:
            return RG_SERENADE_OF_WATER;
        case ITEM_SONG_REQUIEM:
            return RG_REQUIEM_OF_SPIRIT;
        case ITEM_SONG_NOCTURNE:
            return RG_NOCTURNE_OF_SHADOW;
        case ITEM_SONG_PRELUDE:
            return RG_PRELUDE_OF_LIGHT;
        case ITEM_SONG_LULLABY:
            return RG_ZELDAS_LULLABY;
        case ITEM_SONG_EPONA:
            return RG_EPONAS_SONG;
        case ITEM_SONG_SARIA:
            return RG_SARIAS_SONG;
        case ITEM_SONG_SUN:
            return RG_SUNS_SONG;
        case ITEM_SONG_TIME:
            return RG_SONG_OF_TIME;
        case ITEM_SONG_STORMS:
            return RG_SONG_OF_STORMS;
        case ITEM_MEDALLION_FOREST:
            return RG_FOREST_MEDALLION;
        case ITEM_MEDALLION_FIRE:
            return RG_FIRE_MEDALLION;
        case ITEM_MEDALLION_WATER:
            return RG_WATER_MEDALLION;
        case ITEM_MEDALLION_SPIRIT:
            return RG_SPIRIT_MEDALLION;
        case ITEM_MEDALLION_SHADOW:
            return RG_SHADOW_MEDALLION;
        case ITEM_MEDALLION_LIGHT:
            return RG_LIGHT_MEDALLION;
        case ITEM_KOKIRI_EMERALD:
            return RG_KOKIRI_EMERALD;
        case ITEM_GORON_RUBY:
            return RG_GORON_RUBY;
        case ITEM_ZORA_SAPPHIRE:
            return RG_ZORA_SAPPHIRE;
        case ITEM_SWORD_MASTER:
            return RG_MASTER_SWORD;
        default:
            return RG_MAX;
    }
}

RandomizerCheck Randomizer_GetCheckFromActor(s16 actorId, s16 sceneNum, s16 actorParams) {
    (void)actorId;
    (void)sceneNum;
    (void)actorParams;
    return RC_UNKNOWN_CHECK;
}

ShopItemIdentity Randomizer_IdentifyShopItem(s32 sceneNum, u8 slotIndex) {
    (void)sceneNum;
    (void)slotIndex;
    return (ShopItemIdentity){ 0 };
}

bool Ship_IsCStringEmpty(const char* string) {
    return string == NULL || string[0] == '\0';
}

GetItemEntry Randomizer_GetItemFromKnownCheckWithoutObtainabilityCheck(RandomizerCheck check, GetItemID original) {
    (void)check;
    return ItemTable_Retrieve(original);
}

ItemObtainability Randomizer_GetItemObtainabilityFromRandomizerCheck(RandomizerCheck check) {
    (void)check;
    return CAN_OBTAIN;
}

void Randomizer_DrawTriforcePieceGI(PlayState* play, GetItemEntry entry) {
    (void)play;
    (void)entry;
}

u16 Randomizer_Item_Give(PlayState* play, GetItemEntry entry) {
    (void)play;
    (void)entry;
    return ITEM_NONE;
}
