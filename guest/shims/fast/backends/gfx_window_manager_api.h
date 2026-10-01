#pragma once
#include <cstdint>
namespace Fast {
// The application owns the window and pacing. Fast3D only needs its dimensions.
struct GfxWindowBackend {
    uint32_t width = 320, height = 240;
    int fps = 20;
    void Init(const char*, const char*, bool, uint32_t w, uint32_t h, uint32_t, uint32_t) { width=w; height=h; }
    void GetDimensions(uint32_t* w, uint32_t* h, int32_t* x, int32_t* y) { *w=width; *h=height; *x=*y=0; }
    void Destroy() {}
    void HandleEvents() {}
    bool IsFrameReady() { return true; }
    void SwapBuffersBegin() {}
    void SwapBuffersEnd() {}
    int GetTargetFps() { return fps; }
    void SetTargetFps(int value) { fps=value; }
    void SetMaxFrameLatency(int) {}
};
}
