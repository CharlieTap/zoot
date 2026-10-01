#include "global.h"
#include "oot_guest.h"
#include "soh/Enhancements/game-interactor/GameInteractor_Hooks.h"
#include "soh/GameVersions.h"
#include "soh/ResourceManagerHelpers.h"
#include <libultraship/bridge/consolevariablebridge.h>
#include <libultraship/bridge/resourcebridge.h>
#include <unistd.h>
#if OOT_RENDER_TRACE
#include <stdio.h>
#endif

void OotRenderer_Init(void);
void OotRenderer_Run(Gfx* commands);
int32_t OotRenderer_Framebuffer(uint32_t, uint32_t, uint32_t, uint32_t, uint32_t, uint32_t);
void OotRenderer_BlendedTexture(const char*, const uint8_t*, const uint8_t*);
void* ResourceMgr_LoadIfDListByName(const char* path);

static uint64_t simulationTime;
static uint64_t simulationTick;
static uint32_t randomSeed;
s32 gPauseFrameBuffer = -1;
s32 gBlurFrameBuffer = -1;
s32 gReusableFrameBuffer = -1;
s32 gN64ResFrameBuffer = -1;


void AudioMgr_CreateNextAudioBuffer(s16* samples, u32 sampleFrames);

void OotShipwright_AudioInit(void) {
    Audio_Init();
    Audio_InitSound();
    Audio_SetGameVolume(SEQ_PLAYER_BGM_MAIN, 1.0f);
    Audio_SetGameVolume(SEQ_PLAYER_BGM_SUB, 1.0f);
    Audio_SetGameVolume(SEQ_PLAYER_FANFARE, 1.0f);
    Audio_SetGameVolume(SEQ_PLAYER_SFX, 1.0f);
}

int32_t OotShipwright_AudioStep(uint32_t sampleFrames) {
    static s16 samples[0x400 * 2];
    if (sampleFrames > 0x400) {
        return -1;
    }
    AudioMgr_CreateNextAudioBuffer(samples, sampleFrames);
    return (int32_t)sampleFrames;
}

void OotShipwright_SetAudioVolumes(float music, float effects, float fanfares) {
    Audio_SetGameVolume(SEQ_PLAYER_BGM_MAIN, music);
    Audio_SetGameVolume(SEQ_PLAYER_BGM_SUB, music);
    Audio_SetGameVolume(SEQ_PLAYER_SFX, effects);
    Audio_SetGameVolume(SEQ_PLAYER_FANFARE, fanfares);
}

void osCreateMesgQueue(OSMesgQueue* queue, OSMesg* messages, s32 capacity) {
    queue->mtqueue = NULL;
    queue->fullqueue = NULL;
    queue->validCount = 0;
    queue->first = 0;
    queue->msgCount = capacity;
    queue->msg = messages;
}

s32 osSendMesg(OSMesgQueue* queue, OSMesg message, s32 flags) {
    (void)flags;
    if (queue == NULL || queue->msg == NULL || queue->msgCount <= 0 || queue->validCount >= queue->msgCount) {
        return -1;
    }
    s32 index = (queue->first + queue->validCount) % queue->msgCount;
    queue->msg[index] = message;
    queue->validCount++;
    return 0;
}

s32 osRecvMesg(OSMesgQueue* queue, OSMesg* message, s32 flags) {
    (void)flags;
    if (queue == NULL || queue->validCount <= 0) {
        return -1;
    }
    if (message != NULL) {
        *message = queue->msg[queue->first];
    }
    queue->first = (queue->first + 1) % queue->msgCount;
    queue->validCount--;
    return 0;
}

void OotShipwright_SetTime(uint64_t monotonicMicroseconds) {
    simulationTime = monotonicMicroseconds;
    simulationTick++;
}

void OotShipwright_SetRandomSeed(uint32_t seed) {
    randomSeed = seed;
}

uint32_t OotShipwright_GetRandomSeed(void) {
    return randomSeed;
}

