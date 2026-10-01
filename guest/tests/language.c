#include "oot_guest.h"
#include <assert.h>

__attribute__((export_name("test_language")))
void TestLanguage(void) {
    assert(OotGuest_GetLanguage() == 0);
    assert(oot_set_language(3) == 0);
    assert(OotGuest_GetLanguage() == 3);
    assert(oot_start() == 0);
    assert(oot_step(1) == OOT_STEP_FRAME_PRODUCED);
    assert(OotGuest_GetLanguage() == 3);
    assert(oot_set_language(1) == -1);
    assert(OotGuest_GetLanguage() == 3);
    assert(oot_set_language(0) == 0);
    assert(OotGuest_GetLanguage() == 0);
}
