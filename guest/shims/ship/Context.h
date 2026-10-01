#pragma once
#include "ship/resource/ResourceManager.h"
#include "ship/config/ConsoleVariable.h"
namespace Ship {
struct Context {
    static Context* GetRawInstance() { static Context context; return &context; }
    ResourceManager* GetResourceManager() { static ResourceManager resources; return &resources; }
    ConsoleVariables* GetConsoleVariables() { static ConsoleVariables variables; return &variables; }
};
}
