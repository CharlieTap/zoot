#include "oot_gpu.h"
#include <webgpu/wgpu.h>
#include <algorithm>
#include <array>
#include <atomic>
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <map>
#include <string>
#include <thread>
#include <unordered_map>
#include <vector>

// This is the iOS WebGPU adapter, not an N64 renderer. The guest supplies WGSL,
// vertices, textures and a fixed draw-state record, exactly as on Android.
namespace {
WGPUStringView text(const char *value) { return {value, strlen(value)}; }
struct Image {
    WGPUTexture texture = nullptr;
    WGPUTextureView view = nullptr;
    int width = 0, height = 0;
    void close() { wgpuTextureViewRelease(view); wgpuTextureRelease(texture); }
};
struct Target { Image color, depth; void close() { color.close(); depth.close(); } };
struct Shader { WGPUShaderModule module; int stride; std::vector<WGPUVertexAttribute> attributes; };
struct Presentation {
    WGPURenderPipeline pipeline;
    WGPUBindGroupLayout layout;
    WGPUSampler sampler;
    WGPUBindGroup binding = nullptr;
    WGPUTextureView source = nullptr;
    void close() {
        if (binding) wgpuBindGroupRelease(binding);
        wgpuSamplerRelease(sampler);
        wgpuBindGroupLayoutRelease(layout);
        wgpuRenderPipelineRelease(pipeline);
    }
};
const char *blitSource = R"(
@group(0) @binding(0) var image: texture_2d<f32>;
@group(0) @binding(1) var imageSampler: sampler;
struct Output { @builtin(position) position: vec4f, @location(0) uv: vec2f };
@vertex fn vertexMain(@builtin(vertex_index) index: u32) -> Output {
    let uv = vec2f(f32((index << 1u) & 2u), f32(index & 2u));
    return Output(vec4f(uv * vec2f(2.0, -2.0) + vec2f(-1.0, 1.0), 0.0, 1.0), uv);
}
@fragment fn fragmentMain(input: Output) -> @location(0) vec4f {
    return textureSampleLevel(image, imageSampler, input.uv, 0.0);
})";
}

struct OotGpu {
    WGPUInstance instance = nullptr;
    WGPUSurface surface = nullptr;
    WGPUAdapter adapter = nullptr;
    WGPUDevice device = nullptr;
    WGPUQueue queue = nullptr;
    WGPUTextureFormat format = WGPUTextureFormat_BGRA8Unorm;
    WGPUBuffer vertices = nullptr, uniforms = nullptr;
    WGPUBindGroupLayout layout = nullptr;
    WGPUPipelineLayout pipelineLayout = nullptr;
    WGPUCommandEncoder command = nullptr;
    WGPURenderPassEncoder pass = nullptr;
    int passTarget = -1;
    size_t vertexOffset = 0, uniformOffset = 0;
    std::vector<uint8_t> vertexUpload = std::vector<uint8_t>(16 * 1024 * 1024);
    std::vector<uint8_t> uniformUpload = std::vector<uint8_t>(4 * 1024 * 1024);
    std::unordered_map<int, Image> textures;
    std::unordered_map<int, Target> targets;
    std::unordered_map<int, Shader> shaders;
    std::unordered_map<uint64_t, WGPURenderPipeline> pipelines;
    std::unordered_map<int, WGPUSampler> samplers;
    std::map<std::array<int, 12>, WGPUBindGroup> bindings;
    std::map<std::string, Presentation> presentations;
    Presentation *presentation = nullptr;
    Presentation blit;

    WGPUShaderModule module(const char *source) {
        WGPUShaderSourceWGSL wgsl = WGPU_SHADER_SOURCE_WGSL_INIT;
        wgsl.code = text(source);
        WGPUShaderModuleDescriptor descriptor = WGPU_SHADER_MODULE_DESCRIPTOR_INIT;
        descriptor.nextInChain = &wgsl.chain;
        return wgpuDeviceCreateShaderModule(device, &descriptor);
    }

