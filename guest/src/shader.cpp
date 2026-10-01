#include "renderer.h"
#include <array>
namespace Fast {
void BuildShader(ShaderProgram &shader) {
    auto &f = shader.features;
    gfx_cc_get_features(shader.id0, shader.id1, &f);
    // Match GenerateCC's cycle-specific texture slots. In cycle two the slots swap.
    f.usedTextures[0] = f.usedTextures[1] = false;
    for (int cycle = 0; cycle < (f.opt_2cyc ? 2 : 1); cycle++) {
        for (auto &channel : f.c[cycle])
            for (int input : channel) {
                if (input == SHADER_TEXEL0 || input == SHADER_TEXEL0A)
                    f.usedTextures[cycle] = true;
                if (input == SHADER_TEXEL1 || input == SHADER_TEXEL1A)
                    f.usedTextures[1 - cycle] = true;
            }
    }
    std::string in = "struct Vin { @location(0) position: vec4f,\n";
    std::string out = "struct Vout { @builtin(position) position: vec4f,\n";
    std::string assign = "o.position = v.position;\n";
    uint32_t location = 1;
    shader.attributes = {4};
    shader.stride = 16;
    auto attribute = [&](std::string name, int size) {
        std::string type = size == 1 ? "f32" : "vec" + std::to_string(size) + "f";
        std::string field = "@location(" + std::to_string(location++) + ") " + name + ": " + type + ",\n";
        in += field;
        out += field;
        assign += "o." + name + "=v." + name + ";\n";
        shader.attributes.push_back(size);
        shader.stride += size * 4;
    };
    for (int t = 0; t < 2; t++)
        if (f.usedTextures[t]) {
            auto n = std::to_string(t);
            attribute("uv" + n, 2);
            if (f.clamp[t][0])
                attribute("clampS" + n, 1);
            if (f.clamp[t][1])
                attribute("clampT" + n, 1);
        }
    if (f.opt_fog)
        attribute("fog", 4);
    if (f.opt_grayscale)
        attribute("gray", 4);
    for (int i = 1; i <= f.numInputs; i++)
        attribute("input" + std::to_string(i), f.opt_alpha ? 4 : 3);
    std::string src = in + "};\n" + out + "};\n";
    src += "@vertex fn vertexMain(v: Vin) -> Vout { var o: Vout; " + assign + "return o; }\n";
    src += "struct Params { depth: f32, frame: f32, noiseScale: f32, filtering: u32 };\n";
    src += "@group(0) @binding(0) var<uniform> params: Params;\n";
    for (int t = 0; t < 6; t++) {
        src += "@group(0) @binding(" + std::to_string(1 + t * 2) + ") var tex" + std::to_string(t) +
               ": texture_2d<f32>;\n";
        src += "@group(0) @binding(" + std::to_string(2 + t * 2) + ") var samp" + std::to_string(t) +
               ": sampler;\n";
    }
    src += R"(
fn random(p: vec3f) -> f32 { return fract(sin(dot(sin(p),vec3f(12.9898,78.233,37.719)))*143758.5453); }
fn wrap3(v: vec3f, low: f32, high: f32) -> vec3f { return v-floor((v-low)/(high-low))*(high-low); }
fn wrap1(v: f32, low: f32, high: f32) -> f32 { return v-floor((v-low)/(high-low))*(high-low); }
fn sample3(t: texture_2d<f32>, s: sampler, uv: vec2f, filtered: bool) -> vec4f {
    if (!filtered) { return textureSampleLevel(t,s,uv,0.0); }
    let size=vec2f(textureDimensions(t)); var offset=fract(uv*size-0.5);
    offset -= step(1.0,offset.x+offset.y);
    let a=textureSampleLevel(t,s,uv-offset/size,0.0);
    let b=textureSampleLevel(t,s,uv-vec2f(offset.x-sign(offset.x),offset.y)/size,0.0);
    let c=textureSampleLevel(t,s,uv-vec2f(offset.x,offset.y-sign(offset.y))/size,0.0);
    return a+abs(offset.x)*(b-a)+abs(offset.y)*(c-a);
}
)";
    src += "struct Fragment { @location(0) color: vec4f";
    if (f.opt_prim_depth)
        src += ", @builtin(frag_depth) depth: f32";
    src += " };\n";
    src += R"(
@fragment fn fragmentMain(v: Vout) -> Fragment {
    let noise=random(vec3f(floor(v.position.xy*params.noiseScale),params.frame));
    var texel0=vec4f(1.0); var texel1=vec4f(1.0); var combined=vec4f(0.0);
)";
    for (int t = 0; t < 2; t++)
        if (f.usedTextures[t]) {
            auto n = std::to_string(t);
            src += "var uv" + n + "=v.uv" + n + ";\n";
            if (f.clamp[t][0])
                src += "uv" + n + ".x=clamp(uv" + n + ".x,0.5/f32(textureDimensions(tex" + n +
                       ").x),v.clampS" + n + ");\n";
            if (f.clamp[t][1])
                src += "uv" + n + ".y=clamp(uv" + n + ".y,0.5/f32(textureDimensions(tex" + n +
                       ").y),v.clampT" + n + ");\n";
            src += "texel" + n + "=sample3(tex" + n + ",samp" + n + ",uv" + n + ",(params.filtering & " +
                   std::to_string(1 << t) + "u)!=0u);\n";
            if (f.used_masks[t]) {
                auto mask = std::to_string(t + 2), blend = std::to_string(t + 4);
                auto filtered = "(params.filtering & " + std::to_string(1 << t) + "u)!=0u";
                auto replacement = f.used_blend[t]
                                       ? "sample3(tex" + blend + ",samp" + blend + ",uv" + n + "," + filtered + ")"
                                       : "vec4f(0.0)";
                src += "texel" + n + "=mix(texel" + n + "," + replacement + ",sample3(tex" + mask +
                       ",samp" + mask + ",uv" + n + "," + filtered + ").a);\n";
            }
        }
    auto value = [&](int v, bool alpha, int cycle) -> std::string {
        std::string s;
        if (v >= SHADER_INPUT_1 && v <= SHADER_INPUT_7) {
            s = "v.input" + std::to_string(v);
            return s + (alpha ? ".a" : ".rgb");
        }
        switch (v) {
        case SHADER_TEXEL0:
            s = cycle ? "texel1" : "texel0";
            break;
        case SHADER_TEXEL1:
            s = cycle ? "texel0" : "texel1";
            break;
        case SHADER_TEXEL0A:
            return alpha ? (cycle ? "texel1.a" : "texel0.a")
                         : (cycle ? "vec3f(texel1.a)" : "vec3f(texel0.a)");
        case SHADER_TEXEL1A:
            return alpha ? (cycle ? "texel0.a" : "texel1.a")
                         : (cycle ? "vec3f(texel0.a)" : "vec3f(texel1.a)");
        case SHADER_COMBINED:
            s = "combined";
            break;
        case SHADER_1:
            return alpha ? "1.0" : "vec3f(1.0)";
        case SHADER_NOISE:
            return alpha ? "((noise+1.0)/2.0)" : "vec3f((noise+1.0)/2.0)";
        default:
            return alpha ? "0.0" : "vec3f(0.0)";
        }
        return s + (alpha ? ".a" : ".rgb");
    };
    for (int c = 0; c < (f.opt_2cyc ? 2 : 1); c++) {
        if (c) {
            src += "combined=vec4f(wrap3(combined.rgb," +
                   std::string(f.c[c][0][2] == SHADER_COMBINED ? "-1.01,1.01" : "-0.51,1.51") +
                   "),wrap1(combined.a," + (f.c[c][1][2] == SHADER_COMBINED ? "-1.01,1.01" : "-0.51,1.51") +
                   "));\n";
        }
        auto formula = [&](bool alpha) {
            int *args = f.c[c][alpha ? 1 : 0];
            return "(" + value(args[0], alpha, c) + "-" + value(args[1], alpha, c) + ")*" +
                   value(args[2], alpha, c) + "+" + value(args[3], alpha, c);
        };
        src += "combined=vec4f(" + formula(false) + "," + (f.opt_alpha ? formula(true) : "1.0") + ");\n";
    }
    src += "combined=clamp(vec4f(wrap3(combined.rgb,-0.51,1.51),wrap1(combined.a,-0.51,1.51)),vec4f(0.0),"
           "vec4f(1.0));\n";
    if (f.opt_fog)
        src += "combined=vec4f(mix(combined.rgb,v.fog.rgb,v.fog.a),combined.a);\n";
    if (f.opt_texture_edge && f.opt_alpha)
        src += "if(combined.a>0.19){combined.a=1.0;}else{discard;}\n";
    if (f.opt_noise && f.opt_alpha)
        src += "combined.a*=floor(clamp(noise+combined.a,0.0,1.0));\n";
    if (f.opt_grayscale)
        src += "combined=vec4f(mix(combined.rgb,v.gray.rgb*dot(combined.rgb,vec3f(1.0/"
               "3.0)),v.gray.a),combined.a);\n";
    if (f.opt_alpha_threshold && f.opt_alpha)
        src += "if(combined.a<8.0/256.0){discard;}\n";
    if (f.opt_invisible && f.opt_alpha)
        src += "combined.a=0.0;\n";
    src += "return Fragment(combined" + std::string(f.opt_prim_depth ? ",params.depth" : "") +
           ");\n}";
    shader.source = std::move(src);
}
} // namespace Fast
