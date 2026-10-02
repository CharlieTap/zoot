#include "global.h"
#include "oot_guest.h"
#include "soh/ResourceManagerHelpers.h"
#include "message_data_static.h"
#include <libultraship/bridge/resourcebridge.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

void Play_InitScene(PlayState* play, s32 spawn);

extern MessageTableEntry* sNesMessageEntryTablePtr;
extern MessageTableEntry* sGerMessageEntryTablePtr;
extern MessageTableEntry* sFraMessageEntryTablePtr;
extern MessageTableEntry* sJpnMessageEntryTablePtr;
extern MessageTableEntry* sStaffMessageEntryTablePtr;

__attribute__((import_module("oot_resources"), import_name("resource_info")))
int32_t Host_ResourceInfo(uint64_t id, OotResourceInfo* info);

__attribute__((import_module("oot_resources"), import_name("resource_read")))
int32_t Host_ResourceRead(uint64_t id, uint32_t offset, void* destination, uint32_t length);

__attribute__((import_module("oot_resources"), import_name("resource_list")))
int32_t Host_ResourceList(const char* prefix, uint32_t prefixLength, uint32_t index, char* destination,
                          uint32_t capacity);

#ifdef OOT_WASM_DIAGNOSTICS
__attribute__((import_module("oot_diagnostics"), import_name("checkpoint")))
void OotDiagnostics_Checkpoint(s32 stage, s32 value);
#endif

typedef struct ResourceEntry {
    uint64_t id;
    uint8_t* bytes;
    uint32_t size;
    void* parsed;
    struct ResourceEntry* next;
} ResourceEntry;

#define RESOURCE_BUCKET_COUNT 4096
static ResourceEntry* resources[RESOURCE_BUCKET_COUNT];

uint64_t OotResource_Hash(const char* path);

static uint64_t ResourceHash(const char* path) {
    if (path == NULL) {
        return 0;
    }
    if (memcmp(path, "__OTR__", 7) == 0) {
        path += 7;
    }
    return OotResource_Hash(path);
}

static uint16_t ReadU16(const uint8_t* bytes) {
    return (uint16_t)bytes[0] | (uint16_t)bytes[1] << 8;
}

static uint32_t ReadU32(const uint8_t* bytes) {
    return (uint32_t)bytes[0] | (uint32_t)bytes[1] << 8 | (uint32_t)bytes[2] << 16 | (uint32_t)bytes[3] << 24;
}

typedef struct {
    const uint8_t* cursor;
    const uint8_t* end;
} ResourceReader;

static uint8_t ReaderU8(ResourceReader* reader) {
    return reader->cursor < reader->end ? *reader->cursor++ : 0;
}

static uint16_t ReaderU16(ResourceReader* reader) {
    if (reader->end - reader->cursor < 2) {
        reader->cursor = reader->end;
        return 0;
    }
    uint16_t value = ReadU16(reader->cursor);
    reader->cursor += 2;
    return value;
}

static uint32_t ReaderU32(ResourceReader* reader) {
    if (reader->end - reader->cursor < 4) {
        reader->cursor = reader->end;
        return 0;
    }
    uint32_t value = ReadU32(reader->cursor);
    reader->cursor += 4;
    return value;
}

static float ReaderF32(ResourceReader* reader) {
    union {
        uint32_t bits;
        float value;
    } result = { .bits = ReaderU32(reader) };
    return result.value;
}

static char* ReaderString(ResourceReader* reader) {
    uint32_t length = ReaderU32(reader);
    if ((uint32_t)(reader->end - reader->cursor) < length) {
        reader->cursor = reader->end;
        return NULL;
    }
    char* string = malloc(length + 1);
    if (string == NULL) {
        return NULL;
    }
    memcpy(string, reader->cursor, length);
    string[length] = '\0';
    reader->cursor += length;
    return string;
}

static void ReaderBytes(ResourceReader* reader, void* destination, uint32_t length) {
    if ((uint32_t)(reader->end - reader->cursor) < length) {
        memset(destination, 0, length);
        reader->cursor = reader->end;
        return;
    }
    memcpy(destination, reader->cursor, length);
    reader->cursor += length;
}

static char* ReaderOtrPath(ResourceReader* reader) {
    char* path = ReaderString(reader);
    if (path == NULL || path[0] == '\0') {
        free(path);
        return NULL;
    }
    size_t length = strlen(path);
    char* otrPath = malloc(length + 8);
    if (otrPath != NULL) {
        memcpy(otrPath, "__OTR__", 7);
        memcpy(otrPath + 7, path, length + 1);
    }
    free(path);
    return otrPath;
}

static ResourceEntry* FindResource(uint64_t id) {
    for (ResourceEntry* entry = resources[id & (RESOURCE_BUCKET_COUNT - 1)]; entry != NULL; entry = entry->next) {
        if (entry->id == id) {
            return entry;
        }
    }
    return NULL;
}

static ResourceEntry* LoadResource(uint64_t id) {
    ResourceEntry* cached = FindResource(id);
    if (cached != NULL) {
        return cached;
    }
    OotResourceInfo info = { .byte_size = sizeof(OotResourceInfo) };
    if (Host_ResourceInfo(id, &info) != 0 || info.byte_size != sizeof(OotResourceInfo) || info.resource_size < 64) {
        return NULL;
    }
    ResourceEntry* entry = calloc(1, sizeof(ResourceEntry));
    entry->bytes = malloc(info.resource_size);
    if (entry->bytes == NULL || Host_ResourceRead(id, 0, entry->bytes, info.resource_size) != (int32_t)info.resource_size) {
        free(entry->bytes);
        free(entry);
        return NULL;
    }
    entry->id = id;
    entry->size = info.resource_size;
    uint32_t bucket = id & (RESOURCE_BUCKET_COUNT - 1);
    entry->next = resources[bucket];
    resources[bucket] = entry;
    return entry;
}

static ResourceEntry* LoadResourceByName(const char* path) {
    return LoadResource(ResourceHash(path));
}

static uint32_t ResourceType(const ResourceEntry* entry);

static MessageTableEntry* LoadMessageTable(const char* path) {
    ResourceEntry* entry = LoadResourceByName(path);
    if (entry == NULL || ResourceType(entry) != 0x4F545854) { // OTXT
        return NULL;
    }
    if (entry->parsed != NULL) {
        return entry->parsed;
    }
    ResourceReader reader = { entry->bytes + 64, entry->bytes + entry->size };
    uint32_t count = ReaderU32(&reader);
    MessageTableEntry* messages = calloc(count + 1, sizeof(MessageTableEntry));
    for (uint32_t i = 0; i < count; i++) {
        messages[i].textId = ReaderU16(&reader);
        uint8_t boxType = ReaderU8(&reader);
        uint8_t boxPosition = ReaderU8(&reader);
        messages[i].typePos = (boxType << 4) | boxPosition;
        messages[i].msgSize = ReaderU32(&reader);
        char* text = calloc(messages[i].msgSize + 1, 1);
        ReaderBytes(&reader, text, messages[i].msgSize);
        messages[i].segment = text;
    }
    messages[count].textId = 0xFFFF;
    entry->parsed = messages;
    return messages;
}