    WGPUBuffer buffer(uint64_t size, WGPUBufferUsage usage) {
        WGPUBufferDescriptor descriptor = WGPU_BUFFER_DESCRIPTOR_INIT;
        descriptor.size = size;
        descriptor.usage = usage;
        return wgpuDeviceCreateBuffer(device, &descriptor);
    }

    Image image(int width, int height, WGPUTextureFormat imageFormat = WGPUTextureFormat_RGBA8Unorm) {
        WGPUTextureDescriptor descriptor = WGPU_TEXTURE_DESCRIPTOR_INIT;
        descriptor.size = {uint32_t(width), uint32_t(height), 1};
        descriptor.format = imageFormat;
        descriptor.usage = WGPUTextureUsage_TextureBinding | WGPUTextureUsage_CopyDst |
            WGPUTextureUsage_CopySrc | WGPUTextureUsage_RenderAttachment;
        auto texture = wgpuDeviceCreateTexture(device, &descriptor);
        return {texture, wgpuTextureCreateView(texture, nullptr), width, height};
    }

    WGPUSampler sampler(int key) {
        auto found = samplers.find(key);
        if (found != samplers.end()) return found->second;
        auto mode = [](int bits) { return (bits & 2) ? WGPUAddressMode_ClampToEdge :
            (bits & 1) ? WGPUAddressMode_MirrorRepeat : WGPUAddressMode_Repeat; };
        WGPUSamplerDescriptor descriptor = WGPU_SAMPLER_DESCRIPTOR_INIT;
        descriptor.addressModeU = mode(key & 3);
        descriptor.addressModeV = mode((key >> 2) & 3);
        descriptor.magFilter = WGPUFilterMode_Nearest;
        descriptor.minFilter = WGPUFilterMode_Nearest;
        return samplers[key] = wgpuDeviceCreateSampler(device, &descriptor);
    }

    Presentation makePresentation(const char *source, WGPUTextureFormat output, bool linear) {
        auto shader = module(source);
        WGPUColorTargetState color = WGPU_COLOR_TARGET_STATE_INIT;
        color.format = output;
        WGPUFragmentState fragment = WGPU_FRAGMENT_STATE_INIT;
        fragment.module = shader; fragment.entryPoint = text("fragmentMain");
        fragment.targetCount = 1; fragment.targets = &color;
        WGPURenderPipelineDescriptor descriptor = WGPU_RENDER_PIPELINE_DESCRIPTOR_INIT;
        descriptor.vertex.module = shader; descriptor.vertex.entryPoint = text("vertexMain");
        descriptor.fragment = &fragment;
        auto pipeline = wgpuDeviceCreateRenderPipeline(device, &descriptor);
        wgpuShaderModuleRelease(shader);
        WGPUSamplerDescriptor sample = WGPU_SAMPLER_DESCRIPTOR_INIT;
        sample.addressModeU = sample.addressModeV = WGPUAddressMode_ClampToEdge;
        sample.minFilter = sample.magFilter = linear ? WGPUFilterMode_Linear : WGPUFilterMode_Nearest;
        return {pipeline, wgpuRenderPipelineGetBindGroupLayout(pipeline, 0), wgpuDeviceCreateSampler(device, &sample)};
    }

