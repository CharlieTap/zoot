#include "../src/shipwright_save.c"

SaveContext gSaveContext;

__attribute__((export_name("buffer")))
void* SaveBuffer(void) { return &gSaveContext; }

__attribute__((export_name("size")))
uint32_t SaveSize(void) { return sizeof(gSaveContext); }

__attribute__((export_name("read_save")))
int32_t TestRead(uint32_t slot, uint32_t capacity) { return ReadSave(slot, &gSaveContext, capacity); }

__attribute__((export_name("write_save")))
int32_t TestWrite(uint32_t slot, uint32_t length) { return WriteSave(slot, &gSaveContext, length); }

__attribute__((export_name("valid")))
int TestValid(uint32_t slot) { return Save_GetSaveMetaInfo(slot)->valid; }
