package com.tap.zoot.ui.performance

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.tap.zoot.ui.theme.ZootColors

private const val GRAPH_WINDOW_NANOS = 3_000_000_000L

@Composable
internal fun FrameTimeGraph(
    state: OverlayState,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .semantics { contentDescription = "Frame times over the last three seconds, 35 to 100 milliseconds" }
            .graphicsLayer()
            .drawWithCache {
                val line = Path()
                val fill = Path()
                val stroke = Stroke(width = 1.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                val wash = Brush.verticalGradient(listOf(ZootColors.Mint.copy(alpha = 0.15f), ZootColors.Mint.copy(alpha = 0.02f)))
                var pathRevision = Long.MIN_VALUE
                var newestTime = 0L
                onDrawBehind {
                    val now = state.drawTime
                    val history = state.history
                    if (history.count > 0) {
                        // Rebuild on new samples; between them, scrolling only translates the paths.
                        if (pathRevision != history.revision) {
                            newestTime = history.newestTime
                            line.rewind()
                            var previousX = 0f
                            var previousY = 0f
                            var firstX = 0f
                            for (index in 0 until history.count) {
                                val x = graphX(newestTime - history.times[index], size.width)
                                val y = graphY(history.intervals[index], size.height - stroke.width) + stroke.width / 2
                                if (index == 0) {
                                    firstX = x
                                    line.moveTo(x, y)
                                } else {
                                    // Flat tangents keep the trace smooth without inventing overshoot between real samples.
                                    val middle = (previousX + x) / 2
                                    line.cubicTo(middle, previousY, middle, y, x, y)
                                }
                                previousX = x
                                previousY = y
                            }
                            fill.rewind()
                            fill.addPath(line)
                            fill.lineTo(previousX, size.height)
                            fill.lineTo(firstX, size.height)
                            fill.close()
                            pathRevision = history.revision
                        }
                        clipRect {
                            val targetY = graphY(50f, size.height - stroke.width) + stroke.width / 2
                            drawLine(ZootColors.Mint.copy(alpha = 0.16f), Offset(0f, targetY), Offset(size.width, targetY), 0.5.dp.toPx())
                            translate(left = graphX(now - newestTime, size.width) - size.width) {
                                drawPath(fill, wash)
                                drawPath(line, ZootColors.Mint, style = stroke)
                            }
                        }
                    }
                }
            },
    )
}

internal fun graphX(
    ageNanos: Long,
    width: Float,
): Float = width * (1f - ageNanos.toFloat() / GRAPH_WINDOW_NANOS)

// A fixed scale keeps ordinary jitter from making the whole graph bounce. Large stalls clip at the top.
internal fun graphY(
    frameMs: Float,
    height: Float,
): Float = height * (1f - ((frameMs - 35f) / 65f).coerceIn(0f, 1f))
