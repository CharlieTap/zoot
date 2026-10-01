#pragma once
#include <cstdio>
#define SPDLOG_ERROR(message, ...) fprintf(stderr, "Fast3D: " message "\n")
#define SPDLOG_WARN(message, ...) fprintf(stderr, "Fast3D: " message "\n")
#define SPDLOG_CRITICAL(message, ...) fprintf(stderr, "Fast3D: " message "\n")
