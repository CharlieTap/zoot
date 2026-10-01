#include "shipwright_stub_registry.h"

#include <stddef.h>

#if OOT_TRACE_STUBS
uint64_t gOotStubCalls[OOT_STUB_COUNT];
#endif

static const char* const gOotStubNames[OOT_STUB_COUNT] = {
    "lusprintf",
    "_Printf",
    "AudioEditor_GetReplacementSeq",
    "GameInteractor_ExecuteOnOcarinaNote",
    "SetAudioChannels",
    "FrameInterpolation_RecordOpenChild",
    "FrameInterpolation_RecordCloseChild",
    "GameInteractor_ExecuteOnGameStateMainStart",
    "GameInteractor_ExecuteOnGameFrameUpdate",
    "OTRGfxPrint",
    "osSetTimer",
    "GfxDebuggerIsDebugging",
    "GfxDebuggerIsDebuggingRequested",
    "GfxDebuggerDebugDisplayList",
    "osPfsInitPak",
    "osPfsFreeBlocks",
    "osPfsFindFile",
    "osPfsReadWriteFile",
    "osPfsAllocateFile",
    "osPfsDeleteFile",
    "osPfsFileState",
    "FrameInterpolation_RecordMatrixPush",
    "FrameInterpolation_RecordMatrixPop",
    "FrameInterpolation_RecordMatrixPut",
    "FrameInterpolation_RecordMatrixMult",
    "FrameInterpolation_RecordMatrixTranslate",
    "FrameInterpolation_RecordMatrixScale",
    "FrameInterpolation_RecordMatrixRotate1Coord",
    "FrameInterpolation_RecordMatrixRotateZYX",
    "FrameInterpolation_RecordMatrixTranslateRotateZYX",
    "FrameInterpolation_RecordMatrixSetTranslateRotateYXZ",
    "FrameInterpolation_RecordMatrixMtxFToMtx",
    "FrameInterpolation_RecordMatrixToMtx",
    "FrameInterpolation_RecordMatrixReplaceRotation",
    "GameInteractor_ExecuteOnPresentTitleCard",
    "GameInteractor_ExecuteOnActorKill",
    "GameInteractor_ExecuteOnActorInit",
    "NameTag_RemoveAllForActor",
    "GameInteractor_ExecuteOnPlayerSfx",
    "SetActorListIndex",
    "GameInteractor_ExecuteOnSceneSpawnActors",
    "GameInteractor_ExecuteOnActorUpdate",
    "GameInteractor_ExecuteOnActorSpawn",
    "GameInteractor_ExecuteOnActorDestroy",
    "ObjectExtension_Free",
    "FrameInterpolation_RecordActorPosRotMatrix",
    "GameInteractor_ExecuteOnFlagSet",
    "GameInteractor_ExecuteOnFlagUnset",
    "FrameInterpolation_DontInterpolateCamera",
    "GameInteractor_NoUIActive",
    "Randomizer_GetSettingValue",
    "CVarClear",
    "GameInteractor_ExecuteOnLinkEquipmentChange",
    "GameInteractor_ExecuteOnKaleidoUpdate",
    "CVarSetInteger",
    "ResourceMgr_PatchGfxByName",
    "GameInteractor_ExecuteOnMinimapDrawCompassIcons",
    "GameInteractor_ExecuteOnOpenText",
    "GameInteractor_ExecuteOnOcarinaSongAction",
    "GameInteractor_ExecuteOnDialogMessage",
    "GameInteractor_PacifistModeActive",
    "GetUnixTimestamp",
    "GameInteractor_ExecuteOnTimestamp",
    "GameInteractor_ExecuteOnItemReceiveHooks",
    "GameInteractor_OneHitKOActive",
    "GameInteractor_DefenseModifier",
    "GameInteractor_ExecuteOnPlayerHealthChange",
    "GameInteractor_ExecuteOnSetDoAction",
    "GameInteractor_ExecuteOnInterfaceUpdate",
    "GameInteractor_ExecuteOnSaleEndHooks",
    "Grotto_ForceGrottoReturn",
    "Interface_ReplaceSpecialCharacters",
    "Ship_GetCharFontTexture",
    "Ship_GetCharFontWidth",
    "GameInteractor_ExecuteOnPlayDestroy",
    "disableBetaQuest",
    "enableBetaQuest",
    "GameInteractor_ExecuteOnExitGame",
    "FrameInterpolation_StartRecord",
    "FrameInterpolation_StopRecord",
    "GameInteractor_ExecuteOnTransitionEndHooks",
    "GameInteractor_ExecuteOnCameraState",
    "GameInteractor_ExecuteOnPlayDrawBegin",
    "GameInteractor_ExecuteOnPlayDrawEnd",
    "GameInteractor_ExecuteOnPlayerSetModels",
    "GameInteractor_InvisibleLinkActive",
    "ResourceMgr_UnloadOriginalWhenAltExists",
    "GameInteractor_ExecuteOnLinkSkeletonInit",
    "FrameInterpolation_RecordSkinMatrixMtxFToMtx",
    "BossRush_InitSave",
    "Randomizer_IsSeedGenerated",
    "Randomizer_IsSpoilerLoaded",
    "Randomizer_InitSaveFile",
    "SaveManager_ThreadPoolWait",
    "FrameInterpolation_GetCameraEpoch",
    "Gfx_TextureCacheDelete",
    "GameInteractor_RegisterOnAssetAltChange",
    "GameInteractor_ExecuteOnPresentFileSelect",
    "GetSeedTexture",
    "GetSeedIconIndex",
    "SpoilerFileExists",
    "CVarSetString",
    "Randomizer_SetSpoilerLoaded",
    "Randomizer_ParseSpoiler",
    "GameInteractor_ExecuteOnUpdateFileSelectSelection",
    "GameInteractor_ExecuteOnUpdateFileQuestSelection",
    "GameInteractor_ExecuteOnUpdateFileRandomizerOptionSelection",
    "SohFileSelect_ShowPresetModal",
    "Randomizer_GenerateRandomizer",
    "Randomizer_ShowRandomizerMenu",
    "FileChoose_DrawBossRushMenuWindowContents",
    "SohFileSelect_GetSettingText",
    "GameInteractor_ExecuteOnUpdateFileSelectConfirmationSelection",
    "GameInteractor_ExecuteOnLoadGame",
    "GameInteractor_ExecuteOnFileChooseMain",
    "FileChoose_UpdateBossRushMenu",
    "GameInteractor_ExecuteOnUpdateFileCopySelection",
    "GameInteractor_ExecuteOnUpdateFileCopyConfirmationSelection",
    "GameInteractor_ExecuteOnUpdateFileEraseSelection",
    "GameInteractor_ExecuteOnUpdateFileEraseConfirmationSelection",
    "CVarSave",
    "GameInteractor_ExecuteOnUpdateFileAudioSelection",
    "GameInteractor_ExecuteOnUpdateFileTargetSelection",
    "GameInteractor_ExecuteOnUpdateFileNameSelection",
    "GameInteractor_ExecuteOnSetGameLanguage",
    "GameInteractor_ExecuteOnUpdateFileLanguageSelection",
    "Grotto_GetRenamedGrottoIndexFromOriginal",
    "GameInteractor_ExecuteOnZTitleInit",
    "Randomizer_GetPrevChildTradeItem",
    "Randomizer_GetNextChildTradeItem",
    "Randomizer_GetPrevAdultTradeItem",
    "Randomizer_GetNextAdultTradeItem",
    "Enhancement_GetPrevNayrusItem",
    "Enhancement_GetNextNayrusItem",
    "RandoKaleido_DrawMiscCollectibles",
    "GameInteractor_ExecuteOnKaleidoscopeUpdate",
    "osViSetMode",
    "osViSwapBuffer",
    "osGetThreadId",
    "osViSetYScale",
    "osWritebackDCacheAll",
    "osViGetNextFramebuffer",
    "osCartRomInit",
    "osInvalDCache",
    "osAiSetFrequency",
    "osSetIntMask",
    "GameInteractor_ExecuteOnSeqPlayerInit",
    "ResourceGetNameByCrc",
    "Messagebox_ShowErrorBox",
    "AudioCollection_HasSequenceNum",
    "AudioCollection_AddToCollection",
    "aOPUSFree",
    "osWritebackDCache",
};

uint32_t oot_stub_count(void) {
    return OOT_STUB_COUNT;
}

uint64_t oot_stub_calls(uint32_t index) {
#if OOT_TRACE_STUBS
    return index < OOT_STUB_COUNT ? gOotStubCalls[index] : 0;
#else
    (void)index;
    return 0;
#endif
}

uint32_t oot_stub_name(uint32_t index, char* destination, uint32_t capacity) {
    if (index >= OOT_STUB_COUNT) {
        return 0;
    }
    const char* source = gOotStubNames[index];
    uint32_t length = 0;
    while (source[length] != '\0') {
        length++;
    }
    if (destination != NULL && capacity > 0) {
        uint32_t copied = length < capacity - 1 ? length : capacity - 1;
        for (uint32_t offset = 0; offset < copied; offset++) {
            destination[offset] = source[offset];
        }
        destination[copied] = '\0';
    }
    return length;
}

void oot_stub_reset(void) {
#if OOT_TRACE_STUBS
    for (uint32_t index = 0; index < OOT_STUB_COUNT; index++) {
        gOotStubCalls[index] = 0;
    }
#endif
}
