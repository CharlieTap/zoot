@file:OptIn(ExperimentalTestApi::class)

package com.tap.n64.controls

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.v2.runComposeUiTest
import com.tap.n64.controls.editor.N64ControlsEditor
import com.tap.n64.controls.editor.N64ControlsEditorState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ControlsEditorTest {
    @Test
    fun draggingSnapsAndCommitsOneUndoStep() =
        runComposeUiTest {
            val editor = N64ControlsEditorState(ControlLayout.Default)
            setContent { ControlsFixture { N64ControlsEditor(editor, Modifier.fillMaxSize()) } }
            val controls = onNodeWithTag("n64-editor")
            val fixture = onNodeWithTag("fixture")
            saveCapture("editor", fixture.captureToImage())
            runOnIdle { editor.snap = true }
            controls.performTouchInput {
                down(Offset(888f, 315f))
                moveTo(Offset(478f, 200f))
            }
            runOnIdle {
                assertFalse(editor.canUndo)
                assertEquals(480f, editor.guideX)
            }
            saveCapture("editor-drag", fixture.captureToImage())
            controls.performTouchInput { up() }
            runOnIdle {
                assertEquals(ControlPosition(0.5f, 0.5f), editor.layout[ControlId.A])
                assertTrue(editor.canUndo)
                editor.undo()
                assertEquals(ControlLayout.Default, editor.layout)
                assertFalse(editor.canUndo)
            }
        }

    @Test
    fun disablingDuringADragRestoresOriginalPosition() =
        runComposeUiTest {
            val editor = N64ControlsEditorState(ControlLayout.Default)
            val enabled = mutableStateOf(true)
            setContent { ControlsFixture { N64ControlsEditor(editor, Modifier.fillMaxSize(), enabled.value) } }
            val controls = onNodeWithTag("n64-editor")
            controls.performTouchInput {
                down(Offset(888f, 315f))
                moveTo(Offset(480f, 200f))
            }
            runOnIdle { enabled.value = false }
            waitForIdle()
            runOnIdle {
                assertFalse(editor.canUndo)
                assertEquals(ControlLayout.Default, editor.layout)
                assertEquals(849, editor.bounds(ControlId.A).left)
            }
        }
}