void OotShipwright_SetInput(const OotInputState* input) {
    Input* pad = &gPadMgr.inputs[0];
    pad->prev = pad->cur;
    pad->cur.button = input->buttons;
    pad->cur.stick_x = input->stick_x;
    pad->cur.stick_y = input->stick_y;
    pad->press.button = pad->cur.button & ~pad->prev.button;
    pad->rel.button = pad->prev.button & ~pad->cur.button;
    PadUtils_UpdateRelXY(pad);
    pad->press.stick_x = pad->cur.stick_x - pad->prev.stick_x;
    pad->press.stick_y = pad->cur.stick_y - pad->prev.stick_y;
}

OSTime osGetTime(void) {
    return simulationTime;
}

u32 osGetCount(void) {
    return (u32)simulationTime;
}

uint64_t GetFrequency(void) {
    return 1000000;
}

uint64_t GetPerfCounter(void) {
    return simulationTime;
}

int32_t CVarGetInteger(const char* name, int32_t defaultValue) {
    (void)name;
    return defaultValue;
}

float CVarGetFloat(const char* name, float defaultValue) {
    (void)name;
    return defaultValue;
}

const char* CVarGetString(const char* name, const char* defaultValue) {
    (void)name;
    return defaultValue;
}

Color_RGBA8 CVarGetColor(const char* name, Color_RGBA8 defaultValue) {
    (void)name;
    return defaultValue;
}

Color_RGB8 CVarGetColor24(const char* name, Color_RGB8 defaultValue) {
    (void)name;
    return defaultValue;
}

bool GameInteractor_Should(GIVanillaBehavior flag, uint32_t result, ...) {
    (void)flag;
    return result;
}

bool GameInteractor_ShouldActorInit(void* actor) {
    (void)actor;
    return true;
}

bool GameInteractor_ShouldActorUpdate(void* actor) {
    (void)actor;
    return true;
}

bool GameInteractor_ShouldActorDestroy(void* actor) {
    (void)actor;
    return true;
}

float OTRGetDimensionFromLeftEdge(float value) {
    return value;
}

float OTRGetDimensionFromRightEdge(float value) {
    return value;
}

int16_t OTRGetRectDimensionFromLeftEdge(float value) {
    return (int16_t)value;
}

int16_t OTRGetRectDimensionFromRightEdge(float value) {
    return (int16_t)value;
}

float OTRGetAspectRatio(void) {
    return 4.0f / 3.0f;
}

uint32_t OTRGetGameRenderWidth(void) {
    return SCREEN_WIDTH;
}

uint32_t OTRGetGameRenderHeight(void) {
    return SCREEN_HEIGHT;
}

uint32_t ResourceMgr_GameHasOriginal(void) {
    return true;
}

uint32_t ResourceMgr_GetGameRegion(int index) {
    (void)index;
    return GAME_REGION_NTSC;
}

uint32_t ResourceMgr_GetGameVersion(int index) {
    (void)index;
    return OOT_NTSC_US_12;
}

uint32_t ResourceMgr_IsGameMasterQuest(void) {
    return false;
}

uint32_t ResourceMgr_IsSceneMasterQuest(s16 sceneNum) {
    (void)sceneNum;
    return false;
}

bool ResourceMgr_IsAltAssetsEnabled(void) {
    return false;
}

void Graph_ProcessGfxCommands(Gfx* commands) {
    OotRenderer_Run(commands);
}

#if OOT_RENDER_TRACE || OOT_PROFILE
void oot_trace_entrance(uint32_t entrance) {
    extern PlayState* gPlayState;
    gPlayState->nextEntranceIndex = entrance;
    gPlayState->transitionTrigger = TRANS_TRIGGER_START;
    gPlayState->transitionType = TRANS_TYPE_FADE_BLACK;
}
#endif

