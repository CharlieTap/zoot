package com.tap.n64.controls.editor

import com.tap.n64.controls.ControlLayout

/** One undo entry per completed move or reset. The saved layout is never mutated. */
internal class LayoutHistory(
    initial: ControlLayout,
) {
    var layout = initial
        private set
    private val undoStack = ArrayDeque<ControlLayout>()
    val canUndo: Boolean get() = undoStack.isNotEmpty()

    fun update(next: ControlLayout) {
        if (next == layout) return
        undoStack.addLast(layout)
        layout = next
    }

    fun undo() {
        if (canUndo) layout = undoStack.removeLast()
    }

    fun reset() = update(ControlLayout.Default)
}
