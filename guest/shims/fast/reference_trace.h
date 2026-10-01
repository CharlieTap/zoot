#pragma once
// Reference-capture instrumentation is excluded from the performance guest.
#if OOT_RENDER_TRACE
#include "fast/interpreter.h"
#include <cstdio>
extern "C" void OotTrace_PrintScene();
namespace Fast {
inline uint32_t traceFrame = 0, traceRequested = 0, traceCommands = 0;
inline bool traceActive = false;
inline uint32_t traceDrawn = 0, traceClipped = 0, traceCulled = 0;
inline void ReferenceTraceBeginRun(Interpreter *) {
    traceActive = ++traceFrame == traceRequested;
    traceCommands = traceDrawn = traceClipped = traceCulled = 0;
    if (traceActive) {
        std::printf("trace-begin|%u\n", traceFrame);
        OotTrace_PrintScene();
    }
}
inline void ReferenceTraceEndRun(Interpreter *) {
    if (traceActive)
        std::printf("trace-end|%u|commands=%u|drawn=%u|clipped=%u|culled=%u\n",
                    traceFrame, traceCommands, traceDrawn, traceClipped, traceCulled);
    traceActive = false;
}
inline void ReferenceTraceCommand(Interpreter *, const F3DGfx *cmd, uint32_t ucode, size_t depth) {
    if (traceActive)
        std::printf("command|%u|%u|%zu|%08x|%08x\n", traceCommands++, ucode, depth,
                    (uint32_t)cmd->words.w0, (uint32_t)cmd->words.w1);
}
inline void ReferenceTraceVertex(Interpreter *, size_t index, const LoadedVertex &v) {
    if (traceActive)
        std::printf("vertex|%zu|%.9g|%.9g|%.9g|%.9g|%u\n", index, v.x, v.y, v.z, v.w, v.clip_rej);
}
inline void ReferenceTraceTriangle(Interpreter *, uint8_t a, uint8_t b, uint8_t c, const char *kind) {
    if (!traceActive) return;
    if (kind[0] == 'D') traceDrawn++;
    else if (kind[0] == 'T') traceClipped++;
    else traceCulled++;
    std::printf("triangle|%u|%u|%u|%s\n", a, b, c, kind);
}
}
#else
#define ReferenceTraceBeginRun(...) ((void)0)
#define ReferenceTraceEndRun(...) ((void)0)
#define ReferenceTraceCommand(...) ((void)0)
#define ReferenceTraceVertex(...) ((void)0)
#define ReferenceTraceTriangle(...) ((void)0)
#endif
#define ReferenceTraceResource(...) ((void)0)
#define ReferenceTraceState(...) ((void)0)
#define ReferenceTraceGeometry(...) ((void)0)
#define ReferenceTraceFramebuffers(...) ((void)0)
#define ReferenceTraceDraw(...) ((void)0)
#define ReferenceTraceOperation(...) ((void)0)
