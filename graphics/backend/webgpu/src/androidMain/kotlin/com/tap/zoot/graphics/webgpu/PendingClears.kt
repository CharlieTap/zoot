package com.tap.zoot.graphics.webgpu

/** Clears requested per target but not yet encoded. They become the load operations of the target's next pass. */
internal class PendingClears {
    private var clears = IntArray(INITIAL_TARGETS)

    val targets: IntRange
        get() = clears.indices

    fun request(
        targetId: Int,
        colour: Boolean,
        depth: Boolean,
    ) {
        if (targetId >= clears.size) clears = clears.copyOf(maxOf(targetId + 1, clears.size * 2))
        clears[targetId] = clears[targetId] or (if (colour) COLOUR_BIT else 0) or (if (depth) DEPTH_BIT else 0)
    }

    fun has(targetId: Int): Boolean = targetId < clears.size && clears[targetId] != 0

    /** Drops a pending colour clear for a target about to be overwritten entirely. */
    fun dropColour(targetId: Int) {
        if (targetId < clears.size) clears[targetId] = clears[targetId] and COLOUR_BIT.inv()
    }

    /** Returns and forgets the target's pending clears. */
    fun take(targetId: Int): Clears {
        if (targetId >= clears.size) return Clears.NONE
        val taken = clears[targetId]
        clears[targetId] = 0
        // Entries are ordered so that each ordinal is its bit pattern.
        return Clears.entries[taken]
    }

    enum class Clears(
        val colour: Boolean,
        val depth: Boolean,
    ) {
        NONE(false, false),
        COLOUR(true, false),
        DEPTH(false, true),
        BOTH(true, true),
    }

    private companion object {
        const val INITIAL_TARGETS = 8
        const val COLOUR_BIT = 1
        const val DEPTH_BIT = 2
    }
}