    void present(Presentation &filter, WGPUTextureView source, WGPUTextureView destination) {
        if (filter.source != source) {
            if (filter.binding) wgpuBindGroupRelease(filter.binding);
            WGPUBindGroupEntry entries[2] = {WGPU_BIND_GROUP_ENTRY_INIT, WGPU_BIND_GROUP_ENTRY_INIT};
            entries[0].binding = 0; entries[0].textureView = source;
            entries[1].binding = 1; entries[1].sampler = filter.sampler;
            WGPUBindGroupDescriptor descriptor = WGPU_BIND_GROUP_DESCRIPTOR_INIT;
            descriptor.layout = filter.layout; descriptor.entryCount = 2; descriptor.entries = entries;
            filter.binding = wgpuDeviceCreateBindGroup(device, &descriptor);
            filter.source = source;
        }
        WGPURenderPassColorAttachment color = WGPU_RENDER_PASS_COLOR_ATTACHMENT_INIT;
        color.view = destination; color.loadOp = WGPULoadOp_Clear; color.storeOp = WGPUStoreOp_Store;
        color.clearValue = {0, 0, 0, 1};
        WGPURenderPassDescriptor descriptor = WGPU_RENDER_PASS_DESCRIPTOR_INIT;
        descriptor.colorAttachmentCount = 1; descriptor.colorAttachments = &color;
        auto encoder = wgpuCommandEncoderBeginRenderPass(command, &descriptor);
        wgpuRenderPassEncoderSetPipeline(encoder, filter.pipeline);
        wgpuRenderPassEncoderSetBindGroup(encoder, 0, filter.binding, 0, nullptr);
        wgpuRenderPassEncoderDraw(encoder, 3, 1, 0, 0);
        wgpuRenderPassEncoderEnd(encoder);
        wgpuRenderPassEncoderRelease(encoder);
    }

    void clearBindings() {
        for (auto &entry : bindings) wgpuBindGroupRelease(entry.second);
        bindings.clear();
        // Texture views may be replaced when the guest recreates a target.
        for (auto &entry : presentations) entry.second.source = nullptr;
        blit.source = nullptr;
    }

    void endPass() {
        if (pass) { wgpuRenderPassEncoderEnd(pass); wgpuRenderPassEncoderRelease(pass); }
        pass = nullptr; passTarget = -1;
    }

    void beginPass(int id, bool clearColor = false, bool clearDepth = false) {
        auto &target = targets.at(id);
        WGPURenderPassColorAttachment color = WGPU_RENDER_PASS_COLOR_ATTACHMENT_INIT;
        color.view = target.color.view; color.loadOp = clearColor ? WGPULoadOp_Clear : WGPULoadOp_Load;
        color.storeOp = WGPUStoreOp_Store; color.clearValue = {0, 0, 0, 1};
        WGPURenderPassDepthStencilAttachment depth = WGPU_RENDER_PASS_DEPTH_STENCIL_ATTACHMENT_INIT;
        depth.view = target.depth.view; depth.depthLoadOp = clearDepth ? WGPULoadOp_Clear : WGPULoadOp_Load;
        depth.depthStoreOp = WGPUStoreOp_Store; depth.depthClearValue = 1;
        WGPURenderPassDescriptor descriptor = WGPU_RENDER_PASS_DESCRIPTOR_INIT;
        descriptor.colorAttachmentCount = 1; descriptor.colorAttachments = &color;
        descriptor.depthStencilAttachment = &depth;
        pass = wgpuCommandEncoderBeginRenderPass(command, &descriptor); passTarget = id;
    }

    void submit() {
        if (vertexOffset) {
            wgpuQueueWriteBuffer(queue, vertices, 0, vertexUpload.data(), vertexOffset);
            wgpuQueueWriteBuffer(queue, uniforms, 0, uniformUpload.data(), uniformOffset);
        }
        auto commands = wgpuCommandEncoderFinish(command, nullptr);
        wgpuQueueSubmit(queue, 1, &commands);
        wgpuCommandBufferRelease(commands);
        wgpuCommandEncoderRelease(command); command = nullptr;
    }

