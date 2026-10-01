#include "fast/resource/type/Texture.h"
#include "ship/resource/ResourceManager.h"
#include "ship/utils/StrHash64.h"
#include <cstring>
#include <unordered_map>
extern "C" {
uint64_t OotResource_Hash(const char *path) { return CRC64(path); }
void *ResourceGetDataByName(const char *);
void *ResourceGetDataByCrc(uint64_t);
uint32_t OotResource_TextureInfo(const char *, uint32_t *, float *);
__attribute__((import_module("oot_resources"), import_name("resource_name"))) int
Host_ResourceName(uint64_t hash, char *destination, uint32_t capacity);
}
namespace Fast {
Texture::Texture() : Resource(std::make_shared<Ship::ResourceInitData>()) {}
uint8_t *Texture::GetPointer() { return ImageData; }
size_t Texture::GetPointerSize() { return ImageDataSize; }
// Pixel storage belongs to the guest resource cache, not this metadata view.
Texture::~Texture() = default;
} // namespace Fast
namespace Ship {
struct ResourceIdHash {
    size_t operator()(uint64_t hash) const { return static_cast<uint32_t>(hash ^ (hash >> 32)); }
};

const char *ArchiveManager::HashToCString(uint64_t hash) {
    static std::unordered_map<uint64_t, std::string, ResourceIdHash> names;
    auto found = names.find(hash);
    if (found != names.end())
        return found->second.c_str();
    int length = Host_ResourceName(hash, nullptr, 0);
    if (length < 0)
        return nullptr;
    std::string name(length, '\0');
    Host_ResourceName(hash, name.data(), length + 1);
    return names.emplace(hash, std::move(name)).first->second.c_str();
}
static std::shared_ptr<IResource> LoadTexture(uint64_t hash, const char *path) {
    static std::unordered_map<uint64_t, std::shared_ptr<IResource>, ResourceIdHash> textures;
    auto found = textures.find(hash);
    if (found != textures.end())
        return found->second;
    if (!path) {
        ArchiveManager archives;
        path = archives.HashToCString(hash);
        if (!path)
            return nullptr;
    }
    uint32_t info[5];
    float scale[2];
    if (!OotResource_TextureInfo(path, info, scale))
        return nullptr;
    auto texture = std::make_shared<Fast::Texture>();
    texture->GetInitData()->Path = path;
    texture->Type = static_cast<Fast::TextureType>(info[0]);
    texture->Width = info[1];
    texture->Height = info[2];
    texture->Flags = info[3];
    texture->ImageDataSize = info[4];
    texture->HByteScale = scale[0];
    texture->VPixelScale = scale[1];
    texture->ImageData = static_cast<uint8_t *>(ResourceGetDataByName(path));
    textures.emplace(hash, texture);
    return texture;
}
std::shared_ptr<IResource> ResourceManager::LoadResourceProcess(const char *path) {
    if (!path)
        return nullptr;
    if (OtrSignatureCheck(path))
        path += 7;
    return LoadTexture(CRC64(path), path);
}
std::shared_ptr<IResource> ResourceManager::LoadResourceProcess(uint64_t hash) {
    return LoadTexture(hash, nullptr);
}
void *ResourceManager::GetResourceRawPointer(const char *path) { return ResourceGetDataByName(path); }
void *ResourceManager::GetResourceRawPointer(uint64_t hash) { return ResourceGetDataByCrc(hash); }
bool ResourceManager::OtrSignatureCheck(const char *path) {
    return path && std::memcmp(path, "__OTR__", 7) == 0;
}
} // namespace Ship
