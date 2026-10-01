package com.tap.zoot.ui.controls

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.tap.n64.controls.ControlLayout
import com.tap.n64.controls.N64Controls
import com.tap.n64.controls.editor.N64ControlsEditor
import com.tap.n64.controls.editor.N64ControlsEditorState
import com.tap.n64.input.N64InputSink
import com.tap.zoot.settings.ControlsSettings
import com.tap.zoot.ui.settings.ControlLayoutToolbar

@Composable
internal fun GameControls(
    input: N64InputSink,
    controls: ControlsSettings,
    enabled: Boolean,
    editor: N64ControlsEditorState?,
    onFinishEditing: (ControlLayout?) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxSize()) {
        if (editor != null) {
            N64ControlsEditor(editor, Modifier.fillMaxSize(), enabled = enabled)
            ControlLayoutToolbar(
                canUndo = editor.canUndo,
                touchAreas = editor.showTouchAreas,
                snap = editor.snap,
                onUndo = editor::undo,
                onReset = editor::reset,
                onCancel = {
                    editor.cancelDrag()
                    onFinishEditing(null)
                },
                onDone = {
                    editor.cancelDrag()
                    onFinishEditing(editor.layout)
                },
                onTouchAreasChange = { editor.showTouchAreas = it },
                onSnapChange = { editor.snap = it },
            )
        } else if (controls.touchControls) {
            N64Controls(
                input = input,
                modifier = Modifier.fillMaxSize(),
                layout = controls.controlLayout,
                enabled = enabled,
                opacity = controls.controlOpacity,
                vibration = controls.buttonVibration,
                description = ::ootControlDescription,
            )
        }
    }
}
