@file:OptIn(ExperimentalTestApi::class)

package com.tap.n64.controls

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.v2.runComposeUiTest
import com.tap.n64.controls.editor.N64ControlsEditor
import com.tap.n64.controls.editor.N64ControlsEditorState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class PointerCancellationTest {
    // The Skiko test injector's cancel() is a no-op. Exercise the real Android cancel event here.
    @Test
    fun cancelRestoresTheDraggedControl() =
        runComposeUiTest {
            val editor = N64ControlsEditorState(ControlLayout.Default)
            setContent { ControlsFixture { N64ControlsEditor(editor, Modifier.fillMaxSize()) } }
            onNodeWithTag("n64-editor").performTouchInput {
                down(Offset(888f, 315f))
                moveTo(Offset(480f, 200f))
                cancel()
            }
            runOnIdle {
                assertFalse(editor.canUndo)
                assertEquals(ControlLayout.Default, editor.layout)
                assertEquals(849, editor.bounds(ControlId.A).left)
            }
        }
}
