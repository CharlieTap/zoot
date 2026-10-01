// Exercise the adapter directly; no game assets or GPU are needed.
#include "../src/renderer.cpp"
#include <cassert>
#include <cstring>

static DrawState captured;
extern "C" {
void Host_Shader(uint32_t, const char *, uint32_t, uint32_t, const uint32_t *, uint32_t) {}
void Host_Texture(uint32_t, const uint8_t *, uint32_t, uint32_t) {}
void Host_DeleteTexture(uint32_t) {}
void Host_Draw(const void *state, const float *, uint32_t, uint32_t) { std::memcpy(&captured, state, sizeof(captured)); }
void Host_Frame(uint32_t) {}
void Host_Target(uint32_t, uint32_t, uint32_t) {}
void Host_Clear(uint32_t, uint32_t, uint32_t) {}
void Host_Copy(uint32_t, uint32_t) {}
void Host_Read(uint32_t, uint32_t, uint32_t, uint16_t *) {}
uint32_t Host_Depth(uint32_t, float, float) { return 0; }
}
namespace Fast { void BuildShader(ShaderProgram &) {} }

int main() {
    Renderer test;
    const auto world = test.NewTexture(), hud = test.NewTexture();
    test.SelectTexture(0, world);
    test.SetSamplerParameters(0, true, 0, 0);
    test.SelectTexture(0, hud);
    test.SetSamplerParameters(0, false, 2, 2);
    // A cache hit does not call SetSamplerParameters again.
    test.SelectTexture(0, world);
    test.DrawTriangles(nullptr, 0, 0);
    assert(captured.samplers[0] == 0 && (captured.filtering & 1));
    test.SelectTexture(1, hud);
    test.DrawTriangles(nullptr, 0, 0);
    assert(captured.samplers[1] == 10 && !(captured.filtering & 2));
    // Sampler settings belong to the texture, including when both slots bind it.
    test.SelectTexture(1, world);
    test.SetSamplerParameters(1, false, 1, 2);
    test.DrawTriangles(nullptr, 0, 0);
    assert(captured.samplers[0] == 9 && captured.samplers[1] == 9);
    assert(captured.filtering == 0);
    test.DeleteTexture(world);
    test.SelectTexture(0, world);
    test.DrawTriangles(nullptr, 0, 0);
    assert(captured.samplers[0] == 0);

    test.StartDrawToFramebuffer(1, 2);
    test.DrawTriangles(nullptr, 0, 0);
    assert(captured.noiseScale == 0.5f);
    test.StartDrawToFramebuffer(2, 0);
    test.DrawTriangles(nullptr, 0, 0);
    assert(captured.noiseScale == 0.5f);
}