    std::vector<uint8_t> readback(const Image &image) {
        bool inFrame = command != nullptr;
        endPass();
        if (!command) command = wgpuDeviceCreateCommandEncoder(device, nullptr);
        uint32_t stride = (image.width * 4 + 255) & ~255;
        size_t size = stride * image.height;
        auto data = buffer(size, WGPUBufferUsage_CopyDst | WGPUBufferUsage_MapRead);
        WGPUTexelCopyTextureInfo source = WGPU_TEXEL_COPY_TEXTURE_INFO_INIT;
        source.texture = image.texture;
        WGPUTexelCopyBufferInfo destination = WGPU_TEXEL_COPY_BUFFER_INFO_INIT;
        destination.buffer = data; destination.layout.bytesPerRow = stride; destination.layout.rowsPerImage = image.height;
        WGPUExtent3D extent = {uint32_t(image.width), uint32_t(image.height), 1};
        wgpuCommandEncoderCopyTextureToBuffer(command, &source, &destination, &extent);
        submit();
        std::atomic<int> status{0};
        WGPUBufferMapCallbackInfo callback = WGPU_BUFFER_MAP_CALLBACK_INFO_INIT;
        callback.mode = WGPUCallbackMode_AllowSpontaneous; callback.userdata1 = &status;
        callback.callback = [](WGPUMapAsyncStatus result, WGPUStringView message, void *context, void *) {
            if (result != WGPUMapAsyncStatus_Success) fprintf(stderr, "Zoot readback: %.*s\n", int(message.length), message.data);
            static_cast<std::atomic<int> *>(context)->store(result == WGPUMapAsyncStatus_Success ? 1 : -1);
        };
        wgpuBufferMapAsync(data, WGPUMapMode_Read, 0, size, callback);
        while (!status.load()) wgpuDevicePoll(device, true, nullptr);
        std::vector<uint8_t> output(image.width * image.height * 4);
        if (status.load() == 1) {
            auto mapped = static_cast<const uint8_t *>(wgpuBufferGetConstMappedRange(data, 0, size));
            for (int y = 0; y < image.height; y++) memcpy(output.data() + y * image.width * 4, mapped + y * stride, image.width * 4);
            wgpuBufferUnmap(data);
        }
        wgpuBufferRelease(data);
        if (inFrame) command = wgpuDeviceCreateCommandEncoder(device, nullptr);
        return output;
    }
};

