#pragma once
#include <algorithm>
#define BE16SWAP(value) __builtin_bswap16(value)
namespace Ship::Math { inline float clamp(float v, float lo, float hi) { return std::clamp(v, lo, hi); } }
