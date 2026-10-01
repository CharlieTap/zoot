package com.tap.n64.controls

import androidx.compose.ui.geometry.Offset
import com.tap.n64.controls.editor.N64ControlsEditorState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EditorStateTest {
    private fun editor() = N64ControlsEditorState(ControlLayout.Default).apply { measure(960, 400, 1f) }

    @Test
    fun defaultsAndCancelledMovesDoNotChangeTheDraft() {
        val editor = editor()
        assertTrue(editor.showTouchAreas)
        assertFalse(editor.snap)
        editor.begin(Offset(888f, 315f))
        editor.drag(Offset(480f, 200f), 8f)
        assertTrue(editor.dragging)
        assertEquals(441, editor.bounds(ControlId.A).left)
        editor.cancelDrag()
        assertEquals(849, editor.bounds(ControlId.A).left)
        assertEquals(ControlLayout.Default, editor.layout)
        assertFalse(editor.canUndo)
    }

    @Test
    fun finalUpMovementIsCommittedOnce() {
        val editor = editor()
        editor.begin(Offset(888f, 315f))
        editor.drag(Offset(500f, 200f), 8f)
        assertFalse(editor.canUndo)
        editor.finish(Offset(480f, 200f), 8f)
        assertEquals(ControlPosition(0.5f, 0.5f), editor.layout[ControlId.A])
        assertTrue(editor.canUndo)
        editor.undo()
        assertEquals(ControlLayout.Default, editor.layout)
        assertFalse(editor.canUndo)
    }

    @Test
    fun snappingUsesOtherControlsWhileMoving() {
        val editor = editor().apply { snap = true }
        editor.begin(Offset(888f, 315f))
        editor.drag(Offset(820f, 202f), 8f)
        assertEquals(817f, editor.guideX)
        assertEquals(200f, editor.guideY)
        editor.finish(Offset(820f, 202f), 8f)
        assertEquals(ControlPosition(817f / 960f, 0.5f), editor.layout[ControlId.A])
    }

    @Test
    fun noMovementOrReturningToOriginDoesNotCreateHistory() {
        val editor = editor()
        editor.begin(Offset(888f, 315f))
        editor.finish(Offset(890f, 316f), 8f)
        assertFalse(editor.canUndo)
        editor.begin(Offset(888f, 315f))
        editor.drag(Offset(480f, 200f), 8f)
        editor.finish(Offset(888f, 315f), 8f)
        assertFalse(editor.canUndo)
    }

    @Test
    fun resetAndAccessibleMovesRemainUndoable() {
        val editor = editor()
        editor.nudge(ControlId.A, -1f, 0f)
        val moved = editor.layout
        assertEquals(876f / 960f, moved[ControlId.A]?.x)
        editor.reset()
        assertEquals(ControlLayout.Default, editor.layout)
        editor.undo()
        assertEquals(moved, editor.layout)
        editor.undo()
        assertFalse(editor.canUndo)
    }
}
