package com.tap.zoot.graphics.upscaler.sgsr1

// WGSL port of Qualcomm SGSR 1's RGBA mode with edge-direction filtering.
// Copyright (c) 2025, Qualcomm Innovation Center, Inc. SPDX-License-Identifier: BSD-3-Clause
// Source and full license: third-party/THIRD_PARTY_NOTICES.txt.
internal const val SGSR1_SHADER = """
fn sgsrWeight(dx: f32, dy: f32, value: f32, contrastScale: f32, direction: vec2f) -> vec2f {
    let edgeDistance = dx * direction.y + dy * direction.x;
    let distance = dx * dx + dy * dy + edgeDistance * edgeDistance * (clamp(value * value * contrastScale, 0.0, 1.0) * 0.7 - 1.0);
    let a = distance - 4.0;
    let weight = (distance * a - a) * (a * a);
    return vec2f(weight, weight * value);
}

fn sgsrEdgeDirection(left: vec4f, right: vec4f) -> vec2f {
    let diagonalA = right.x - left.z;
    let diagonalB = right.w - left.y;
    let delta = vec2f(diagonalA + diagonalB, diagonalA - diagonalB);
    return delta * inverseSqrt((delta.x * delta.x + 3.075740e-05) + delta.y * delta.y);
}

fn upscale(uv: vec2f) -> vec4f {
    let color = textureSampleLevel(image, imageSampler, uv, 0.0).rgb;
    let size = vec2f(textureDimensions(image));
    let texel = 1.0 / size;
    let position = uv * size + vec2f(-0.5, 0.5);
    let pixel = floor(position);
    let fraction = position - pixel;
    let origin = pixel * texel;
    var left = textureGather(1, image, imageSampler, origin);
    let edge = abs(left.z - left.y) + abs(color.g - left.y) + abs(color.g - left.z);
    if (edge <= 8.0 / 255.0) {
        return vec4f(color, 1.0);
    }

    var right = textureGather(1, image, imageSampler, origin + vec2f(2.0 * texel.x, 0.0));
    let up = textureGather(1, image, imageSampler, origin + vec2f(texel.x, -texel.y));
    let down = textureGather(1, image, imageSampler, origin + vec2f(texel.x, texel.y));
    var upDown = vec4f(up.wz, down.yx);
    let mean = (left.y + left.z + right.x + right.w) * 0.25;
    left -= vec4f(mean);
    right -= vec4f(mean);
    upDown -= vec4f(mean);
    let center = color.g - mean;
    let sum = dot(abs(left), vec4f(1.0)) + dot(abs(right), vec4f(1.0)) + dot(abs(upDown), vec4f(1.0));
    let sumMean = 10.14185 / sum;
    let contrastScale = sumMean * sumMean;
    let direction = sgsrEdgeDirection(left, right);

    var weighted = sgsrWeight(fraction.x, fraction.y + 1.0, upDown.x, contrastScale, direction);
    weighted += sgsrWeight(fraction.x - 1.0, fraction.y + 1.0, upDown.y, contrastScale, direction);
    weighted += sgsrWeight(fraction.x - 1.0, fraction.y - 2.0, upDown.z, contrastScale, direction);
    weighted += sgsrWeight(fraction.x, fraction.y - 2.0, upDown.w, contrastScale, direction);
    weighted += sgsrWeight(fraction.x + 1.0, fraction.y - 1.0, left.x, contrastScale, direction);
    weighted += sgsrWeight(fraction.x, fraction.y - 1.0, left.y, contrastScale, direction);
    weighted += sgsrWeight(fraction.x, fraction.y, left.z, contrastScale, direction);
    weighted += sgsrWeight(fraction.x + 1.0, fraction.y, left.w, contrastScale, direction);
    weighted += sgsrWeight(fraction.x - 1.0, fraction.y - 1.0, right.x, contrastScale, direction);
    weighted += sgsrWeight(fraction.x - 2.0, fraction.y - 1.0, right.y, contrastScale, direction);
    weighted += sgsrWeight(fraction.x - 2.0, fraction.y, right.z, contrastScale, direction);
    weighted += sgsrWeight(fraction.x - 1.0, fraction.y, right.w, contrastScale, direction);
    let maximum = max(max(left.y, left.z), max(right.x, right.w));
    let minimum = min(min(left.y, left.z), min(right.x, right.w));
    let sharpened = clamp(2.0 * weighted.y / weighted.x, minimum, maximum);
    let delta = clamp(sharpened - center, -23.0 / 255.0, 23.0 / 255.0);
    return vec4f(clamp(color + vec3f(delta), vec3f(0.0), vec3f(1.0)), 1.0);
}
"""