void OTRMessage_Init(void) {
    if (sNesMessageEntryTablePtr == NULL) {
        sNesMessageEntryTablePtr = LoadMessageTable("text/nes_message_data_static/nes_message_data_static");
        if (sNesMessageEntryTablePtr == NULL) {
            sNesMessageEntryTablePtr = LoadMessageTable("text/nes_message_data_static/ntsc_nes_message_data_static");
        }
        if (sNesMessageEntryTablePtr != NULL) {
            for (MessageTableEntry* message = sNesMessageEntryTablePtr; message->textId != 0xFFFF; message++) {
                if (message->textId == 0xFFFC) {
                    _message_0xFFFC_nes = (char*)message->segment;
                    break;
                }
            }
        }
    }
    if (sJpnMessageEntryTablePtr == NULL) {
        sJpnMessageEntryTablePtr = LoadMessageTable("text/jpn_message_data_static/jpn_message_data_static");
    }
    if (sGerMessageEntryTablePtr == NULL) sGerMessageEntryTablePtr = sNesMessageEntryTablePtr;
    if (sFraMessageEntryTablePtr == NULL) sFraMessageEntryTablePtr = sNesMessageEntryTablePtr;
    if (sStaffMessageEntryTablePtr == NULL) {
        sStaffMessageEntryTablePtr = LoadMessageTable("text/staff_message_data_static/staff_message_data_static");
    }
}

static uint32_t ResourceType(const ResourceEntry* entry) {
    return entry != NULL && entry->size >= 8 ? ReadU32(entry->bytes + 4) : 0;
}

static uint16_t SwapU16(uint16_t value) {
    return (uint16_t)(value << 8) | (uint16_t)(value >> 8);
}

static SequenceData* ParseAudioSequence(ResourceEntry* entry) {
    if (entry == NULL || ResourceType(entry) != 0x4F534551) {
        return NULL;
    }
    if (entry->parsed != NULL) {
        return entry->parsed;
    }
    ResourceReader reader = { entry->bytes + 64, entry->bytes + entry->size };
    SequenceData* sequence = calloc(1, sizeof(SequenceData));
    sequence->seqDataSize = (int32_t)ReaderU32(&reader);
    sequence->seqData = malloc(sequence->seqDataSize);
    ReaderBytes(&reader, sequence->seqData, sequence->seqDataSize);
    sequence->seqNumber = ReaderU8(&reader);
    sequence->medium = ReaderU8(&reader);
    sequence->cachePolicy = ReaderU8(&reader);
    sequence->numFonts = (int32_t)ReaderU32(&reader);
    for (int32_t index = 0; index < sequence->numFonts && index < (int32_t)ARRAY_COUNT(sequence->fonts); index++) {
        sequence->fonts[index] = ReaderU8(&reader);
    }
    entry->parsed = sequence;
    return sequence;
}

static SoundFontSample* ParseAudioSample(ResourceEntry* entry) {
    if (entry == NULL || ResourceType(entry) != 0x4F534D50) {
        return NULL;
    }
    if (entry->parsed != NULL) {
        return entry->parsed;
    }
    ResourceReader reader = { entry->bytes + 64, entry->bytes + entry->size };
    SoundFontSample* sample = calloc(1, sizeof(SoundFontSample));
    sample->codec = ReaderU8(&reader);
    sample->medium = ReaderU8(&reader);
    sample->unk_bit26 = ReaderU8(&reader);
    sample->isRelocated = ReaderU8(&reader);
    sample->size = ReaderU32(&reader);
    sample->fileSize = sample->size;
    sample->sampleAddr = calloc(1, sample->size + 16);
    ReaderBytes(&reader, sample->sampleAddr, sample->size);

    sample->loop = calloc(1, sizeof(AdpcmLoop));
    sample->loop->start = ReaderU32(&reader);
    sample->loop->loopEnd = ReaderU32(&reader);
    sample->loop->count = ReaderU32(&reader);
    uint32_t stateCount = ReaderU32(&reader);
    for (uint32_t index = 0; index < stateCount; index++) {
        uint16_t value = ReaderU16(&reader);
        if (index < ARRAY_COUNT(sample->loop->predictorState)) {
            sample->loop->predictorState[index] = (int16_t)value;
        }
    }

    sample->book = calloc(1, sizeof(AdpcmBook));
    sample->book->order = (int32_t)ReaderU32(&reader);
    sample->book->npredictors = (int32_t)ReaderU32(&reader);
    uint32_t bookCount = ReaderU32(&reader);
    sample->book->book = malloc(bookCount * sizeof(int16_t));
    for (uint32_t index = 0; index < bookCount; index++) {
        sample->book->book[index] = (int16_t)ReaderU16(&reader);
    }
    entry->parsed = sample;
    return sample;
}

static void ReadEnvelope(ResourceReader* reader, AdsrEnvelope** destination) {
    uint32_t count = ReaderU32(reader);
    AdsrEnvelope* envelope = calloc(count, sizeof(AdsrEnvelope));
    for (uint32_t index = 0; index < count; index++) {
        envelope[index].delay = (int16_t)SwapU16(ReaderU16(reader));
        envelope[index].arg = (int16_t)SwapU16(ReaderU16(reader));
    }
    *destination = envelope;
}

static void ReadFontSound(ResourceReader* reader, SoundFontSound* sound) {
    if (ReaderU8(reader) == 0) {
        return;
    }
    ReaderU8(reader); // Legacy has-sample-reference flag.
    char* path = ReaderString(reader);
    sound->tuning = ReaderF32(reader);
    sound->sample = ResourceMgr_LoadAudioSample(path);
    free(path);
}