OotGpu *oot_gpu_create(void *layer, int width, int height) {
    auto gpu = new OotGpu();
    gpu->instance = wgpuCreateInstance(nullptr);
    WGPUSurfaceSourceMetalLayer metal = WGPU_SURFACE_SOURCE_METAL_LAYER_INIT;
    metal.layer = layer;
    WGPUSurfaceDescriptor surface = WGPU_SURFACE_DESCRIPTOR_INIT;
    surface.nextInChain = &metal.chain;
    gpu->surface = wgpuInstanceCreateSurface(gpu->instance, &surface);
    WGPURequestAdapterOptions options = WGPU_REQUEST_ADAPTER_OPTIONS_INIT;
    options.backendType = WGPUBackendType_Metal; options.compatibleSurface = gpu->surface;
    std::atomic<bool> ready{false};
    WGPURequestAdapterCallbackInfo adapterCallback = WGPU_REQUEST_ADAPTER_CALLBACK_INFO_INIT;
    adapterCallback.mode = WGPUCallbackMode_AllowSpontaneous; adapterCallback.userdata1 = gpu; adapterCallback.userdata2 = &ready;
    adapterCallback.callback = [](WGPURequestAdapterStatus, WGPUAdapter adapter, WGPUStringView message, void *context, void *done) {
        static_cast<OotGpu *>(context)->adapter = adapter;
        if (!adapter) fprintf(stderr, "Zoot adapter: %.*s\n", int(message.length), message.data);
        static_cast<std::atomic<bool> *>(done)->store(true);
    };
    wgpuInstanceRequestAdapter(gpu->instance, &options, adapterCallback);
    while (!ready.load()) { wgpuInstanceProcessEvents(gpu->instance); std::this_thread::yield(); }
    if (!gpu->adapter) { wgpuSurfaceRelease(gpu->surface); wgpuInstanceRelease(gpu->instance); delete gpu; return nullptr; }
    ready.store(false);
    WGPUDeviceDescriptor device = WGPU_DEVICE_DESCRIPTOR_INIT;
    device.uncapturedErrorCallbackInfo.callback = [](WGPUDevice const *, WGPUErrorType, WGPUStringView message, void *, void *) {
        fprintf(stderr, "Zoot GPU: %.*s\n", int(message.length), message.data);
    };
    WGPURequestDeviceCallbackInfo deviceCallback = WGPU_REQUEST_DEVICE_CALLBACK_INFO_INIT;
    deviceCallback.mode = WGPUCallbackMode_AllowSpontaneous; deviceCallback.userdata1 = gpu; deviceCallback.userdata2 = &ready;
    deviceCallback.callback = [](WGPURequestDeviceStatus, WGPUDevice device, WGPUStringView message, void *context, void *done) {
        static_cast<OotGpu *>(context)->device = device;
        if (!device) fprintf(stderr, "Zoot device: %.*s\n", int(message.length), message.data);
        static_cast<std::atomic<bool> *>(done)->store(true);
    };
    wgpuAdapterRequestDevice(gpu->adapter, &device, deviceCallback);
    while (!ready.load()) { wgpuInstanceProcessEvents(gpu->instance); std::this_thread::yield(); }
    if (!gpu->device) { wgpuAdapterRelease(gpu->adapter); wgpuSurfaceRelease(gpu->surface); wgpuInstanceRelease(gpu->instance); delete gpu; return nullptr; }
    gpu->queue = wgpuDeviceGetQueue(gpu->device);
    WGPUSurfaceConfiguration configuration = WGPU_SURFACE_CONFIGURATION_INIT;
    configuration.device = gpu->device; configuration.format = gpu->format;
    configuration.usage = WGPUTextureUsage_RenderAttachment; configuration.width = width; configuration.height = height;
    configuration.presentMode = WGPUPresentMode_Fifo;
    configuration.alphaMode = WGPUCompositeAlphaMode_Opaque;
    wgpuSurfaceConfigure(gpu->surface, &configuration);
    gpu->vertices = gpu->buffer(gpu->vertexUpload.size(), WGPUBufferUsage_Vertex | WGPUBufferUsage_CopyDst);
    gpu->uniforms = gpu->buffer(gpu->uniformUpload.size(), WGPUBufferUsage_Uniform | WGPUBufferUsage_CopyDst);
    WGPUBindGroupLayoutEntry entries[13];
    for (auto &entry : entries) entry = WGPU_BIND_GROUP_LAYOUT_ENTRY_INIT;
    entries[0].binding = 0; entries[0].visibility = WGPUShaderStage_Fragment;
    entries[0].buffer.type = WGPUBufferBindingType_Uniform;
    entries[0].buffer.hasDynamicOffset = true; entries[0].buffer.minBindingSize = 16;
    for (int i = 0; i < 6; i++) {
        auto &texture = entries[1 + i * 2]; texture.binding = 1 + i * 2; texture.visibility = WGPUShaderStage_Fragment;
        texture.texture.sampleType = WGPUTextureSampleType_Float; texture.texture.viewDimension = WGPUTextureViewDimension_2D;
        auto &sampler = entries[2 + i * 2]; sampler.binding = 2 + i * 2; sampler.visibility = WGPUShaderStage_Fragment;
        sampler.sampler.type = WGPUSamplerBindingType_Filtering;
    }
    WGPUBindGroupLayoutDescriptor layout = WGPU_BIND_GROUP_LAYOUT_DESCRIPTOR_INIT;
    layout.entryCount = 13; layout.entries = entries;
    gpu->layout = wgpuDeviceCreateBindGroupLayout(gpu->device, &layout);
    WGPUPipelineLayoutDescriptor pipeline = WGPU_PIPELINE_LAYOUT_DESCRIPTOR_INIT;
    pipeline.bindGroupLayoutCount = 1; pipeline.bindGroupLayouts = &gpu->layout;
    gpu->pipelineLayout = wgpuDeviceCreatePipelineLayout(gpu->device, &pipeline);
    gpu->blit = gpu->makePresentation(blitSource, WGPUTextureFormat_RGBA8Unorm, false);
    const uint32_t white = 0xffffffff;
    oot_gpu_texture(gpu, 0, &white, 1, 1);
    return gpu;
}

