package com.tap.n64.controls.editor

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntRect
import com.tap.n64.controls.ControlId
import com.tap.n64.controls.ControlLayout
import com.tap.n64.controls.ControlPosition
import com.tap.n64.controls.internal.ControlGeometry
import com.tap.n64.controls.internal.centreX
import com.tap.n64.controls.internal.centreY
import com.tap.n64.controls.internal.constrainCentre
import com.tap.n64.controls.internal.snapCentre
import kotlin.math.abs
import kotlin.math.roundToInt

/** A draft layout. Completed moves can be undone; saving the draft is up to the caller. */
class N64ControlsEditorState(
    initialLayout: ControlLayout,
) {
    private val history = LayoutHistory(initialLayout)

    var layout by mutableStateOf(initialLayout)
        private set
    var canUndo by mutableStateOf(false)
        private set
    var showTouchAreas by mutableStateOf(true)
    var snap by mutableStateOf(false)

    internal var selected by mutableStateOf<ControlId?>(null)
        private set
    internal var drag by mutableStateOf<ControlDrag?>(null)
        private set
    internal var geometry: ControlGeometry? = null
        private set

    internal val dragging: Boolean get() = drag?.moved == true
    internal val guideX: Float get() = drag?.guideX ?: Float.NaN
    internal val guideY: Float get() = drag?.guideY ?: Float.NaN

    internal fun measure(
        width: Int,
        height: Int,
        density: Float,
    ): ControlGeometry = ControlGeometry(width, height, density, layout).also { geometry = it }

    internal fun bounds(id: ControlId): IntRect {
        val drag = drag
        if (drag != null && drag.id == id) return drag.bounds
        return geometry?.get(id) ?: IntRect.Zero
    }

    internal fun select(id: ControlId?) {
        selected = id
    }

    internal fun begin(point: Offset) {
        cancelDrag()
        val geometry = geometry ?: return
        val id = geometry.hit(point)
        selected = id
        if (id == null) return
        val others = ControlId.entries.filter { it != id }.map(geometry::get)
        drag =
            ControlDrag(
                id = id,
                origin = geometry[id],
                pointerDown = point,
                snapX = FloatArray(others.size + 1) { others.getOrNull(it)?.centreX ?: (geometry.width / 2f) },
                snapY = FloatArray(others.size + 1) { others.getOrNull(it)?.centreY ?: (geometry.height / 2f) },
            )
    }

    internal fun drag(
        point: Offset,
        slop: Float,
    ) {
        val drag = drag ?: return
        val geometry = geometry ?: return
        val dx = point.x - drag.pointerDown.x
        val dy = point.y - drag.pointerDown.y
        if (!drag.moved && abs(dx) < slop && abs(dy) < slop) return
        drag.moved = true
        var x = drag.origin.centreX + dx
        var y = drag.origin.centreY + dy
        drag.guideX = Float.NaN
        drag.guideY = Float.NaN
        if (snap) {
            val snappedX = snapCentre(x, drag.snapX, SNAP_DISTANCE * geometry.unit)
            val snappedY = snapCentre(y, drag.snapY, SNAP_DISTANCE * geometry.unit)
            if (x != snappedX) drag.guideX = snappedX
            if (y != snappedY) drag.guideY = snappedY
            x = snappedX
            y = snappedY
        }
        drag.bounds = place(geometry, drag.id, x, y)
    }

    internal fun finish(
        point: Offset,
        slop: Float,
    ) {
        drag(point, slop)
        val drag = drag
        if (drag != null && drag.moved && drag.bounds != drag.origin) commit(drag.id, drag.bounds)
        cancelDrag()
    }

    /** Abandon an unfinished move, without changing the draft or its history. */
    fun cancelDrag() {
        drag = null
    }

    fun undo() {
        cancelDrag()
        history.undo()
        changed()
    }

    fun reset() {
        cancelDrag()
        history.reset()
        selected = null
        changed()
    }

    internal fun nudge(
        id: ControlId,
        dx: Float,
        dy: Float,
    ) {
        cancelDrag()
        val geometry = geometry ?: return
        selected = id
        val current = geometry[id]
        val step = NUDGE_DISTANCE * geometry.unit
        val moved = place(geometry, id, current.centreX + dx * step, current.centreY + dy * step)
        if (moved != current) commit(id, moved)
    }

    private fun place(
        geometry: ControlGeometry,
        id: ControlId,
        centreX: Float,
        centreY: Float,
    ): IntRect {
        val size = geometry[id]
        val left = (constrainCentre(centreX, size.width.toFloat(), geometry.width.toFloat()) - size.width / 2f).roundToInt()
        val top = (constrainCentre(centreY, size.height.toFloat(), geometry.height.toFloat()) - size.height / 2f).roundToInt()
        return IntRect(left, top, left + size.width, top + size.height)
    }

    private fun commit(
        id: ControlId,
        bounds: IntRect,
    ) {
        val geometry = geometry ?: return
        history.update(layout.moved(id, ControlPosition(bounds.centreX / geometry.width, bounds.centreY / geometry.height)))
        changed()
    }

    private fun changed() {
        layout = history.layout
        canUndo = history.canUndo
        geometry = geometry?.withLayout(layout)
    }

    private companion object {
        const val SNAP_DISTANCE = 6f
        const val NUDGE_DISTANCE = 12f
    }
}

@Composable
fun rememberN64ControlsEditorState(initialLayout: ControlLayout): N64ControlsEditorState =
    remember { N64ControlsEditorState(initialLayout) }