static SoundFont* ParseAudioSoundFont(ResourceEntry* entry) {
    if (entry == NULL || ResourceType(entry) != 0x4F534654) {
        return NULL;
    }
    if (entry->parsed != NULL) {
        return entry->parsed;
    }
    ResourceReader reader = { entry->bytes + 64, entry->bytes + entry->size };
    SoundFont* font = calloc(1, sizeof(SoundFont));
    font->fntIndex = (int32_t)ReaderU32(&reader);
    ReaderU8(&reader); // Medium; samples are already resident in guest memory.
    ReaderU8(&reader); // Cache policy; font headers are tiny and cached by ResourceEntry.
    uint16_t banks = ReaderU16(&reader);
    font->sampleBankId1 = banks >> 8;
    font->sampleBankId2 = banks & 0xff;
    ReaderU16(&reader);
    ReaderU16(&reader);
    uint32_t drumCount = ReaderU32(&reader);
    uint32_t instrumentCount = ReaderU32(&reader);
    uint32_t effectCount = ReaderU32(&reader);
    font->numDrums = (uint8_t)drumCount;
    font->numInstruments = (uint8_t)instrumentCount;
    font->numSfx = (uint16_t)effectCount;

    font->drums = calloc(drumCount, sizeof(Drum*));
    for (uint32_t index = 0; index < drumCount; index++) {
        Drum* drum = calloc(1, sizeof(Drum));
        drum->releaseRate = ReaderU8(&reader);
        drum->pan = ReaderU8(&reader);
        ReaderU8(&reader);
        ReadEnvelope(&reader, &drum->envelope);
        ReaderU8(&reader);
        char* path = ReaderString(&reader);
        drum->sound.tuning = ReaderF32(&reader);
        if (path[0] != '\0') {
            drum->sound.sample = ResourceMgr_LoadAudioSample(path);
        }
        free(path);
        if (drum->sound.sample != NULL) {
            font->drums[index] = drum;
        } else {
            free(drum->envelope);
            free(drum);
        }
    }

    font->instruments = calloc(instrumentCount, sizeof(Instrument*));
    for (uint32_t index = 0; index < instrumentCount; index++) {
        uint8_t valid = ReaderU8(&reader);
        Instrument* instrument = calloc(1, sizeof(Instrument));
        ReaderU8(&reader);
        instrument->normalRangeLo = ReaderU8(&reader);
        instrument->normalRangeHi = ReaderU8(&reader);
        instrument->releaseRate = ReaderU8(&reader);
        ReadEnvelope(&reader, &instrument->envelope);
        ReadFontSound(&reader, &instrument->lowNotesSound);
        ReadFontSound(&reader, &instrument->normalNotesSound);
        ReadFontSound(&reader, &instrument->highNotesSound);
        if (valid) {
            font->instruments[index] = instrument;
        } else {
            free(instrument->envelope);
            free(instrument);
        }
    }

    font->soundEffects = calloc(effectCount, sizeof(SoundFontSound));
    for (uint32_t index = 0; index < effectCount; index++) {
        ReadFontSound(&reader, &font->soundEffects[index]);
    }
    entry->parsed = font;
    return font;
}

static void* ResourceData(ResourceEntry* entry) {
    if (entry == NULL || entry->size < 68) {
        return NULL;
    }
    switch (ResourceType(entry)) {
        case 0x4F415252: { // OARR
            uint32_t arrayType = ReadU32(entry->bytes + 64);
            return arrayType == 25 ? entry->bytes + 72 : entry->bytes + 64;
        }
        case 0x4F444C54: // ODLT
            return entry->bytes + 72;
        case 0x4F4D5458: // OMTX
            return entry->bytes + 64;
        case 0x4F50414D: // OPAM
        case 0x4F435654: // OCUT
        case 0x4F424749: // OBGI
        case 0x4F424C42: // OBLB
            return entry->bytes + 68;
        case 0x4F544558: { // OTEX
            uint32_t version = ReadU32(entry->bytes + 8);
            return entry->bytes + (version == 0 ? 80 : 92);
        }
        case 0x4F534551: // OSEQ
            return ParseAudioSequence(entry);
        case 0x4F534D50: // OSMP
            return ParseAudioSample(entry);
        case 0x4F534654: // OSFT
            return ParseAudioSoundFont(entry);
        default:
            return entry->bytes + 64;
    }
}

char** ResourceMgr_ListFiles(const char* searchMask, int* resultSize) {
    size_t prefixLength = strcspn(searchMask, "*");
    size_t capacity = 32;
    size_t count = 0;
    char** paths = malloc(capacity * sizeof(char*));
    while (true) {
        int32_t length = Host_ResourceList(searchMask, (uint32_t)prefixLength, (uint32_t)count, NULL, 0);
        if (length < 0) {
            break;
        }
        if (count == capacity) {
            capacity *= 2;
            paths = realloc(paths, capacity * sizeof(char*));
        }
        paths[count] = malloc((size_t)length + 1);
        if (Host_ResourceList(searchMask, (uint32_t)prefixLength, (uint32_t)count, paths[count],
                              (uint32_t)length + 1) != length) {
            free(paths[count]);
            break;
        }
        count++;
    }
    *resultSize = (int)count;
    return paths;
}

SequenceData ResourceMgr_LoadSeqByName(const char* path) {
    SequenceData* sequence = ParseAudioSequence(LoadResourceByName(path));
    return sequence != NULL ? *sequence : (SequenceData){ 0 };
}

SequenceData* ResourceMgr_LoadSeqPtrByName(const char* path) {
    return ParseAudioSequence(LoadResourceByName(path));
}

SoundFontSample* ResourceMgr_LoadAudioSample(const char* path) {
    return ParseAudioSample(LoadResourceByName(path));
}

SoundFont* ResourceMgr_LoadAudioSoundFontByName(const char* path) {
    return ParseAudioSoundFont(LoadResourceByName(path));
}

uint64_t ResourceGetCrcByName(const char* name) {
    return ResourceHash(name);
}

void* ResourceGetDataByName(const char* name) {
    return ResourceData(LoadResourceByName(name));
}

void* ResourceGetDataByCrc(uint64_t crc) {
    return ResourceData(LoadResource(crc));
}

size_t ResourceGetSizeByName(const char* name) {
    ResourceEntry* entry = LoadResourceByName(name);
    if (entry == NULL) {
        return 0;
    }
    return entry->size - ((uint8_t*)ResourceData(entry) - entry->bytes);
}

Gfx* ResourceMgr_LoadGfxByName(const char* path) {
    return ResourceData(LoadResourceByName(path));
}

uint8_t ResourceGetIsCustomByName(const char* name) {
    (void)name;
    return 0;
}

uint16_t ResourceGetTexWidthByName(const char* name) {
    ResourceEntry* entry = LoadResourceByName(name);
    return ResourceType(entry) == 0x4F544558 && entry->size >= 76 ? (uint16_t)ReadU32(entry->bytes + 68) : 0;
}

uint16_t ResourceGetTexHeightByName(const char* name) {
    ResourceEntry* entry = LoadResourceByName(name);
    return ResourceType(entry) == 0x4F544558 && entry->size >= 76 ? (uint16_t)ReadU32(entry->bytes + 72) : 0;
}

size_t ResourceGetTexSizeByName(const char* name) {
    ResourceEntry* entry = LoadResourceByName(name);
    if (ResourceType(entry) != 0x4F544558 || entry->size < 84) {
        return 0;
    }
    return ReadU32(entry->bytes + (ReadU32(entry->bytes + 8) == 0 ? 76 : 88));
}

uint8_t ResourceMgr_FileExists(const char* path) {
    OotResourceInfo info = { .byte_size = sizeof(OotResourceInfo) };
    return Host_ResourceInfo(ResourceHash(path), &info) == 0;
}

int ResourceMgr_OTRSigCheck(char* data) {
    uintptr_t address = (uintptr_t)data;
    return address != 0 && (address & 1) == 0 && memcmp(data, "__OTR__", 7) == 0;
}

void* ResourceMgr_LoadIfDListByName(const char* path) {
    ResourceEntry* entry = LoadResourceByName(path);
    return ResourceType(entry) == 0x4F444C54 ? ResourceData(entry) : NULL;
}