void oot_gpu_shader(OotGpu *gpu, int id, const char *source, int stride, const int *components, int count) {
    Shader shader{gpu->module(source), stride, {}};
    uint64_t offset = 0;
    const WGPUVertexFormat formats[] = {WGPUVertexFormat_Float32, WGPUVertexFormat_Float32x2, WGPUVertexFormat_Float32x3, WGPUVertexFormat_Float32x4};
    for (int i = 0; i < count; i++) {
        WGPUVertexAttribute attribute = WGPU_VERTEX_ATTRIBUTE_INIT;
        attribute.format = formats[components[i] - 1]; attribute.offset = offset; attribute.shaderLocation = i;
        shader.attributes.push_back(attribute); offset += components[i] * 4;
    }
    gpu->shaders.emplace(id, std::move(shader));
}

void oot_gpu_texture(OotGpu *gpu, int id, const void *bytes, int width, int height) {
    auto found = gpu->textures.find(id);
    if (found == gpu->textures.end() || found->second.width != width || found->second.height != height) {
        gpu->clearBindings();
        if (found != gpu->textures.end()) found->second.close();
        gpu->textures[id] = gpu->image(width, height);
    }
    WGPUTexelCopyTextureInfo texture = WGPU_TEXEL_COPY_TEXTURE_INFO_INIT;
    texture.texture = gpu->textures.at(id).texture;
    WGPUTexelCopyBufferLayout layout = WGPU_TEXEL_COPY_BUFFER_LAYOUT_INIT;
    layout.bytesPerRow = width * 4; layout.rowsPerImage = height;
    WGPUExtent3D extent = {uint32_t(width), uint32_t(height), 1};
    wgpuQueueWriteTexture(gpu->queue, &texture, bytes, width * height * 4, &layout, &extent);
}

void oot_gpu_delete_texture(OotGpu *gpu, int id) {
    gpu->clearBindings();
    auto found = gpu->textures.find(id);
    if (found != gpu->textures.end()) { found->second.close(); gpu->textures.erase(found); }
}

void oot_gpu_target(OotGpu *gpu, int id, int width, int height) {
    auto found = gpu->targets.find(id);
    if (found != gpu->targets.end() && found->second.color.width == width && found->second.color.height == height) return;
    gpu->endPass(); gpu->clearBindings();
    if (found != gpu->targets.end()) found->second.close();
    gpu->targets[id] = {gpu->image(width, height), gpu->image(width, height, WGPUTextureFormat_Depth32Float)};
}

void oot_gpu_begin(OotGpu *gpu) {
    gpu->vertexOffset = gpu->uniformOffset = 0;
    gpu->command = wgpuDeviceCreateCommandEncoder(gpu->device, nullptr);
}

void oot_gpu_clear(OotGpu *gpu, int id, int color, int depth) {
    gpu->endPass(); gpu->beginPass(id, color, depth); gpu->endPass();
}

