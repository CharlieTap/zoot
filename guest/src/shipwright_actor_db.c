#include "global.h"
#include "soh/ActorDB.h"
#include <string.h>

#define DEFINE_ACTOR(name, id, allocType) extern ActorInit name##_InitVars;
#define DEFINE_ACTOR_INTERNAL(name, id, allocType) extern ActorInit name##_InitVars;
#define DEFINE_ACTOR_UNSET(id)
#include "tables/actor_table.h"
#undef DEFINE_ACTOR
#undef DEFINE_ACTOR_INTERNAL
#undef DEFINE_ACTOR_UNSET

#define DEFINE_ACTOR(name, id, allocType) &name##_InitVars,
#define DEFINE_ACTOR_INTERNAL(name, id, allocType) &name##_InitVars,
#define DEFINE_ACTOR_UNSET(id) NULL,
static ActorInit* const actorInitializers[] = {
#include "tables/actor_table.h"
};
#undef DEFINE_ACTOR
#undef DEFINE_ACTOR_INTERNAL
#undef DEFINE_ACTOR_UNSET

#define DEFINE_ACTOR(name, id, allocType) #name,
#define DEFINE_ACTOR_INTERNAL(name, id, allocType) #name,
#define DEFINE_ACTOR_UNSET(id) NULL,
static const char* const actorNames[] = {
#include "tables/actor_table.h"
};
#undef DEFINE_ACTOR
#undef DEFINE_ACTOR_INTERNAL
#undef DEFINE_ACTOR_UNSET

static ActorDBEntry actors[ARRAY_COUNT(actorInitializers)];

static ActorDBEntry* InitializeActor(int id) {
    if (id < 0 || id >= ARRAY_COUNT(actorInitializers) || actorInitializers[id] == NULL) {
        static ActorDBEntry invalid;
        return &invalid;
    }
    ActorDBEntry* entry = &actors[id];
    if (!entry->valid) {
        ActorInit* initializer = actorInitializers[id];
        entry->name = actorNames[id];
        entry->valid = true;
        entry->id = initializer->id;
        entry->category = initializer->category;
        entry->flags = initializer->flags;
        entry->objectId = initializer->objectId;
        entry->instanceSize = initializer->instanceSize;
        entry->init = initializer->init;
        entry->destroy = initializer->destroy;
        entry->update = initializer->update;
        entry->draw = initializer->draw;
        entry->reset = initializer->reset;
    }
    return entry;
}

ActorDBEntry* ActorDB_Retrieve(const int id) {
    return InitializeActor(id);
}

int ActorDB_RetrieveId(const char* name) {
    for (int id = 0; id < ARRAY_COUNT(actorNames); id++) {
        if (actorNames[id] != NULL && strcmp(actorNames[id], name) == 0) {
            return id;
        }
    }
    return -1;
}
