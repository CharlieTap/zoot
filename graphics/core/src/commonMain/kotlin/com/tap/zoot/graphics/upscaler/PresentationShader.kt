package com.tap.zoot.graphics.upscaler

fun Upscaler.presentationShader(): String =
    """
    @group(0) @binding(0) var image: texture_2d<f32>;
    @group(0) @binding(1) var imageSampler: sampler;
    struct VertexOutput { @builtin(position) position: vec4f, @location(0) uv: vec2f };
    @vertex fn vertexMain(@builtin(vertex_index) index: u32) -> VertexOutput {
        let uv = vec2f(f32((index << 1u) & 2u), f32(index & 2u));
        return VertexOutput(vec4f(uv * vec2f(2.0, -2.0) + vec2f(-1.0, 1.0), 0.0, 1.0), uv);
    }
    $shaderSource
    @fragment fn fragmentMain(input: VertexOutput) -> @location(0) vec4f {
        return upscale(input.uv);
    }
    """.trimIndent()
