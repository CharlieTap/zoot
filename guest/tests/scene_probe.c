// Opt-in test build only. Uses the same save setup and game-state transition as scene select.
#include "global.h"
#include "oot_guest.h"
#include "soh/SaveManager.h"

#include <stdio.h>
#include <stdlib.h>

extern PlayState* gPlayState;

typedef struct {
    unsigned first, last, buttons;
    int x, y;
} InputInterval;

static unsigned entrance, age, repeatBoss, waterLevel, tick;
static InputInterval inputs[32];
static unsigned inputCount;
static struct { unsigned tick; Vec3f position; int yaw; } warps[8];
static unsigned warpCount;
static int32_t state[10];

void OotSceneProbe_Init(void) {
    FILE* file = fopen("/saves/scene-probe.txt", "r");
    if (!file || fscanf(file, "%u %u %u %u", &entrance, &age, &repeatBoss, &waterLevel) != 4) {
        fprintf(stderr, "Scene probe requires entrance, age, repeat-boss and water-level\n");
        abort();
    }
    char line[128];
    fgets(line, sizeof(line), file);
    while (fgets(line, sizeof(line), file)) {
        if (line[0] == 'w') {
            if (warpCount == ARRAY_COUNT(warps) ||
                sscanf(line, "warp %u %f %f %f %d", &warps[warpCount].tick,
                       &warps[warpCount].position.x, &warps[warpCount].position.y,
                       &warps[warpCount].position.z, &warps[warpCount].yaw) != 5) abort();
            warpCount++;
            continue;
        }
        if (inputCount == ARRAY_COUNT(inputs)) abort();
        InputInterval* input = &inputs[inputCount];
        if (sscanf(line, "%u %u %x %d %d", &input->first, &input->last,
                   &input->buttons, &input->x, &input->y) != 5) abort();
        inputCount++;
    }
    fclose(file);
}

void OotSceneProbe_BeforeFrame(OotInputState* input) {
    tick++;
    input->buttons = 0;
    input->stick_x = input->stick_y = 0;
    for (unsigned i = 0; i < warpCount; i++) {
        if (tick != warps[i].tick) continue;
        Player* player = GET_PLAYER(gPlayState);
        player->actor.world.pos = player->actor.prevPos = player->actor.home.pos = warps[i].position;
        player->actor.world.rot.y = player->actor.shape.rot.y = warps[i].yaw;
        player->actor.speedXZ = player->actor.velocity.y = 0;
        printf("scene-probe warp tick=%u pos=%.0f,%.0f,%.0f\n", tick,
               player->actor.world.pos.x, player->actor.world.pos.y, player->actor.world.pos.z);
    }
    for (unsigned i = 0; i < inputCount; i++) {
        if (tick >= inputs[i].first && tick <= inputs[i].last) {
            input->buttons = inputs[i].buttons;
            input->stick_x = inputs[i].x;
            input->stick_y = inputs[i].y;
            break;
        }
    }
    if (tick != 2) return;

    gSaveContext.fileNum = 0xFF; // Save_SaveFile rejects this slot: never write a player save.
    gSaveContext.linkAge = age;
    gSaveContext.gameMode = GAMEMODE_NORMAL;
    Save_InitFile(true);
    gSaveContext.entranceIndex = entrance;
    gSaveContext.cutsceneIndex = 0x8000;
    gSaveContext.sceneLayer = 0;
    gSaveContext.dayTime = 0x8000;
    gSaveContext.nightFlag = 0;
    gSaveContext.respawnFlag = 0;
    gSaveContext.respawn[RESPAWN_MODE_DOWN].entranceIndex = ENTR_LOAD_OPENING;
    gSaveContext.showTitleCard = true;
    gSaveContext.magicFillTarget = gSaveContext.magic;
    gSaveContext.magic = gSaveContext.magicCapacity = gSaveContext.magicLevel = 0;
    for (unsigned i = 0; i < ARRAY_COUNT(gSaveContext.buttonStatus); i++) {
        gSaveContext.buttonStatus[i] = BTN_ENABLED;
    }
    gSaveContext.forceRisingButtonAlphas = gSaveContext.nextHudVisibilityMode =
        gSaveContext.hudVisibilityMode = gSaveContext.hudVisibilityModeTimer = 0;
    if (repeatBoss) gSaveContext.eventChkInf[7] |= 0x1FF; // Boss intros already seen, bosses not defeated.
    // The Water Temple's saved water level is stored in the upper switch bits.
    if (waterLevel) gSaveContext.sceneFlags[SCENE_WATER_TEMPLE].swch = 1u << (waterLevel + 27);
    gWeatherMode = 0;
    gGameState->running = false;
    SET_NEXT_GAMESTATE(gGameState, Play_Init, PlayState);
}

int32_t* oot_scene_probe_state(void) { return state; }

void OotSceneProbe_AfterFrame(void) {
    if (tick < 3 || !gPlayState) return;
    const Player* player = GET_PLAYER(gPlayState);
    state[0] = tick;
    state[1] = gPlayState->sceneNum;
    state[2] = gPlayState->roomCtx.curRoom.num;
    state[3] = gSaveContext.linkAge;
    state[4] = gPlayState->actorCtx.actorLists[ACTORCAT_BOSS].length;
    state[5] = GET_ACTIVE_CAM(gPlayState)->setting;
    state[6] = gSaveContext.health;
    state[7] = gPlayState->csCtx.state;
    state[8] = player->csAction;
    state[9] = gSaveContext.fileNum;
    if (tick % 100 == 0) {
        printf("scene-probe tick=%u scene=%d room=%d age=%d bosses=%d camera=%d health=%d cutscene=%d action=%d slot=%d pos=%.0f,%.0f,%.0f\n",
               tick, state[1], state[2], state[3], state[4], state[5], state[6], state[7], state[8], state[9],
               player->actor.world.pos.x, player->actor.world.pos.y, player->actor.world.pos.z);
        fflush(stdout);
    }
}