void oot_gpu_draw(OotGpu *gpu, const void *rawState, const void *data, int size, int count) {
    int state[27]; memcpy(state, rawState, sizeof(state));
    int id = state[0], flags = state[1], targetId = state[2];
    auto &target = gpu->targets.at(targetId).color;
    int x = std::clamp(state[7], 0, target.width), y = std::clamp(target.height - state[8] - state[10], 0, target.height);
    int w = std::clamp(state[9], 0, target.width - x), h = std::clamp(state[10], 0, target.height - y);
    if (!w || !h || !count) return;
    if (size < 0 || gpu->vertexOffset + size > gpu->vertexUpload.size() ||
        gpu->uniformOffset + 256 > gpu->uniformUpload.size()) {
        fprintf(stderr, "Zoot GPU: frame upload capacity exceeded\n");
        std::abort();
    }
    memcpy(gpu->vertexUpload.data() + gpu->vertexOffset, data, size);
    memcpy(gpu->uniformUpload.data() + gpu->uniformOffset, state + 23, 16);
    uint64_t pipelineKey = (uint64_t(uint32_t(id)) << 32) | uint32_t(flags);
    auto pipeline = gpu->pipelines.find(pipelineKey);
    if (pipeline == gpu->pipelines.end()) {
        auto &shader = gpu->shaders.at(id);
        WGPUVertexBufferLayout vertices = WGPU_VERTEX_BUFFER_LAYOUT_INIT;
        vertices.arrayStride = shader.stride; vertices.attributeCount = shader.attributes.size(); vertices.attributes = shader.attributes.data();
        WGPUBlendState blend = WGPU_BLEND_STATE_INIT;
        blend.color.srcFactor = WGPUBlendFactor_SrcAlpha; blend.color.dstFactor = WGPUBlendFactor_OneMinusSrcAlpha;
        blend.alpha.srcFactor = WGPUBlendFactor_One; blend.alpha.dstFactor = WGPUBlendFactor_OneMinusSrcAlpha;
        WGPUColorTargetState color = WGPU_COLOR_TARGET_STATE_INIT;
        color.format = WGPUTextureFormat_RGBA8Unorm; color.blend = (flags & 8) ? &blend : nullptr;
        WGPUDepthStencilState depth = WGPU_DEPTH_STENCIL_STATE_INIT;
        depth.format = WGPUTextureFormat_Depth32Float;
        depth.depthWriteEnabled = (flags & 2) ? WGPUOptionalBool_True : WGPUOptionalBool_False;
        depth.depthCompare = !(flags & 1) ? WGPUCompareFunction_Always : (flags & 4) ? WGPUCompareFunction_LessEqual : WGPUCompareFunction_Less;
        depth.depthBias = (flags & 4) ? -2 : 0; depth.depthBiasSlopeScale = (flags & 4) ? -2.0f : 0.0f;
        WGPUFragmentState fragment = WGPU_FRAGMENT_STATE_INIT;
        fragment.module = shader.module; fragment.entryPoint = text("fragmentMain"); fragment.targetCount = 1; fragment.targets = &color;
        WGPURenderPipelineDescriptor descriptor = WGPU_RENDER_PIPELINE_DESCRIPTOR_INIT;
        descriptor.layout = gpu->pipelineLayout;
        descriptor.vertex.module = shader.module; descriptor.vertex.entryPoint = text("vertexMain");
        descriptor.vertex.bufferCount = 1; descriptor.vertex.buffers = &vertices;
        descriptor.depthStencil = &depth; descriptor.fragment = &fragment;
        pipeline = gpu->pipelines.emplace(pipelineKey, wgpuDeviceCreateRenderPipeline(gpu->device, &descriptor)).first;
    }
    std::array<int, 12> key; memcpy(key.data(), state + 11, sizeof(key));
    auto binding = gpu->bindings.find(key);
    if (binding == gpu->bindings.end()) {
        WGPUBindGroupEntry entries[13];
        for (auto &entry : entries) entry = WGPU_BIND_GROUP_ENTRY_INIT;
        entries[0].binding = 0; entries[0].buffer = gpu->uniforms; entries[0].size = 16;
        for (int tile = 0; tile < 6; tile++) {
            int textureId = key[tile];
            auto &image = textureId < 0 ? gpu->targets.at(textureId & 0x7fffffff).color :
                gpu->textures.at(gpu->textures.count(textureId) ? textureId : 0);
            entries[1 + tile * 2].binding = 1 + tile * 2; entries[1 + tile * 2].textureView = image.view;
            entries[2 + tile * 2].binding = 2 + tile * 2; entries[2 + tile * 2].sampler = gpu->sampler(key[6 + tile]);
        }
        WGPUBindGroupDescriptor descriptor = WGPU_BIND_GROUP_DESCRIPTOR_INIT;
        descriptor.layout = gpu->layout; descriptor.entryCount = 13; descriptor.entries = entries;
        binding = gpu->bindings.emplace(key, wgpuDeviceCreateBindGroup(gpu->device, &descriptor)).first;
    }
    if (gpu->passTarget != targetId) { gpu->endPass(); gpu->beginPass(targetId); }
    wgpuRenderPassEncoderSetPipeline(gpu->pass, pipeline->second);
    uint32_t offset = gpu->uniformOffset;
    wgpuRenderPassEncoderSetBindGroup(gpu->pass, 0, binding->second, 1, &offset);
    wgpuRenderPassEncoderSetVertexBuffer(gpu->pass, 0, gpu->vertices, gpu->vertexOffset, size);
    wgpuRenderPassEncoderSetViewport(gpu->pass, state[3], target.height - state[4] - state[6], state[5], state[6], 0, 1);
    wgpuRenderPassEncoderSetScissorRect(gpu->pass, x, y, w, h);
    wgpuRenderPassEncoderDraw(gpu->pass, count, 1, 0, 0);
    gpu->vertexOffset += size; gpu->uniformOffset += 256;
}

