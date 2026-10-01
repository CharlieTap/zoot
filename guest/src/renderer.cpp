#include "renderer.h"
#include "fast/reference_trace.h"
#include "fast/backends/gfx_window_manager_api.h"
#include <tuple>
extern "C" {
#if OOT_PROFILE
__attribute__((import_module("oot_profile"), import_name("fast3d"))) void Host_ProfileFast3d(uint32_t);
#endif
#define GPU_IMPORT(name) __attribute__((import_module("oot_gpu"), import_name(name)))
GPU_IMPORT("shader") void Host_Shader(uint32_t, const char *, uint32_t, uint32_t, const uint32_t *, uint32_t);
GPU_IMPORT("texture") void Host_Texture(uint32_t, const uint8_t *, uint32_t, uint32_t);
GPU_IMPORT("delete_texture") void Host_DeleteTexture(uint32_t);
GPU_IMPORT("draw") void Host_Draw(const void *, const float *, uint32_t, uint32_t);
GPU_IMPORT("frame") void Host_Frame(uint32_t);
GPU_IMPORT("target") void Host_Target(uint32_t, uint32_t, uint32_t);
GPU_IMPORT("clear") void Host_Clear(uint32_t, uint32_t, uint32_t);
GPU_IMPORT("copy") void Host_Copy(uint32_t, uint32_t);
GPU_IMPORT("read") void Host_Read(uint32_t, uint32_t, uint32_t, uint16_t *);
GPU_IMPORT("depth") uint32_t Host_Depth(uint32_t, float, float);
}
namespace {
// Plain little-endian Wasm32 ABI; all pointers are borrowed for the host call only.
struct DrawState {
    uint32_t shader, flags, target;
    int32_t viewport[4] = {0, 0, 320, 240};
    int32_t scissor[4] = {0, 0, 320, 240};
    uint32_t textures[6]{};
    uint32_t samplers[6]{};
    float depth = 0, frame = 0, noiseScale = 1;
    uint32_t filtering = 0;
};
static_assert(sizeof(DrawState) == 108);
class Renderer final : public Fast::GfxRenderingAPI {
    struct SamplerState {
        uint32_t wrap = 0;
        bool linear = false;
    };
    DrawState state{};
    Fast::ShaderProgram *shader = nullptr;
    std::map<std::pair<uint64_t, uint64_t>, Fast::ShaderProgram> shaders;
    std::map<int, std::pair<uint32_t, uint32_t>> sizes;
    std::unordered_map<uint32_t, SamplerState> samplers;
    uint32_t nextTexture = 1, nextFramebuffer = 1, selected = 0;
    Fast::FilteringMode filter = Fast::FILTER_THREE_POINT;

