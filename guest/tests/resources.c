#include "../src/shipwright_resources.c"
#include <assert.h>

typedef struct {
    ResourceEntry entry;
    uint8_t bytes[256];
} Fixture;

uint64_t OotResource_Hash(const char* path) {
    if (strcmp(path, "curve") == 0) return 1;
    if (strcmp(path, "paths") == 0) return 2;
    if (strcmp(path, "scene") == 0) return 3;
    assert(0 && "Unknown fixture");
    return 0;
}

int32_t Host_ResourceInfo(uint64_t id, OotResourceInfo* info) {
    assert(0 && "Fixture must already be cached");
    return -1;
}

int32_t Host_ResourceRead(uint64_t id, uint32_t offset, void* destination, uint32_t length) {
    assert(0 && "Fixture must already be cached");
    return -1;
}

static void InitFixture(Fixture* fixture, uint64_t id, uint32_t type) {
    memset(fixture, 0, sizeof(*fixture));
    fixture->entry = (ResourceEntry){ .id = id, .bytes = fixture->bytes, .size = 64 };
    memcpy(fixture->bytes + 4, &type, sizeof(type));
    resources[id] = &fixture->entry;
}

static void WriteBytes(Fixture* fixture, const void* bytes, size_t size) {
    assert(fixture->entry.size + size <= sizeof(fixture->bytes));
    memcpy(fixture->bytes + fixture->entry.size, bytes, size);
    fixture->entry.size += size;
}

static void WriteU16(Fixture* fixture, uint16_t value) {
    WriteBytes(fixture, &value, sizeof(value));
}

static void WriteU32(Fixture* fixture, uint32_t value) {
    WriteBytes(fixture, &value, sizeof(value));
}

static void WriteString(Fixture* fixture, const char* value) {
    WriteU32(fixture, strlen(value));
    WriteBytes(fixture, value, strlen(value));
}

__attribute__((export_name("test_curve_animation")))
void TestCurveAnimation(void) {
    Fixture fixture;
    InitFixture(&fixture, 1, 0x4F414E4D);
    WriteU32(&fixture, 2);
    WriteU16(&fixture, 60);
    WriteU32(&fixture, 3);
    WriteBytes(&fixture, "\x02\x00\x00", 3);
    WriteU32(&fixture, 2);
    for (int i = 0; i < 2; i++) {
        WriteU16(&fixture, i + 1);
        WriteU16(&fixture, -2 - i);
        WriteU16(&fixture, 3 + i);
        WriteU16(&fixture, -4 - i);
        float value = i == 0 ? 1.25f : -2.5f;
        WriteBytes(&fixture, &value, sizeof(value));
    }
    WriteU32(&fixture, 2);
    WriteU16(&fixture, -123);
    WriteU16(&fixture, 456);

    TransformUpdateIndex* animation = (TransformUpdateIndex*)ResourceMgr_LoadAnimByName("curve");
    assert(animation != NULL);
    assert(memcmp(animation->refIndex, "\x02\x00\x00", 3) == 0);
    for (int i = 0; i < 2; i++) {
        TransformData* transform = &animation->transformData[i];
        assert(transform->unk_00 == i + 1);
        assert(transform->unk_02 == -2 - i);
        assert(transform->unk_04 == 3 + i);
        assert(transform->unk_06 == -4 - i);
        assert(transform->unk_08 == (i == 0 ? 1.25f : -2.5f));
    }
    assert(animation->copyValues[0] == -123);
    assert(animation->copyValues[1] == 456);
    assert((void*)ResourceMgr_LoadAnimByName("curve") == animation);
    free(animation->refIndex);
    free(animation->transformData);
    free(animation->copyValues);
    free(animation);
    resources[1] = NULL;
}

__attribute__((export_name("test_scene_paths")))
void TestScenePaths(void) {
    Fixture paths, scene;
    InitFixture(&paths, 2, 0x4F505448);
    WriteU32(&paths, 2);
    for (int i = 0; i < 2; i++) {
        WriteU32(&paths, i + 1);
        for (int point = 0; point <= i; point++) {
            WriteU16(&paths, 10 * i + point);
            WriteU16(&paths, -20 * i - point);
            WriteU16(&paths, 30 * i + point);
        }
    }

    InitFixture(&scene, 3, 0x4F524F4D);
    WriteU32(&scene, 3);
    WriteU32(&scene, 13);
    WriteU32(&scene, 2);
    WriteString(&scene, "paths");
    WriteString(&scene, "paths");
    WriteU32(&scene, 5);
    WriteBytes(&scene, "\x01\x02\x03\x04", 4);
    WriteU32(&scene, 20);

    SceneCmd* commands = LoadSceneByName("scene");
    Path* loaded = commands[0].pathList.segment;
    assert(loaded == LoadPathByName("paths"));
    assert(loaded[0].count == 1);
    assert(loaded[1].count == 2);
    assert(loaded[1].points[1].x == 11);
    assert(loaded[1].points[1].y == -21);
    assert(loaded[1].points[1].z == 31);
    assert(commands[1].base.code == 5);
    assert(commands[1].windSettings.unk_07 == 4);
    assert(commands[2].base.code == 20);
    assert(LoadSceneByName("scene") == commands);
    free(loaded[0].points);
    free(loaded[1].points);
    free(loaded);
    free(commands);
    resources[2] = resources[3] = NULL;
}