void oot_gpu_copy(OotGpu *gpu, int destination, int source) {
    gpu->endPass(); gpu->present(gpu->blit, gpu->targets.at(source).color.view, gpu->targets.at(destination).color.view);
}

void oot_gpu_upscaler(OotGpu *gpu, const char *source, int linear) {
    auto found = gpu->presentations.find(source);
    if (found == gpu->presentations.end()) found = gpu->presentations.emplace(source, gpu->makePresentation(source, gpu->format, linear)).first;
    gpu->presentation = &found->second;
}

void oot_gpu_end(OotGpu *gpu) {
    gpu->endPass();
    WGPUSurfaceTexture current = WGPU_SURFACE_TEXTURE_INIT;
    wgpuSurfaceGetCurrentTexture(gpu->surface, &current);
    if (current.texture) {
        auto view = wgpuTextureCreateView(current.texture, nullptr);
        gpu->present(*gpu->presentation, gpu->targets.at(0).color.view, view);
        wgpuTextureViewRelease(view);
        gpu->submit();
        wgpuSurfacePresent(gpu->surface);
        wgpuTextureRelease(current.texture);
    } else gpu->submit();
    wgpuInstanceProcessEvents(gpu->instance);
}

void oot_gpu_read(OotGpu *gpu, int id, int width, int height, void *destination) {
    auto &image = gpu->targets.at(id).color;
    auto data = gpu->readback(image);
    auto output = static_cast<uint16_t *>(destination);
    for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) {
        auto pixel = data.data() + ((y * image.height / height) * image.width + x * image.width / width) * 4;
        output[y * width + x] = ((pixel[0] >> 3) << 11) | ((pixel[1] >> 3) << 6) | ((pixel[2] >> 3) << 1) | (pixel[3] >> 7);
    }
}

int oot_gpu_depth(OotGpu *gpu, int id, float x, float y) {
    if (!gpu->targets.count(id)) return 65532;
    auto &image = gpu->targets.at(id).depth;
    auto data = gpu->readback(image);
    int ix = std::clamp(int(x), 0, image.width - 1), iy = std::clamp(image.height - 1 - int(y), 0, image.height - 1);
    float value; memcpy(&value, data.data() + (iy * image.width + ix) * 4, 4);
    return (int(std::clamp(value, 0.0f, 1.0f) * 0xffffff) >> 10) << 2;
}

void oot_gpu_destroy(OotGpu *gpu) {
    gpu->endPass();
    if (gpu->command) wgpuCommandEncoderRelease(gpu->command);
    gpu->clearBindings();
    for (auto &entry : gpu->presentations) entry.second.close();
    gpu->blit.close();
    for (auto &entry : gpu->targets) entry.second.close();
    for (auto &entry : gpu->textures) entry.second.close();
    for (auto &entry : gpu->pipelines) wgpuRenderPipelineRelease(entry.second);
    for (auto &entry : gpu->shaders) wgpuShaderModuleRelease(entry.second.module);
    for (auto &entry : gpu->samplers) wgpuSamplerRelease(entry.second);
    wgpuBufferRelease(gpu->vertices); wgpuBufferRelease(gpu->uniforms);
    wgpuBindGroupLayoutRelease(gpu->layout); wgpuPipelineLayoutRelease(gpu->pipelineLayout);
    wgpuQueueRelease(gpu->queue); wgpuSurfaceUnconfigure(gpu->surface); wgpuSurfaceRelease(gpu->surface);
    wgpuDeviceRelease(gpu->device); wgpuAdapterRelease(gpu->adapter); wgpuInstanceRelease(gpu->instance);
    delete gpu;
}
