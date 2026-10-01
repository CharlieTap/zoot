package com.tap.n64.controls.internal

import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.sp
import com.tap.n64.controls.ControlId
import com.tap.n64.input.N64Button

private class DpadArrow(
    val symbol: String,
    val button: N64Button,
    val x: Float,
    val y: Float,
)

private val DpadArrows =
    listOf(
        DpadArrow("▲", N64Button.DpadUp, 0.5f, 0.23f),
        DpadArrow("▼", N64Button.DpadDown, 0.5f, 0.87f),
        DpadArrow("◀", N64Button.DpadLeft, 0.18f, 0.55f),
        DpadArrow("▶", N64Button.DpadRight, 0.82f, 0.55f),
    )

private val DarkLabel = Color(0xff252a30)
private val DpadArrowIdle = Color(0xffaeb4ba)
private val DpadArrowHeld = Color(0xffffd455)

@Composable
internal fun ControlFace(
    id: ControlId,
    artwork: ControlArtwork,
    modifier: Modifier,
    interaction: ControlInteraction? = null,
) {
    val textMeasurer = rememberTextMeasurer()
    Spacer(
        modifier.drawWithCache {
            val sprite = id.sprite
            val capsule = sprite == Sprite.Shoulder || sprite == Sprite.Start
            val fontSize =
                when (id) {
                    ControlId.Start -> size.height * 0.23f
                    ControlId.Dpad -> size.width * 0.14f
                    else -> size.height * 0.37f
                } / density
            val labelDensity = Density(density, 1f)
            val textStyle = TextStyle(fontFamily = artwork.labels, fontWeight = FontWeight.Bold, fontSize = fontSize.sp)
            val symbolStyle = textStyle.copy(fontFamily = artwork.symbols)
            val metrics = textMeasurer.measure("M", textStyle, density = labelDensity)
            val dpadLabels =
                if (id ==
                    ControlId.Dpad
                ) {
                    DpadArrows.map { textMeasurer.measure(it.symbol, symbolStyle, density = labelDensity) }
                } else {
                    emptyList()
                }
            val label =
                textMeasurer.measure(
                    id.legend,
                    if (sprite == Sprite.C) symbolStyle else textStyle,
                    density = labelDensity,
                )
            val labelColour = if (sprite == Sprite.C || sprite == Sprite.Shoulder) DarkLabel else Color.White
            onDrawBehind {
                val heldButtons = interaction?.heldButtons ?: 0
                drawSprite(
                    artwork,
                    sprite,
                    size.width / 2,
                    size.height / 2,
                    size.width,
                    if (capsule) size.height * 0.85f else size.height,
                    pressed = heldButtons != 0 && id != ControlId.Dpad,
                )
                when (id) {
                    ControlId.Stick -> {
                        val travel = size.width * ControlInteraction.STICK_TRAVEL
                        drawSprite(
                            artwork,
                            Sprite.Stick,
                            size.width / 2 + (interaction?.stickX ?: 0f) * travel,
                            size.height / 2 + (interaction?.stickY ?: 0f) * travel,
                            size.width * 0.46f,
                            size.height * 0.46f,
                        )
                    }

                    ControlId.Dpad -> {
                        DpadArrows.forEachIndexed { index, arrow ->
                            val text = dpadLabels[index]
                            drawText(
                                text,
                                color = if (heldButtons and arrow.button.mask != 0) DpadArrowHeld else DpadArrowIdle,
                                topLeft =
                                    Offset(
                                        size.width * arrow.x - text.getLineRight(0) / 2f,
                                        size.height * arrow.y - text.firstBaseline,
                                    ),
                            )
                        }
                    }

                    else -> {
                        val baseline = size.height / 2 + metrics.firstBaseline - metrics.getLineBottom(0) / 2
                        drawText(
                            label,
                            color = labelColour,
                            topLeft = Offset(size.width / 2 - label.getLineRight(0) / 2f, baseline - label.firstBaseline),
                        )
                    }
                }
            }
        },
    )
}