uint8_t ResourceMgr_TexIsRaw(const char* path) {
    ResourceEntry* entry = LoadResourceByName(path);
    return ResourceType(entry) == 0x4F544558 && ReadU32(entry->bytes + 8) > 0 && (ReadU32(entry->bytes + 76) & 1) != 0;
}

uint8_t ResourceMgr_ResourceIsBackground(char* path) {
    return ResourceType(LoadResourceByName(path)) == 0x4F424749; // OBGI
}

Vtx* ResourceMgr_LoadVtxByCRC(uint64_t crc) {
    return ResourceData(LoadResource(crc));
}

Vtx* ResourceMgr_LoadVtxByName(char* path) {
    return ResourceData(LoadResourceByName(path));
}

s32* ResourceMgr_LoadCSByName(const char* path) {
    return ResourceData(LoadResourceByName(path));
}

char* ResourceMgr_LoadPlayerAnimByName(const char* path) {
    return ResourceData(LoadResourceByName(path));
}

char* ResourceMgr_LoadArrayByNameAsVec3s(const char* path) {
    ResourceEntry* entry = LoadResourceByName(path);
    if (entry == NULL || ResourceType(entry) != 0x4F415252 || ReadU32(entry->bytes + 64) != 24) {
        return NULL;
    }
    const uint8_t* cursor = entry->bytes + 72;
    uint32_t count = ReadU32(entry->bytes + 68);
    Vec3s* vectors = malloc(sizeof(Vec3s) * count);
    for (uint32_t index = 0; index < count; index++) {
        uint32_t scalarType = ReadU32(cursor);
        uint32_t components = ReadU32(cursor + 4);
        cursor += 8;
        if (scalarType != 4 || components != 3) {
            free(vectors);
            return NULL;
        }
        vectors[index].x = (int16_t)ReadU16(cursor);
        vectors[index].y = (int16_t)ReadU16(cursor + 2);
        vectors[index].z = (int16_t)ReadU16(cursor + 4);
        cursor += 6;
    }
    return (char*)vectors;
}

AnimationHeaderCommon* ResourceMgr_LoadAnimByName(const char* path) {
    ResourceEntry* entry = LoadResourceByName(path);
    if (entry == NULL || ResourceType(entry) != 0x4F414E4D) {
        return NULL;
    }
    if (entry->parsed != NULL) {
        return entry->parsed;
    }
    const uint8_t* cursor = entry->bytes + 64;
    uint32_t animationType = ReadU32(cursor);
    cursor += 4;
    if (animationType == 0) {
        AnimationHeader* animation = calloc(1, sizeof(AnimationHeader));
        animation->common.frameCount = (int16_t)ReadU16(cursor);
        cursor += 2;
        uint32_t frameDataCount = ReadU32(cursor);
        cursor += 4;
        animation->frameData = malloc(frameDataCount * sizeof(int16_t));
        memcpy(animation->frameData, cursor, frameDataCount * sizeof(int16_t));
        cursor += frameDataCount * sizeof(int16_t);
        uint32_t jointCount = ReadU32(cursor);
        cursor += 4;
        animation->jointIndices = malloc(jointCount * sizeof(JointIndex));
        memcpy(animation->jointIndices, cursor, jointCount * sizeof(JointIndex));
        cursor += jointCount * sizeof(JointIndex);
        animation->staticIndexMax = ReadU16(cursor);
        entry->parsed = animation;
    } else if (animationType == 1) {
        LinkAnimationHeader* animation = calloc(1, sizeof(LinkAnimationHeader));
        animation->common.frameCount = (int16_t)ReadU16(cursor);
        cursor += 2;
        uint32_t pathLength = ReadU32(cursor);
        cursor += 4;
        char* segmentPath = malloc(pathLength + 1);
        memcpy(segmentPath, cursor, pathLength);
        segmentPath[pathLength] = '\0';
        animation->segment = ResourceGetDataByName(segmentPath);
        free(segmentPath);
        entry->parsed = animation;
    } else if (animationType == 2) {
        ResourceReader reader = { cursor, entry->bytes + entry->size };
        TransformUpdateIndex* animation = calloc(1, sizeof(TransformUpdateIndex));
        ReaderU16(&reader); // Frame count is unused for curve animations.
        uint32_t referenceCount = ReaderU32(&reader);
        animation->refIndex = malloc(referenceCount);
        ReaderBytes(&reader, animation->refIndex, referenceCount);

        uint32_t transformCount = ReaderU32(&reader);
        animation->transformData = calloc(transformCount, sizeof(TransformData));
        for (uint32_t i = 0; i < transformCount; i++) {
            TransformData* transform = &animation->transformData[i];
            transform->unk_00 = ReaderU16(&reader);
            transform->unk_02 = (s16)ReaderU16(&reader);
            transform->unk_04 = (s16)ReaderU16(&reader);
            transform->unk_06 = (s16)ReaderU16(&reader);
            transform->unk_08 = ReaderF32(&reader);
        }

        uint32_t copyCount = ReaderU32(&reader);
        animation->copyValues = malloc(copyCount * sizeof(s16));
        for (uint32_t i = 0; i < copyCount; i++) {
            animation->copyValues[i] = (s16)ReaderU16(&reader);
        }
        entry->parsed = animation;
    }
    return entry->parsed;
}

