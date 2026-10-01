#pragma once
#include "fast/interpreter.h"
#include <string>
#include <vector>
namespace Fast {
void GfxSetInstance(std::shared_ptr<Interpreter> interpreter);
struct ShaderProgram {
    uint64_t id0, id1;
    uint32_t id, stride;
    CCFeatures features{};
    std::vector<uint32_t> attributes;
    std::string source;
};
void BuildShader(ShaderProgram &shader);
} // namespace Fast
