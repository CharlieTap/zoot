#pragma once
#include <memory>
#include <string>
#include <vector>
namespace Ship {
struct ResourceInitData { std::string Path; };
class IResource {
    std::shared_ptr<ResourceInitData> init;
public:
    inline static const std::string gAltAssetPrefix = "alt/";
    explicit IResource(std::shared_ptr<ResourceInitData> data) : init(std::move(data)) {}
    virtual ~IResource() = default;
    virtual void* GetRawPointer() = 0;
    virtual size_t GetPointerSize() = 0;
    std::shared_ptr<ResourceInitData> GetInitData() { return init; }
};
template<class T> class Resource : public IResource {
public:
    using IResource::IResource;
    virtual T* GetPointer() = 0;
    void* GetRawPointer() override { return GetPointer(); }
};
}
