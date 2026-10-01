package com.tap.n64.controls

data class ControlPosition(
    val x: Float,
    val y: Float,
)

/** Custom centres as fractions of the full control surface; absent entries keep their default position. */
class ControlLayout private constructor(
    private val positions: Map<ControlId, ControlPosition>,
) {
    operator fun get(id: ControlId): ControlPosition? = positions[id]

    fun moved(
        id: ControlId,
        position: ControlPosition,
    ): ControlLayout = ControlLayout(positions + (id to position))

    fun encode(): String = positions.entries.joinToString(";") { (id, position) -> "${id.storageKey}:${position.x}:${position.y}" }

    override fun equals(other: Any?): Boolean = other is ControlLayout && positions == other.positions

    override fun hashCode(): Int = positions.hashCode()

    companion object {
        val Default = ControlLayout(emptyMap())

        fun decode(value: String): ControlLayout {
            val positions =
                buildMap {
                    for (entry in value.split(';')) {
                        val parts = entry.split(':')
                        if (parts.size != 3) continue
                        val id = ControlId.entries.firstOrNull { it.storageKey == parts[0] } ?: continue
                        val x = parts[1].toFloatOrNull() ?: continue
                        val y = parts[2].toFloatOrNull() ?: continue
                        if (x in 0f..1f && y in 0f..1f) put(id, ControlPosition(x, y))
                    }
                }
            return ControlLayout(positions)
        }
    }
}