  public:
    const char *GetName() override { return "WebGPU"; }
    int GetMaxTextureSize() override { return 2048; }
    Fast::GfxClipParameters GetClipParameters() override { return {true, false}; }
    void UnloadShader(Fast::ShaderProgram *) override {}
    void LoadShader(Fast::ShaderProgram *p) override {
        shader = p;
        state.shader = p->id;
    }
    void ClearShaderCache() override { shaders.clear(); }
    Fast::ShaderProgram *CreateAndLoadNewShader(uint64_t a, uint64_t b) override {
        auto &s = shaders[{a, b}];
        s.id0 = a;
        s.id1 = b;
        s.id = shaders.size();
        Fast::BuildShader(s);
        Host_Shader(s.id, s.source.c_str(), s.source.size(), s.stride, s.attributes.data(),
                    s.attributes.size());
        LoadShader(&s);
        return &s;
    }
    Fast::ShaderProgram *LookupShader(uint64_t a, uint64_t b) override {
        auto i = shaders.find({a, b});
        return i == shaders.end() ? nullptr : &i->second;
    }
    void ShaderGetInfo(Fast::ShaderProgram *p, uint8_t *n, bool t[2]) override {
        *n = p->features.numInputs;
        t[0] = p->features.usedTextures[0];
        t[1] = p->features.usedTextures[1];
    }
    uint32_t NewTexture() override { return nextTexture++; }
    void ApplySampler(int tile, const SamplerState &sampler) {
        state.samplers[tile] = sampler.wrap;
        if (tile < 2) {
            state.filtering = (state.filtering & ~(1u << tile)) |
                              ((sampler.linear && filter != Fast::FILTER_NONE) ? 1u << tile : 0);
        }
    }
    void SelectTexture(int tile, uint32_t id) override {
        selected = tile;
        state.textures[tile] = id;
        // Fast3D only reapplies sampler settings when the texture's settings change.
        ApplySampler(tile, samplers[id]);
    }
    void UploadTexture(const uint8_t *bytes, uint32_t w, uint32_t h) override {
        Host_Texture(state.textures[selected], bytes, w, h);
    }
    void SetSamplerParameters(int tile, bool linear, uint32_t s, uint32_t t) override {
        auto &sampler = samplers[state.textures[tile]];
        sampler = {s | (t << 2), linear};
        for (int bound = 0; bound < 6; ++bound)
            if (state.textures[bound] == state.textures[tile]) ApplySampler(bound, sampler);
    }
    void SetDepthTestAndMask(bool test, bool write) override {
        state.flags = (state.flags & ~3u) | test | (write << 1);
    }
    void SetZmodeDecal(bool decal) override { state.flags = (state.flags & ~4u) | (decal << 2); }
    void SetViewport(int x, int y, int w, int h) override {
        state.viewport[0] = x;
        state.viewport[1] = y;
        state.viewport[2] = w;
        state.viewport[3] = h;
    }
    void SetScissor(int x, int y, int w, int h) override {
        state.scissor[0] = x;
        state.scissor[1] = y;
        state.scissor[2] = w;
        state.scissor[3] = h;
    }
    void SetUseAlpha(bool alpha) override { state.flags = (state.flags & ~8u) | (alpha << 3); }
    void DrawTriangles(float *bytes, size_t len, size_t tris) override {
        Host_Draw(&state, bytes, len * 4, tris * 3);
    }
    void Init() override {}
    void OnResize() override {}
    void StartFrame() override {
        state.frame++;
        Host_Frame(0);
    }
    void EndFrame() override { Host_Frame(1); }
    void FinishRender() override {}
    int CreateFramebuffer() override { return nextFramebuffer++; }
    void UpdateFramebufferParameters(int id, uint32_t w, uint32_t h, uint32_t, bool, bool, bool,
                                     bool) override {
        if (sizes[id] == std::pair(w, h))
            return;
        sizes[id] = {w, h};
        Host_Target(id, w, h);
    }
    void StartDrawToFramebuffer(int id, float scale) override {
        state.target = id;
        if (scale != 0)
            state.noiseScale = 1.0f / scale;
    }
    void CopyFramebuffer(int dst, int src, int, int, int, int, int, int, int, int) override {
        Host_Copy(dst, src);
    }
    void ClearFramebuffer(bool color, bool depth) override { Host_Clear(state.target, color, depth); }
    void ReadFramebufferToCPU(int id, uint32_t w, uint32_t h, uint16_t *bytes) override {
        Host_Read(id, w, h, bytes);
    }
    void ResolveMSAAColorBuffer(int dst, int src) override { Host_Copy(dst, src); }
    std::unordered_map<std::pair<float, float>, uint16_t, Fast::hash_pair_ff>
    GetPixelDepth(int id, const std::set<std::pair<float, float>> &coords) override {
        std::unordered_map<std::pair<float, float>, uint16_t, Fast::hash_pair_ff> result;
        for (auto p : coords)
            result[p] = Host_Depth(id, p.first, p.second);
        return result;
    }
    void *GetFramebufferTextureId(int id) override { return reinterpret_cast<void *>(0x80000000u | id); }
    void SelectTextureFb(int id) override { SelectTexture(0, 0x80000000u | id); }
    void DeleteTexture(uint32_t id) override {
        samplers.erase(id);
        Host_DeleteTexture(id);
    }
    void SetTextureFilter(Fast::FilteringMode value) override { filter = value; }
    Fast::FilteringMode GetTextureFilter() override { return filter; }
    void SetSrgbMode() override {}
    ImTextureID GetTextureById(int id) override { return id; }
    void SetCurrentPrimDepth(float depth) override { state.depth = depth; }
};
std::shared_ptr<Fast::Interpreter> interpreter;
Renderer renderer;
Fast::GfxWindowBackend window;
} // namespace
extern "C" {
#if OOT_RENDER_TRACE
void oot_trace_frame(uint32_t frame) { Fast::traceRequested = frame; }
#endif
void OotRenderer_Init() {
    interpreter = std::make_shared<Fast::Interpreter>();
    Fast::GfxSetInstance(interpreter);
    interpreter->SetGfxDebugger(std::make_shared<Fast::GfxDebugger>());
    interpreter->Init(&window, &renderer, "OOT2", false, 320, 240, 0, 0);
    // Reused font/UI path buffers are checked against the cached resource name.
    interpreter->SetResolvedResourceCacheEnabled(true);
    interpreter->mGameWindowViewport = {0, 0, 320, 240};
}
void OotRenderer_Run(Gfx *commands) {
#if OOT_PROFILE
    Host_ProfileFast3d(0);
#endif
    interpreter->StartFrame();
    interpreter->Run(commands, {});
    interpreter->EndFrame();
#if OOT_PROFILE
    Host_ProfileFast3d(1);
#endif
}
int32_t OotRenderer_Framebuffer(uint32_t w, uint32_t h, uint32_t nw, uint32_t nh, uint32_t resize,
                                uint32_t aspect) {
    return interpreter->CreateFrameBuffer(w, h, nw, nh, resize, aspect);
}
void OotRenderer_BlendedTexture(const char *name, const uint8_t *mask, const uint8_t *replacement) {
    if (mask)
        interpreter->RegisterBlendedTexture(name, const_cast<uint8_t *>(mask),
                                            const_cast<uint8_t *>(replacement));
    else
        interpreter->UnregisterBlendedTexture(name);
}
}