CollisionHeader* ResourceMgr_LoadColByName(const char* path) {
    ResourceEntry* entry = LoadResourceByName(path);
    if (entry == NULL || ResourceType(entry) != 0x4F434F4C) { // OCOL
        return NULL;
    }
    if (entry->parsed != NULL) {
        return entry->parsed;
    }

    ResourceReader reader = { entry->bytes + 64, entry->bytes + entry->size };
    CollisionHeader* collision = calloc(1, sizeof(CollisionHeader));
    collision->minBounds.x = (s16)ReaderU16(&reader);
    collision->minBounds.y = (s16)ReaderU16(&reader);
    collision->minBounds.z = (s16)ReaderU16(&reader);
    collision->maxBounds.x = (s16)ReaderU16(&reader);
    collision->maxBounds.y = (s16)ReaderU16(&reader);
    collision->maxBounds.z = (s16)ReaderU16(&reader);

    uint32_t vertexCount = ReaderU32(&reader);
    collision->numVertices = (u16)vertexCount;
    collision->vtxList = calloc(vertexCount, sizeof(Vec3s));
    for (uint32_t i = 0; i < vertexCount; i++) {
        collision->vtxList[i].x = (s16)ReaderU16(&reader);
        collision->vtxList[i].y = (s16)ReaderU16(&reader);
        collision->vtxList[i].z = (s16)ReaderU16(&reader);
    }

    uint32_t polygonCount = ReaderU32(&reader);
    collision->numPolygons = (u16)polygonCount;
    collision->polyList = calloc(polygonCount, sizeof(CollisionPoly));
    for (uint32_t i = 0; i < polygonCount; i++) {
        CollisionPoly* polygon = &collision->polyList[i];
        polygon->type = ReaderU16(&reader);
        polygon->flags_vIA = ReaderU16(&reader);
        polygon->flags_vIB = ReaderU16(&reader);
        polygon->vIC = ReaderU16(&reader);
        polygon->normal.x = (s16)ReaderU16(&reader);
        polygon->normal.y = (s16)ReaderU16(&reader);
        polygon->normal.z = (s16)ReaderU16(&reader);
        polygon->dist = (s16)ReaderU16(&reader);
    }

    uint32_t surfaceCount = ReaderU32(&reader);
    collision->surfaceTypeList = calloc(surfaceCount, sizeof(SurfaceType));
    for (uint32_t i = 0; i < surfaceCount; i++) {
        collision->surfaceTypeList[i].data[1] = ReaderU32(&reader);
        collision->surfaceTypeList[i].data[0] = ReaderU32(&reader);
    }

    uint32_t cameraCount = ReaderU32(&reader);
    collision->cameraDataListLen = cameraCount;
    collision->cameraDataList = calloc(cameraCount, sizeof(CamData));
    int32_t* cameraIndices = calloc(cameraCount, sizeof(int32_t));
    for (uint32_t i = 0; i < cameraCount; i++) {
        collision->cameraDataList[i].cameraSType = ReaderU16(&reader);
        collision->cameraDataList[i].numCameras = (s16)ReaderU16(&reader);
        cameraIndices[i] = (int32_t)ReaderU32(&reader);
    }
    uint32_t cameraPositionCount = ReaderU32(&reader);
    Vec3s* cameraPositions = calloc(cameraPositionCount > 0 ? cameraPositionCount : 1, sizeof(Vec3s));
    for (uint32_t i = 0; i < cameraPositionCount; i++) {
        cameraPositions[i].x = (s16)ReaderU16(&reader);
        cameraPositions[i].y = (s16)ReaderU16(&reader);
        cameraPositions[i].z = (s16)ReaderU16(&reader);
    }
    for (uint32_t i = 0; i < cameraCount; i++) {
        int32_t index = cameraIndices[i];
        collision->cameraDataList[i].camPosData =
            cameraPositionCount > 0 && index >= 0 && (uint32_t)index < cameraPositionCount ? &cameraPositions[index]
                                                                                          : cameraPositions;
    }
    free(cameraIndices);

    uint32_t waterBoxCount = ReaderU32(&reader);
    collision->numWaterBoxes = (u16)waterBoxCount;
    collision->waterBoxes = calloc(waterBoxCount, sizeof(WaterBox));
    for (uint32_t i = 0; i < waterBoxCount; i++) {
        WaterBox* waterBox = &collision->waterBoxes[i];
        waterBox->xMin = (s16)ReaderU16(&reader);
        waterBox->ySurface = (s16)ReaderU16(&reader);
        waterBox->zMin = (s16)ReaderU16(&reader);
        waterBox->xLength = (s16)ReaderU16(&reader);
        waterBox->zLength = (s16)ReaderU16(&reader);
        waterBox->properties = ReaderU32(&reader);
    }
    entry->parsed = collision;
    return collision;
}

SkeletonHeader* ResourceMgr_LoadSkeletonByName(const char* path, SkelAnime* skelAnime) {
    (void)skelAnime;
    ResourceEntry* entry = LoadResourceByName(path);
    if (entry == NULL || ResourceType(entry) != 0x4F534B4C) { // OSKL
        return NULL;
    }
    if (entry->parsed != NULL) {
        return entry->parsed;
    }
    ResourceReader reader = { entry->bytes + 64, entry->bytes + entry->size };
    uint8_t skeletonType = ReaderU8(&reader);
    ReaderU8(&reader);
    uint32_t limbCount = ReaderU32(&reader);
    uint32_t displayListCount = ReaderU32(&reader);
    ReaderU8(&reader);
    uint32_t tableCount = ReaderU32(&reader);
    void** limbs = calloc(tableCount, sizeof(void*));
    for (uint32_t i = 0; i < tableCount; i++) {
        char* limbPath = ReaderString(&reader);
        ResourceEntry* limbEntry = LoadResourceByName(limbPath);
        free(limbPath);
        if (limbEntry == NULL || ResourceType(limbEntry) != 0x4F534C42) { // OSLB
            continue;
        }
        if (limbEntry->parsed == NULL) {
            ResourceReader limbReader = { limbEntry->bytes + 64, limbEntry->bytes + limbEntry->size };
            uint8_t limbType = ReaderU8(&limbReader);
            uint8_t skinType = ReaderU8(&limbReader);
            char* skinDisplayList = ReaderOtrPath(&limbReader);
            uint16_t skinVertexCount = ReaderU16(&limbReader);
            uint32_t modificationCount = ReaderU32(&limbReader);
            SkinLimbModif* modifications = calloc(modificationCount, sizeof(SkinLimbModif));
            for (uint32_t modification = 0; modification < modificationCount; modification++) {
                modifications[modification].unk_4 = ReaderU16(&limbReader);
                uint32_t vertexCount = ReaderU32(&limbReader);
                modifications[modification].vtxCount = (u16)vertexCount;
                modifications[modification].skinVertices = calloc(vertexCount, sizeof(SkinVertex));
                for (uint32_t vertex = 0; vertex < vertexCount; vertex++) {
                    SkinVertex* value = &modifications[modification].skinVertices[vertex];
                    value->index = ReaderU16(&limbReader);
                    value->s = (s16)ReaderU16(&limbReader);
                    value->t = (s16)ReaderU16(&limbReader);
                    value->normX = (s8)ReaderU8(&limbReader);
                    value->normY = (s8)ReaderU8(&limbReader);
                    value->normZ = (s8)ReaderU8(&limbReader);
                    value->alpha = ReaderU8(&limbReader);
                }
                uint32_t transformCount = ReaderU32(&limbReader);
                modifications[modification].transformCount = (u16)transformCount;
                modifications[modification].limbTransformations = calloc(transformCount, sizeof(SkinTransformation));
                for (uint32_t transform = 0; transform < transformCount; transform++) {
                    SkinTransformation* value = &modifications[modification].limbTransformations[transform];
                    value->limbIndex = ReaderU8(&limbReader);
                    value->x = (s16)ReaderU16(&limbReader);
                    value->y = (s16)ReaderU16(&limbReader);
                    value->z = (s16)ReaderU16(&limbReader);
                    value->scale = ReaderU8(&limbReader);
                }
            }
            char* skinDisplayList2 = ReaderOtrPath(&limbReader);
            ReaderU32(&limbReader);
            ReaderU32(&limbReader);
            ReaderU32(&limbReader);
            ReaderU16(&limbReader);
            ReaderU16(&limbReader);
            ReaderU16(&limbReader);
            free(ReaderString(&limbReader));
            free(ReaderString(&limbReader));
            char* displayList = ReaderOtrPath(&limbReader);
            char* displayList2 = ReaderOtrPath(&limbReader);
            Vec3s translation = { (s16)ReaderU16(&limbReader), (s16)ReaderU16(&limbReader),
                                  (s16)ReaderU16(&limbReader) };
            uint8_t child = ReaderU8(&limbReader);
            uint8_t sibling = ReaderU8(&limbReader);
            if (limbType == 1) {
                StandardLimb* limb = calloc(1, sizeof(StandardLimb));
                limb->jointPos = translation;
                limb->child = child;
                limb->sibling = sibling;
                limb->dList = (Gfx*)displayList;
                limbEntry->parsed = limb;
            } else if (limbType == 2) {
                LodLimb* limb = calloc(1, sizeof(LodLimb));
                limb->jointPos = translation;
                limb->child = child;
                limb->sibling = sibling;
                limb->dLists[0] = (Gfx*)displayList;
                limb->dLists[1] = (Gfx*)displayList2;
                limbEntry->parsed = limb;
            } else if (limbType == 3) {
                SkinLimb* limb = calloc(1, sizeof(SkinLimb));
                limb->jointPos = translation;
                limb->child = child;
                limb->sibling = sibling;
                limb->segmentType = skinType;
                if (skinType == 4) {
                    SkinAnimatedLimbData* data = calloc(1, sizeof(SkinAnimatedLimbData));
                    data->totalVtxCount = skinVertexCount;
                    data->limbModifCount = (u16)modificationCount;
                    data->limbModifications = modifications;
                    data->dlist = (Gfx*)skinDisplayList2;
                    limb->segment = data;
                } else {
                    limb->segment = skinDisplayList;
                }
                limbEntry->parsed = limb;
            } else if (limbType == 4) {
                SkelCurveLimb* limb = calloc(1, sizeof(SkelCurveLimb));
                limb->firstChildIdx = child;
                limb->nextLimbIdx = sibling;
                limb->dList[0] = (Gfx*)displayList;
                limb->dList[1] = (Gfx*)displayList2;
                limbEntry->parsed = limb;
            }
        }
        limbs[i] = limbEntry->parsed;
    }
    if (skeletonType == 1) {
        FlexSkeletonHeader* skeleton = calloc(1, sizeof(FlexSkeletonHeader));
        skeleton->sh.segment = limbs;
        skeleton->sh.limbCount = (u8)limbCount;
        skeleton->sh.skeletonType = skeletonType;
        skeleton->dListCount = (u8)displayListCount;
        entry->parsed = skeleton;
    } else if (skeletonType == 2) {
        SkelCurveLimbList* skeleton = calloc(1, sizeof(SkelCurveLimbList));
        skeleton->limbs = (SkelCurveLimb**)limbs;
        skeleton->limbCount = (u8)limbCount;
        entry->parsed = skeleton;
    } else {
        SkeletonHeader* skeleton = calloc(1, sizeof(SkeletonHeader));
        skeleton->segment = limbs;
        skeleton->limbCount = (u8)limbCount;
        skeleton->skeletonType = skeletonType;
        entry->parsed = skeleton;
    }
    return entry->parsed;
}

