package com.tap.n64.controls

import androidx.compose.ui.input.key.Key
import com.tap.n64.controls.editor.LayoutHistory
import com.tap.n64.controls.editor.isControlActivationKey
import com.tap.n64.controls.internal.constrainCentre
import com.tap.n64.controls.internal.snapCentre
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ControlLayoutTest {
    @Test
    fun editorBlocksControlActivationButNotFocusNavigationOrBack() {
        for (key in listOf(
            Key.Enter,
            Key.NumPadEnter,
            Key.DirectionCenter,
            Key.Spacebar,
            Key.ButtonA,
        )) {
            assertTrue(isControlActivationKey(key))
        }
        assertFalse(isControlActivationKey(Key.DirectionRight))
        assertFalse(isControlActivationKey(Key.Tab))
        assertFalse(isControlActivationKey(Key.Back))
    }

    @Test
    fun everyControlCanMoveIndependentlyAndSurvivesReload() {
        var layout = ControlLayout.Default
        for ((index, id) in ControlId.entries.withIndex()) {
            layout = layout.moved(id, ControlPosition(index / 20f, 0.6f))
        }
        assertEquals(layout, ControlLayout.decode(layout.encode()))
        assertNull(ControlLayout.Default[ControlId.A])
        assertEquals(ControlId.entries.size, layout.encode().split(';').size)
    }

    @Test
    fun missingAndInvalidStoredPositionsKeepDefaults() {
        val layout = ControlLayout.decode("UNKNOWN:0.5:0.5;A:NaN:0.2;B:Infinity:0.1;L:-1:0;R:0.2:2;C_UP:0.3:0.4;bad")
        assertEquals(ControlLayout.Default.moved(ControlId.CUp, ControlPosition(0.3f, 0.4f)), layout)
        assertEquals(ControlLayout.Default, ControlLayout.decode(""))
    }

    @Test
    fun undoRestoresOneCompletedMoveAtATime() {
        val saved = ControlLayout.Default.moved(ControlId.Start, ControlPosition(0.2f, 0.3f))
        val session = LayoutHistory(saved)
        assertFalse(session.canUndo)
        val first = saved.moved(ControlId.A, ControlPosition(0.5f, 0.5f))
        session.update(first)
        session.update(first.moved(ControlId.B, ControlPosition(0.5f, 0.5f)))
        assertTrue(session.canUndo)
        session.undo()
        assertEquals(first, session.layout)
        session.undo()
        assertEquals(saved, session.layout)
        assertFalse(session.canUndo)
        assertNull(saved[ControlId.A])
    }

    @Test
    fun resetIsUndoableAndEmptyChangesDoNotAddHistory() {
        val saved = ControlLayout.Default.moved(ControlId.Stick, ControlPosition(0.4f, 0.5f))
        val session = LayoutHistory(saved)
        session.update(saved)
        assertFalse(session.canUndo)
        session.reset()
        assertEquals(ControlLayout.Default, session.layout)
        session.reset()
        session.undo()
        assertEquals(saved, session.layout)
        assertFalse(session.canUndo)
    }

    @Test
    fun positionsUseTheWholeScreenIncludingTheGameAndOtherControls() {
        assertEquals(50f, constrainCentre(-20f, 100f, 800f))
        assertEquals(750f, constrainCentre(900f, 100f, 800f))
        assertEquals(400f, constrainCentre(400f, 100f, 800f))
        val saved = ControlLayout.Default.moved(ControlId.Stick, ControlPosition(0.5f, 0.5f))
        val overlap = saved.moved(ControlId.A, ControlPosition(0.5f, 0.5f))
        assertEquals(overlap[ControlId.Stick], overlap[ControlId.A])
    }

    @Test
    fun snappingChoosesOnlyNearbyCentres() {
        assertEquals(102f, snapCentre(100f, floatArrayOf(105f, 102f, 300f), 6f))
        assertEquals(100f, snapCentre(100f, floatArrayOf(120f, 300f), 6f))
    }

    @Test
    fun defaultPositionsMatchTheExistingControls() {
        assertEquals(97f, ControlId.Stick.defaultX(800f))
        assertEquals(288f, ControlId.Stick.defaultY(400f))
        assertEquals(400f, ControlId.Start.defaultX(800f))
        assertEquals(371f, ControlId.Start.defaultY(400f))
        assertEquals(708f, ControlId.CUp.defaultX(800f))
        assertEquals(103f, ControlId.CUp.defaultY(400f))
    }
}
