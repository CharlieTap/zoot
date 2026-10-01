#pragma once
namespace Ship {
struct ConsoleVariables {
    float GetFloat(const char*, float value) { return value; }
    int GetInteger(const char*, int value) { return value; }
};
}