void ResourceMgr_ClearSkeletons(void) {
}

uint32_t ResourceMgr_GameHasMasterQuest(void) {
    return 0;
}

static Path* LoadPathByName(const char* path) {
    ResourceEntry* entry = LoadResourceByName(path);
    if (entry == NULL || ResourceType(entry) != 0x4F505448) { // OPTH
        return NULL;
    }
    if (entry->parsed != NULL) {
        return entry->parsed;
    }
    ResourceReader reader = { entry->bytes + 64, entry->bytes + entry->size };
    uint32_t pathCount = ReaderU32(&reader);
    Path* paths = calloc(pathCount, sizeof(Path));
    for (uint32_t i = 0; i < pathCount; i++) {
        uint32_t pointCount = ReaderU32(&reader);
        paths[i].count = (u8)pointCount;
        paths[i].points = calloc(pointCount, sizeof(Vec3s));
        for (uint32_t point = 0; point < pointCount; point++) {
            paths[i].points[point].x = (s16)ReaderU16(&reader);
            paths[i].points[point].y = (s16)ReaderU16(&reader);
            paths[i].points[point].z = (s16)ReaderU16(&reader);
        }
    }
    entry->parsed = paths;
    return paths;
}

static void* ParseMesh(ResourceReader* reader) {
    ReaderU8(reader);
    uint8_t type = ReaderU8(reader);
    uint8_t count = type == 1 ? 1 : ReaderU8(reader);
    if (type == 0) {
        MeshHeader0* header = calloc(1, sizeof(MeshHeader0));
        MeshEntry0* entries = calloc(count, sizeof(MeshEntry0));
        header->base.headerType = 0;
        header->numEntries = count;
        header->dListStart = (Gfx*)entries;
        header->dListEnd = (Gfx*)(entries + count);
        for (uint32_t i = 0; i < count; i++) {
            ReaderU8(reader);
            entries[i].opaqueDList = (Gfx*)ReaderOtrPath(reader);
            entries[i].translucentDList = (Gfx*)ReaderOtrPath(reader);
        }
        return header;
    }
    if (type == 2) {
        MeshHeader2* header = calloc(1, sizeof(MeshHeader2));
        MeshEntry2* entries = calloc(count, sizeof(MeshEntry2));
        header->base.headerType = 2;
        header->numEntries = count;
        header->dListStart = (Gfx*)entries;
        header->dListEnd = (Gfx*)(entries + count);
        for (uint32_t i = 0; i < count; i++) {
            ReaderU8(reader);
            entries[i].playerXMax = (s16)ReaderU16(reader);
            entries[i].playerZMax = (s16)ReaderU16(reader);
            entries[i].playerXMin = (s16)ReaderU16(reader);
            entries[i].playerZMin = (s16)ReaderU16(reader);
            entries[i].opaqueDList = (Gfx*)ReaderOtrPath(reader);
            entries[i].translucentDList = (Gfx*)ReaderOtrPath(reader);
        }
        return header;
    }
    if (type == 1) {
        uint8_t format = ReaderU8(reader);
        free(ReaderString(reader));
        free(ReaderString(reader));
        uint32_t imageCount = ReaderU32(reader);
        MeshHeader1Single* single = calloc(1, sizeof(MeshHeader1Single));
        BackgroundRecord* images = calloc(imageCount, sizeof(BackgroundRecord));
        single->base.base.headerType = 1;
        single->base.format = format;
        for (uint32_t i = 0; i < imageCount; i++) {
            BackgroundRecord* image = &images[i];
            image->unknown = ReaderU16(reader);
            image->bgID = (s8)ReaderU8(reader);
            image->imagePtr = ReaderOtrPath(reader);
            image->unknown2 = ReaderU32(reader);
            image->unknown3 = ReaderU32(reader);
            image->bgWidth = ReaderU16(reader);
            image->bgHeight = ReaderU16(reader);
            image->imageFmt = ReaderU8(reader);
            image->imageSize = ReaderU8(reader);
            image->imagePal = ReaderU16(reader);
            image->imageFlip = ReaderU16(reader);
        }
        ReaderU8(reader);
        MeshEntry0* dlist = calloc(1, sizeof(MeshEntry0));
        dlist->opaqueDList = (Gfx*)ReaderOtrPath(reader);
        dlist->translucentDList = (Gfx*)ReaderOtrPath(reader);
        single->base.entryRecord = (u32)(uintptr_t)dlist;
        if (format == 1 && imageCount > 0) {
            single->imagePtr = images[0].imagePtr;
            single->unknown = images[0].unknown2;
            single->unknown2 = images[0].unknown3;
            single->bgWidth = images[0].bgWidth;
            single->bgHeight = images[0].bgHeight;
            single->imageFormat = images[0].imageFmt;
            single->imageSize = images[0].imageSize;
            single->imagePal = images[0].imagePal;
            single->imageFlip = images[0].imageFlip;
        } else {
            MeshHeader1Multi* multi = (MeshHeader1Multi*)single;
            multi->bgCnt = (u8)imageCount;
            multi->bgRecordPtr = images;
        }
        return single;
    }
    return NULL;
}

