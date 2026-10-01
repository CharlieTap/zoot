#pragma once
#include "ship/resource/Resource.h"
#include <cstdint>
namespace Ship {
struct ArchiveManager { const char* HashToCString(uint64_t hash); };
struct ResourceManager {
    std::shared_ptr<IResource> LoadResourceProcess(const char* path);
    std::shared_ptr<IResource> LoadResourceProcess(uint64_t hash);
    void* GetResourceRawPointer(const char* path);
    void* GetResourceRawPointer(uint64_t hash);
    bool OtrSignatureCheck(const char* path);
    ArchiveManager* GetArchiveManager() { static ArchiveManager archives; return &archives; }
};
}
