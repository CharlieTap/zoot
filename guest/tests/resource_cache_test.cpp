#include "fast/interpreter.h"
#include "ship/resource/ResourceManager.h"
#include <cassert>
#include <cstdio>
#include <cstring>

namespace {
unsigned loads;
std::shared_ptr<Ship::IResource> resource;
class TestResource final : public Ship::Resource<unsigned char> {
  public:
    explicit TestResource(const char* path = "textures/test") : Resource(std::make_shared<Ship::ResourceInitData>()) {
        GetInitData()->Path = path;
    }
    unsigned char *GetPointer() override { return nullptr; }
    size_t GetPointerSize() override { return 0; }
};
}

std::shared_ptr<Ship::IResource> Ship::ResourceManager::LoadResourceProcess(const char *) {
    ++loads;
    return resource;
}

int main() {
    Fast::Interpreter interpreter;
    const char first[] = "__OTR__textures/test";
    const char second[] = "__OTR__textures/test";
    resource = std::make_shared<TestResource>();
    assert(!interpreter.ResolveResourceCached(nullptr));
    assert(loads == 0);
    assert(interpreter.ResolveResourceCached(first) == resource);
    assert(interpreter.ResolveResourceCached(first) == resource);
    assert(loads == 2);
    interpreter.SetResolvedResourceCacheEnabled(true);
    assert(interpreter.ResolveResourceCached(first) == resource);
    assert(interpreter.ResolveResourceCached(first) == resource);
    assert(loads == 3);
    assert(interpreter.ResolveResourceCached(second) == resource);
    assert(loads == 4);
    assert(interpreter.mResolvedResourceCache.size() == 2);
    interpreter.TextureCacheClear();
    assert(interpreter.mResolvedResourceCache.empty());
    assert(interpreter.ResolveResourceCached(first) == resource);
    assert(loads == 5);
    interpreter.SetResolvedResourceCacheEnabled(false);
    assert(interpreter.mResolvedResourceCache.empty());
    interpreter.SetResolvedResourceCacheEnabled(true);
    resource.reset();
    assert(!interpreter.ResolveResourceCached(first));
    assert(!interpreter.ResolveResourceCached(first));
    assert(loads == 7);
    resource = std::make_shared<TestResource>();
    assert(interpreter.ResolveResourceCached(first) == resource);
    assert(loads == 8);
    char reused[] = "__OTR__textures/test";
    assert(interpreter.ResolveResourceCached(reused) == resource);
    assert(loads == 9);
    std::strcpy(reused, "__OTR__textures/next");
    resource = std::make_shared<TestResource>("textures/next");
    assert(interpreter.ResolveResourceCached(reused) == resource);
    assert(loads == 10);
    assert(interpreter.ResolveResourceCached(reused) == resource);
    assert(loads == 10);
    std::strcpy(reused, "__OTR__textures/miss");
    resource.reset();
    assert(!interpreter.ResolveResourceCached(reused));
    assert(!interpreter.ResolveResourceCached(reused));
    assert(loads == 12);
    resource = std::make_shared<TestResource>("textures/miss");
    assert(interpreter.ResolveResourceCached(reused) == resource);
    assert(loads == 13);
    resource = std::make_shared<TestResource>("x");
    const char shortPath[] = "x";
    assert(interpreter.ResolveResourceCached(shortPath) == resource);
    assert(interpreter.ResolveResourceCached(shortPath) == resource);
    assert(loads == 14);
    std::puts("resource cache: stable/reused paths, clear, disable and misses passed");
}
