#include <stdlib.h>
#include <string.h>
#include "code/z_scene.c"

SaveContext gSaveContext;
GameInfo* gGameInfo;
uintptr_t gSegments[NUM_SEGMENTS];
u32 gBitFlags[32];
u16 gTimeSpeed;

static int objectCleanups;

void osSyncPrintfUnused(const char* format, ...) {
}

void Actor_KillAllWithMissingObject(PlayState* play, ActorContext* actorCtx) {
    objectCleanups++;
}

__attribute__((export_name("test_scene_reuse")))
void TestSceneReuse(void) {
    PlayState* play = calloc(1, sizeof(PlayState));
    SceneCmd first[] = {
        { .windSettings = { .code = SCENE_CMD_ID_WIND_SETTINGS, .x = 22 } },
        { .base = { .code = SCENE_CMD_ID_END } },
    };
    SceneCmd second[] = {
        { .windSettings = { .code = SCENE_CMD_ID_WIND_SETTINGS, .x = 33 } },
        { .base = { .code = SCENE_CMD_ID_END } },
    };
    SceneCmd* alternatives[] = { first, second, NULL };
    SceneCmd commands[] = {
        { .altHeaders = { .code = SCENE_CMD_ID_ALTERNATE_HEADER_LIST, .segment = alternatives } },
        { .windSettings = { .code = SCENE_CMD_ID_WIND_SETTINGS, .x = 11 } },
        { .base = { .code = SCENE_CMD_ID_END } },
    };
    SceneCmd original[ARRAY_COUNT(commands)];
    memcpy(original, commands, sizeof(commands));
    const int layers[] = { 0, 1, 0, 2, 3, 0 };
    const int expected[] = { 11, 22, 11, 33, 33, 11 };
    for (int i = 0; i < ARRAY_COUNT(layers); i++) {
        gSaveContext.sceneLayer = layers[i];
        play->envCtx.windDirection.x = 0;
        Scene_ExecuteCommands(play, commands);
        assert(play->envCtx.windDirection.x == expected[i]);
        assert(memcmp(commands, original, sizeof(commands)) == 0);
    }
    alternatives[0] = NULL;
    gSaveContext.sceneLayer = 1;
    Scene_ExecuteCommands(play, commands);
    assert(play->envCtx.windDirection.x == 11);
    assert(memcmp(commands, original, sizeof(commands)) == 0);
    gSaveContext.sceneLayer = 0;
    free(play);
}

__attribute__((export_name("test_scene_objects")))
void TestSceneObjects(void) {
    PlayState* play = calloc(1, sizeof(PlayState));
    play->objectCtx.num = 3;
    play->objectCtx.status[0].id = 10;
    play->objectCtx.status[1].id = 20;
    play->objectCtx.status[2].id = 30;
    // Only the first element belongs to the new list. The others catch overreads.
    s16 objects[] = { 10, 20, 30 };
    SceneCmd command = { .objectList = { .code = SCENE_CMD_ID_OBJECT_LIST, .num = 1, .segment = objects } };
    objectCleanups = 0;
    Scene_CommandObjectList(play, &command);
    assert(play->objectCtx.num == 1);
    assert(play->objectCtx.status[0].id == 10);
    assert(play->objectCtx.status[1].id == OBJECT_INVALID);
    assert(play->objectCtx.status[2].id == OBJECT_INVALID);
    assert(objectCleanups == 1);

    Scene_CommandObjectList(play, &command);
    assert(play->objectCtx.num == 1);
    assert(objectCleanups == 1);

    command.objectList.num = 0;
    command.objectList.segment = NULL;
    Scene_CommandObjectList(play, &command);
    assert(play->objectCtx.num == 0);
    assert(play->objectCtx.status[0].id == OBJECT_INVALID);
    assert(objectCleanups == 2);
    free(play);
}

__attribute__((export_name("test_transition_reuse")))
void TestTransitionReuse(void) {
    PlayState* play = calloc(1, sizeof(PlayState));
    TransitionActorEntry actors[] = { { .id = -9 }, { .id = 10 }, { .id = 0 } };
    SceneCmd commands[] = {
        { .transiActorList = { .code = SCENE_CMD_ID_TRANSITION_ACTOR_LIST, .num = 3, .segment = actors } },
        { .base = { .code = SCENE_CMD_ID_END } },
    };
    for (int visit = 0; visit < 3; visit++) {
        Scene_ExecuteCommands(play, commands);
        assert(play->transiActorCtx.list == actors);
        assert(play->transiActorCtx.numActors == 3);
        assert(actors[0].id == 9);
        assert(actors[1].id == 10);
        assert(actors[2].id == 0);
        actors[0].id = -actors[0].id;
        actors[1].id = -actors[1].id;
    }
    free(play);
}
