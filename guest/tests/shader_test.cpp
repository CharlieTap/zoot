#include "renderer.h"
#include <cassert>

// Feed combiner features directly to test our generator, not the upstream decoder.
static CCFeatures features;
void gfx_cc_get_features(uint64_t, uint64_t, CCFeatures *out) { *out = features; }

static std::string Generate() {
    Fast::ShaderProgram shader{};
    Fast::BuildShader(shader);
    return shader.source;
}

int main() {
    auto source = Generate();
    assert(source.find("@builtin(frag_depth)") == std::string::npos);
    assert(source.find("return Fragment(combined);") != std::string::npos);

    features.opt_prim_depth = true;
    source = Generate();
    assert(source.find("@builtin(frag_depth)") != std::string::npos);
    assert(source.find("return Fragment(combined,params.depth);") != std::string::npos);

    features = {};
    features.c[0][0][3] = SHADER_TEXEL0;
    features.used_masks[0] = features.used_blend[0] = true;
    source = Generate();
    assert(source.find("sample3(tex2,samp2,uv0,(params.filtering & 1u)!=0u).a") != std::string::npos);
    assert(source.find("sample3(tex4,samp4,uv0,(params.filtering & 1u)!=0u)") != std::string::npos);

    features = {};
    features.opt_alpha = true;
    features.opt_noise = true;
    features.c[0][0][3] = features.c[0][1][3] = SHADER_NOISE;
    source = Generate();
    assert(source.find("vec3f((noise+1.0)/2.0)") != std::string::npos);
    assert(source.find("+((noise+1.0)/2.0)") != std::string::npos);
    // Alpha dithering still uses the unscaled [0, 1) random value.
    assert(source.find("clamp(noise+combined.a,0.0,1.0)") != std::string::npos);
}
