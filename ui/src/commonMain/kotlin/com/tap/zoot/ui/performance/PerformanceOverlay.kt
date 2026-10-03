package com.tap.zoot.ui.performance

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.tap.zoot.performance.FrameHistory
import com.tap.zoot.performance.METRICS_WINDOW_NANOS
import com.tap.zoot.performance.PerformanceMetrics
import com.tap.zoot.performance.PerformanceTelemetry
import com.tap.zoot.runtime.platform.monotonicNanos
import com.tap.zoot.ui.theme.PanelShape
import com.tap.zoot.ui.theme.ZootColors
import kotlinx.coroutines.isActive
import kotlin.math.roundToInt

private val LabelStyle =
    TextStyle(
        color = ZootColors.Muted,
        fontFamily = FontFamily.Monospace,
        fontSize = 7.5.sp,
        lineHeight = 9.sp,
    )
private val ValueStyle =
    TextStyle(
        color = ZootColors.Text,
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Medium,
        fontSize = 10.5.sp,
        lineHeight = 12.sp,
    )
private const val INITIALISING = "Initialising…"
private const val POLL_INTERVAL_NANOS = 25_000_000L

internal class OverlayState {
    val history = FrameHistory()
    var metrics by mutableStateOf(PerformanceMetrics())
    var message by mutableStateOf<String?>(INITIALISING)
    var drawTime by mutableLongStateOf(0L)
}

@Composable
fun PerformanceOverlay(
    telemetry: PerformanceTelemetry,
    modifier: Modifier = Modifier,
    paused: Boolean = false,
) {
    val state = remember(telemetry) { OverlayState() }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(telemetry, lifecycle, paused) {
        if (paused) {
            telemetry.copyInto(state.history)
            val history = state.history
            val now = if (history.isEmpty) monotonicNanos() else history.newestTime
            state.message = statusMessage(history)
            state.metrics = history.metrics(now)
            state.drawTime = now
            return@LaunchedEffect
        }
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            var nextPoll = 0L
            var nextReadout = 0L
            while (isActive) {
                val now = withFrameNanos { monotonicNanos() }
                if (now >= nextPoll) {
                    telemetry.copyInto(state.history)
                    val message = statusMessage(state.history)
                    if (state.message != null && message == null) nextReadout = now
                    state.message = message
                    nextPoll = now + POLL_INTERVAL_NANOS
                }
                if (now >= nextReadout) {
                    state.metrics = state.history.metrics(now)
                    nextReadout = now + METRICS_WINDOW_NANOS
                }
                // Only the graph's draw phase reads this clock. Text does not recompose at vsync.
                if (state.message == null) state.drawTime = now
            }
        }
    }
    OverlayPanel(state, modifier)
}

private fun statusMessage(history: FrameHistory): String? = if (history.isEmpty) INITIALISING else null

@Composable
internal fun OverlayPanel(
    state: OverlayState,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .width(348.dp)
            .height(28.dp)
            .background(Color(0xdb141719), PanelShape)
            .border(0.5.dp, ZootColors.Border.copy(alpha = 0.65f), PanelShape)
            .padding(horizontal = 10.dp, vertical = 3.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        val message = state.message
        if (message != null) {
            BasicText(message, style = ValueStyle, maxLines = 1)
        } else {
            Readouts(state)
        }
    }
}

@Composable
private fun Readouts(state: OverlayState) {
    val metrics = state.metrics
    Row(verticalAlignment = Alignment.CenterVertically) {
        Metric("FPS", decimal(metrics.fps), Modifier.width(28.dp), ZootColors.Mint)
        Separator()
        Row(Modifier.width(98.dp), verticalAlignment = Alignment.Bottom) {
            Metric("FRAME", milliseconds(metrics.frameMs), Modifier.width(44.dp))
            FrameTimeGraph(state, Modifier.padding(start = 6.dp).size(48.dp, 12.dp))
        }
        Separator()
        Metric("GAME", milliseconds(metrics.stepMs), Modifier.width(46.dp))
        Separator()
        Metric("AUDIO", milliseconds(metrics.audioMs), Modifier.width(46.dp))
        Separator()
        Metric("RENDER", milliseconds(metrics.gpuHostMs), Modifier.width(46.dp))
    }
}

@Composable
private fun Metric(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color = ValueStyle.color,
) {
    Column(modifier) {
        BasicText(label, style = LabelStyle, maxLines = 1, softWrap = false)
        BasicText(value, style = ValueStyle.copy(color = valueColor), maxLines = 1, softWrap = false)
    }
}

@Composable
private fun Separator() {
    Box(Modifier.width(16.dp).fillMaxHeight(), contentAlignment = Alignment.Center) {
        Box(Modifier.width(0.5.dp).height(18.dp).background(Color(0xff666e73)))
    }
}

internal fun decimal(value: Float): String {
    val tenths = (value * 10).roundToInt()
    return "${tenths / 10}.${tenths % 10}"
}

internal fun milliseconds(value: Float): String = if (value >= 100) "${value.roundToInt()} ms" else "${decimal(value)} ms"