#if OOT_RENDER_TRACE
void OotTrace_PrintScene(void) {
    extern PlayState* gPlayState;
    if (gPlayState) {
        const View* view = &gPlayState->view;
        printf("scene|%d|room=%d|mode=%d|eye=%.9g,%.9g,%.9g\n",
               gPlayState->sceneNum, gPlayState->roomCtx.curRoom.num, gSaveContext.gameMode,
               view->eye.x, view->eye.y, view->eye.z);
        const Camera* camera = GET_ACTIVE_CAM(gPlayState);
        const Player* player = GET_PLAYER(gPlayState);
        printf("pause|state=%d|debug=%d\n", gPlayState->pauseCtx.state, gPlayState->pauseCtx.debugState);
        printf("cutscene|state=%d|frame=%d|index=%x|trigger=%d|playerAction=%d|message=%d\n",
               gPlayState->csCtx.state, gPlayState->csCtx.frames, gSaveContext.cutsceneIndex,
               gSaveContext.cutsceneTrigger, player->csAction, gPlayState->msgCtx.msgMode);
        printf("camera|setting=%d|mode=%d|status=%d|data=%d|eye=%.9g,%.9g,%.9g|at=%.9g,%.9g,%.9g|player=%.9g,%.9g,%.9g\n",
               camera->setting, camera->mode, camera->status, camera->camDataIdx,
               camera->eye.x, camera->eye.y, camera->eye.z, camera->at.x, camera->at.y, camera->at.z,
               player->actor.world.pos.x, player->actor.world.pos.y, player->actor.world.pos.z);
    }
}
#endif

static int Oot_CreateFramebuffer(uint32_t width, uint32_t height, uint32_t nativeWidth, uint32_t nativeHeight,
                           bool resize, bool forceFixedAspect) {
    return OotRenderer_Framebuffer(width, height, nativeWidth, nativeHeight, resize, forceFixedAspect);
}

void Gfx_RegisterBlendedTexture(const char* texture, u8* mask, u8* replacement) {
    OotRenderer_BlendedTexture(texture, mask, replacement);
}

void Gfx_UnregisterBlendedTexture(const char* texture) {
    OotRenderer_BlendedTexture(texture, NULL, NULL);
}

void FB_CreateFramebuffers(void) {
    if (gPauseFrameBuffer == -1) {
        gPauseFrameBuffer = Oot_CreateFramebuffer(SCREEN_WIDTH, SCREEN_HEIGHT, SCREEN_WIDTH, SCREEN_HEIGHT, true, false);
    }
    if (gBlurFrameBuffer == -1) {
        gBlurFrameBuffer = Oot_CreateFramebuffer(SCREEN_WIDTH, SCREEN_HEIGHT, SCREEN_WIDTH, SCREEN_HEIGHT, true, false);
    }
    if (gReusableFrameBuffer == -1) {
        gReusableFrameBuffer = Oot_CreateFramebuffer(SCREEN_WIDTH, SCREEN_HEIGHT, SCREEN_WIDTH, SCREEN_HEIGHT, true, false);
    }
    if (gN64ResFrameBuffer == -1) {
        gN64ResFrameBuffer = Oot_CreateFramebuffer(SCREEN_WIDTH, SCREEN_HEIGHT, SCREEN_WIDTH, SCREEN_HEIGHT, false, false);
    }
}

void FB_CopyToFramebuffer(Gfx** gfxp, s32 source, s32 destination, u8 oncePerFrame, u8* hasCopied) {
    Gfx* gfx = *gfxp;

    gSPMatrix(gfx++, &gMtxClear, G_MTX_NOPUSH | G_MTX_LOAD | G_MTX_MODELVIEW);
    gDPSetOtherMode(gfx++,
                    G_AD_DISABLE | G_CD_DISABLE | G_CK_NONE | G_TC_FILT | G_TF_POINT | G_TT_NONE | G_TL_TILE |
                        G_TD_CLAMP | G_TP_NONE | G_CYC_1CYCLE | G_PM_NPRIMITIVE,
                    G_AC_NONE | G_ZS_PRIM | G_RM_OPA_SURF | G_RM_OPA_SURF2);
    gSPClearGeometryMode(gfx++, G_FOG | G_LIGHTING | G_TEXTURE_GEN | G_TEXTURE_GEN_LINEAR);
    gSPSetGeometryMode(gfx++, G_ZBUFFER | G_SHADE | G_SHADING_SMOOTH);
    gDPSetBlendColor(gfx++, 255, 255, 255, 8);
    gDPSetPrimDepth(gfx++, 0xFFFF, 0xFFFF);
    gDPSetEnvColor(gfx++, 255, 255, 255, 255);
    gDPSetCombineLERP(gfx++, TEXEL0, 0, ENVIRONMENT, 0, 0, 0, 0, ENVIRONMENT, TEXEL0, 0, ENVIRONMENT, 0, 0, 0, 0,
                      ENVIRONMENT);
    gDPSetScissor(gfx++, G_SC_NON_INTERLACE, 0, 0, SCREEN_WIDTH, SCREEN_HEIGHT);
    gDPCopyFB(gfx++, destination, source, oncePerFrame, hasCopied);
    *gfxp = gfx;
}