static SceneCmd* ParseSceneResource(ResourceEntry* entry);

static SceneCmd* LoadSceneByName(const char* path) {
    ResourceEntry* entry = LoadResourceByName(path);
    return ParseSceneResource(entry);
}

static ActorEntry* ReadActors(ResourceReader* reader, uint32_t count) {
    ActorEntry* actors = calloc(count, sizeof(ActorEntry));
    for (uint32_t i = 0; i < count; i++) {
        actors[i].id = (s16)ReaderU16(reader);
        actors[i].pos.x = (s16)ReaderU16(reader);
        actors[i].pos.y = (s16)ReaderU16(reader);
        actors[i].pos.z = (s16)ReaderU16(reader);
        actors[i].rot.x = (s16)ReaderU16(reader);
        actors[i].rot.y = (s16)ReaderU16(reader);
        actors[i].rot.z = (s16)ReaderU16(reader);
        actors[i].params = (s16)ReaderU16(reader);
    }
    return actors;
}

static SceneCmd* ParseSceneResource(ResourceEntry* entry) {
    if (entry == NULL || ResourceType(entry) != 0x4F524F4D) { // OROM
        return NULL;
    }
    if (entry->parsed != NULL) {
        return entry->parsed;
    }
    ResourceReader reader = { entry->bytes + 64, entry->bytes + entry->size };
    uint32_t count = ReaderU32(&reader);
    SceneCmd* commands = calloc(count + 1, sizeof(SceneCmd));
    entry->parsed = commands;
    for (uint32_t index = 0; index < count; index++) {
        SceneCmd* command = &commands[index];
        uint32_t id = ReaderU32(&reader);
        command->base.code = (u8)id;
        switch (id) {
            case 0:
            case 1: {
                uint32_t actorCount = ReaderU32(&reader);
                command->actorList.num = (u8)actorCount;
                command->actorList.segment = ReadActors(&reader, actorCount);
                break;
            }
            case 2:
                ReaderU8(&reader);
                ReaderU32(&reader);
                break;
            case 3: {
                char* path = ReaderString(&reader);
                command->colHeader.segment = ResourceMgr_LoadColByName(path);
                free(path);
                break;
            }
            case 4: {
                uint32_t roomCount = ReaderU32(&reader);
                RomFile* rooms = calloc(roomCount, sizeof(RomFile));
                for (uint32_t i = 0; i < roomCount; i++) {
                    rooms[i].fileName = ReaderString(&reader);
                    rooms[i].vromStart = ReaderU32(&reader);
                    rooms[i].vromEnd = ReaderU32(&reader);
                }
                command->roomList.num = (u8)roomCount;
                command->roomList.segment = rooms;
                break;
            }
            case 5:
                command->windSettings.x = ReaderU8(&reader);
                command->windSettings.y = ReaderU8(&reader);
                command->windSettings.z = ReaderU8(&reader);
                command->windSettings.unk_07 = ReaderU8(&reader);
                break;
            case 6: {
                uint32_t entranceCount = ReaderU32(&reader);
                EntranceEntry* entrances = calloc(entranceCount, sizeof(EntranceEntry));
                for (uint32_t i = 0; i < entranceCount; i++) {
                    entrances[i].spawn = ReaderU8(&reader);
                    entrances[i].room = ReaderU8(&reader);
                }
                command->entranceList.segment = entrances;
                break;
            }
            case 7:
                command->specialFiles.cUpElfMsgNum = ReaderU8(&reader);
                command->specialFiles.keepObjectId = (s16)ReaderU16(&reader);
                break;
            case 8:
                command->roomBehavior.gpFlag1 = ReaderU8(&reader);
                command->roomBehavior.gpFlag2 = ReaderU32(&reader);
                break;
            case 9:
                break;
            case 10:
                command->mesh.segment = ParseMesh(&reader);
                break;
            case 11: {
                uint32_t objectCount = ReaderU32(&reader);
                s16* objects = calloc(objectCount, sizeof(s16));
                for (uint32_t i = 0; i < objectCount; i++) objects[i] = (s16)ReaderU16(&reader);
                command->objectList.num = (u8)objectCount;
                command->objectList.segment = objects;
                break;
            }
            case 12: {
                uint32_t lightCount = ReaderU32(&reader);
                LightInfo* lights = calloc(lightCount, sizeof(LightInfo));
                for (uint32_t i = 0; i < lightCount; i++) {
                    lights[i].type = ReaderU8(&reader);
                    lights[i].params.point.x = (s16)ReaderU16(&reader);
                    lights[i].params.point.y = (s16)ReaderU16(&reader);
                    lights[i].params.point.z = (s16)ReaderU16(&reader);
                    lights[i].params.point.color[0] = ReaderU8(&reader);
                    lights[i].params.point.color[1] = ReaderU8(&reader);
                    lights[i].params.point.color[2] = ReaderU8(&reader);
                    lights[i].params.point.drawGlow = ReaderU8(&reader);
                    lights[i].params.point.radius = (s16)ReaderU16(&reader);
                }
                command->lightList.num = (u8)lightCount;
                command->lightList.segment = lights;
                break;
            }
            case 13: {
                uint32_t pathCount = ReaderU32(&reader);
                for (uint32_t i = 0; i < pathCount; i++) {
                    char* path = ReaderString(&reader);
                    // Each resource contains the complete path list.
                    if (i == 0) command->pathList.segment = LoadPathByName(path);
                    free(path);
                }
                break;
            }
            case 14: {
                uint32_t transitionCount = ReaderU32(&reader);
                TransitionActorEntry* actors = calloc(transitionCount, sizeof(TransitionActorEntry));
                for (uint32_t i = 0; i < transitionCount; i++) {
                    actors[i].sides[0].room = (s8)ReaderU8(&reader);
                    actors[i].sides[0].effects = (s8)ReaderU8(&reader);
                    actors[i].sides[1].room = (s8)ReaderU8(&reader);
                    actors[i].sides[1].effects = (s8)ReaderU8(&reader);
                    actors[i].id = (s16)ReaderU16(&reader);
                    actors[i].pos.x = (s16)ReaderU16(&reader);
                    actors[i].pos.y = (s16)ReaderU16(&reader);
                    actors[i].pos.z = (s16)ReaderU16(&reader);
                    actors[i].rotY = (s16)ReaderU16(&reader);
                    actors[i].params = (s16)ReaderU16(&reader);
                }
                command->transiActorList.num = (u8)transitionCount;
                command->transiActorList.segment = actors;
                break;
            }
            case 15: {
                uint32_t lightCount = ReaderU32(&reader);
                EnvLightSettings* lights = calloc(lightCount, sizeof(EnvLightSettings));
                for (uint32_t i = 0; i < lightCount; i++) {
                    ReaderBytes(&reader, &lights[i], 18);
                    lights[i].fogNear = (s16)ReaderU16(&reader);
                    lights[i].fogFar = ReaderU16(&reader);
                }
                command->lightSettingList.num = (u8)lightCount;
                command->lightSettingList.segment = lights;
                break;
            }
            case 16:
                command->timeSettings.hour = ReaderU8(&reader);
                command->timeSettings.min = ReaderU8(&reader);
                command->timeSettings.unk_06 = ReaderU8(&reader);
                break;
            case 17:
                ReaderU8(&reader);
                command->skyboxSettings.skyboxId = ReaderU8(&reader);
                command->skyboxSettings.unk_05 = ReaderU8(&reader);
                command->skyboxSettings.unk_06 = ReaderU8(&reader);
                break;
            case 18:
                command->skyboxDisables.unk_04 = ReaderU8(&reader);
                command->skyboxDisables.unk_05 = ReaderU8(&reader);
                break;
            case 19: {
                uint32_t exitCount = ReaderU32(&reader);
                u16* exits = calloc(exitCount, sizeof(u16));
                for (uint32_t i = 0; i < exitCount; i++) exits[i] = ReaderU16(&reader);
                command->exitList.segment = exits;
                break;
            }
            case 20:
                break;
            case 21:
                command->soundSettings.specId = ReaderU8(&reader);
                command->soundSettings.natureAmbienceId = ReaderU8(&reader);
                command->soundSettings.seqId = ReaderU8(&reader);
                break;
            case 22:
                command->echoSettings.echo = ReaderU8(&reader);
                break;
            case 23: {
                char* path = ReaderString(&reader);
                command->cutsceneData.segment = ResourceMgr_LoadCSByName(path);
                free(path);
                break;
            }
            case 24: {
                uint32_t headerCount = ReaderU32(&reader);
                SceneCmd** headers = calloc(headerCount, sizeof(SceneCmd*));
                for (uint32_t i = 0; i < headerCount; i++) {
                    char* path = ReaderString(&reader);
                    if (path != NULL && path[0] != '\0') headers[i] = LoadSceneByName(path);
                    free(path);
                }
                command->altHeaders.segment = headers;
                break;
            }
            case 25:
                command->miscSettings.cameraMovement = ReaderU8(&reader);
                command->miscSettings.area = ReaderU32(&reader);
                break;
            default:
                reader.cursor = reader.end;
                break;
        }
    }
    commands[count].base.code = 20;
    return commands;
}