void FB_DrawFromFramebuffer(Gfx** gfxp, s32 frameBuffer, u8 alpha) {
    Gfx* gfx = *gfxp;

    gSPMatrix(gfx++, &gMtxClear, G_MTX_NOPUSH | G_MTX_LOAD | G_MTX_MODELVIEW);
    gDPSetEnvColor(gfx++, 255, 255, 255, alpha);
    gDPSetOtherMode(gfx++,
                    G_AD_NOISE | G_CD_NOISE | G_CK_NONE | G_TC_FILT | G_TF_POINT | G_TT_NONE | G_TL_TILE | G_TD_CLAMP |
                        G_TP_NONE | G_CYC_1CYCLE | G_PM_NPRIMITIVE,
                    G_AC_NONE | G_ZS_PRIM | G_RM_CLD_SURF | G_RM_CLD_SURF2);
    gSPClearGeometryMode(gfx++, G_CULL_BOTH | G_FOG | G_LIGHTING | G_TEXTURE_GEN | G_TEXTURE_GEN_LINEAR);
    gSPSetGeometryMode(gfx++, G_ZBUFFER | G_SHADE | G_SHADING_SMOOTH);
    gDPSetCombineLERP(gfx++, TEXEL0, 0, ENVIRONMENT, 0, 0, 0, 0, ENVIRONMENT, TEXEL0, 0, ENVIRONMENT, 0, 0, 0, 0,
                      ENVIRONMENT);
    gDPSetScissor(gfx++, G_SC_NON_INTERLACE, 0, 0, SCREEN_WIDTH, SCREEN_HEIGHT);
    gDPSetTextureImageFB(gfx++, 0, 0, 0, frameBuffer);
    gDPImageRectangle(gfx++, 0, 0, 0, 0, SCREEN_WIDTH << 2, SCREEN_HEIGHT << 2, SCREEN_WIDTH, SCREEN_HEIGHT,
                      G_TX_RENDERTILE, SCREEN_WIDTH, SCREEN_HEIGHT);

    *gfxp = gfx;
}

void gSPSegment(void* packet, int segment, uintptr_t target) {
    if (ResourceMgr_OTRSigCheck((char*)target)) {
        void* displayList = ResourceMgr_LoadIfDListByName((const char*)target);
        // Texture paths must stay intact so Fast3D can load their metadata.
        if (displayList != NULL) target = (uintptr_t)displayList;
    }
    __gSPSegment(packet, segment, target);
}

void gSPDisplayList(Gfx* packet, Gfx* displayList) {
    if (ResourceMgr_OTRSigCheck((char*)displayList)) {
        displayList = ResourceMgr_LoadGfxByName((char*)displayList);
    }
    __gSPDisplayList(packet, displayList);
}

void gSPVertex(Gfx* packet, uintptr_t vertices, int count, int first) {
    if (ResourceMgr_OTRSigCheck((char*)vertices)) {
        vertices = (uintptr_t)ResourceGetDataByName((char*)vertices);
    }
    __gSPVertex(packet, vertices, count, first);
}

void gSPInvalidateTexCache(Gfx* packet, uintptr_t texture) {
    __gSPInvalidateTexCache(packet, texture);
}

void gDPSetTileSizeLerp(Gfx* packet, int tile, float uls0, float ult0, float lrs0, float lrt0, float uls1,
                        float ult1, float lrs1, float lrt1) {
    __gDPSetTileSizeLerp(packet, tile, uls0, ult0, lrs0, lrt0, uls1, ult1, lrs1, lrt1);
}