void OTRPlay_SpawnScene(PlayState* play, s32 sceneId, s32 spawn) {
#ifdef OOT_WASM_DIAGNOSTICS
    OotDiagnostics_Checkpoint(40, sceneId);
    OotDiagnostics_Checkpoint(41, gSaveContext.entranceIndex);
    OotDiagnostics_Checkpoint(42, gSaveContext.cutsceneIndex);
#endif
    SceneTableEntry* scene = &gSceneTable[sceneId];
    scene->unk_13 = 0;
    play->loadedScene = scene;
    play->sceneNum = sceneId;
    play->sceneConfig = scene->config;
    bool dungeon = (sceneId >= SCENE_DEKU_TREE && sceneId <= SCENE_ICE_CAVERN) ||
                   sceneId == SCENE_GERUDO_TRAINING_GROUND || sceneId == SCENE_INSIDE_GANONS_CASTLE;
    char path[256];
    snprintf(path, sizeof(path), "scenes/%s/%s/%s", dungeon ? "nonmq" : "shared", scene->sceneFile.fileName,
             scene->sceneFile.fileName);
#ifdef OOT_WASM_DIAGNOSTICS
    OotDiagnostics_Checkpoint(43, sceneId);
#endif
    play->sceneSegment = LoadSceneByName(path);
#ifdef OOT_WASM_DIAGNOSTICS
    OotDiagnostics_Checkpoint(44, play->sceneSegment != NULL);
#endif
    if (play->sceneSegment == NULL && sceneId != SCENE_DODONGOS_CAVERN) {
        OTRPlay_SpawnScene(play, SCENE_DODONGOS_CAVERN, 0);
        return;
    }
#ifdef OOT_WASM_DIAGNOSTICS
    OotDiagnostics_Checkpoint(45, spawn);
#endif
    Play_InitScene(play, spawn);
#ifdef OOT_WASM_DIAGNOSTICS
    OotDiagnostics_Checkpoint(46, spawn);
#endif
    func_80096FE8(play, &play->roomCtx);
#ifdef OOT_WASM_DIAGNOSTICS
    OotDiagnostics_Checkpoint(47, spawn);
#endif
}

s32 OTRRoom_RequestNewRoom(PlayState* play, RoomContext* roomCtx, s32 roomNum) {
    if (roomCtx->status != 0 || roomNum < 0 || roomNum >= play->numRooms) {
        return 0;
    }
    roomCtx->prevRoom = roomCtx->curRoom;
    roomCtx->curRoom.num = roomNum;
    roomCtx->curRoom.segment = NULL;
    roomCtx->roomToLoad = LoadSceneByName(play->roomList[roomNum].fileName);
    if (roomCtx->roomToLoad == NULL) {
        return 0;
    }
    roomCtx->status = 1;
    roomCtx->activeBufPage ^= 1;
    return 1;
}

s32 OTRfunc_800973FC(PlayState* play, RoomContext* roomCtx) {
    if (roomCtx->status == 1) {
        roomCtx->status = 0;
        roomCtx->curRoom.segment = roomCtx->roomToLoad;
        gSegments[3] = VIRTUAL_TO_PHYSICAL(roomCtx->roomToLoad);
        Scene_ExecuteCommands(play, roomCtx->roomToLoad);
        Player_SetBootData(play, GET_PLAYER(play));
        Actor_SpawnTransitionActors(play, &play->actorCtx);
    }
    return 1;
}

uint32_t OotResource_TextureInfo(const char* path, uint32_t* info, float* scale) {
    ResourceEntry* entry = LoadResourceByName(path);
    if (!entry || ResourceType(entry) != 0x4F544558) return 0;
    info[0] = ReadU32(entry->bytes + 64);
    info[1] = ReadU32(entry->bytes + 68);
    info[2] = ReadU32(entry->bytes + 72);
    uint32_t version = ReadU32(entry->bytes + 8);
    info[3] = version ? ReadU32(entry->bytes + 76) : 0;
    info[4] = ReadU32(entry->bytes + (version ? 88 : 76));
    scale[0] = scale[1] = 1.0f;
    if (version) { memcpy(scale, entry->bytes + 80, 8); }
    return 1;
}
