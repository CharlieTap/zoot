package com.tap.zoot.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

internal object ZootColors {
    val Mint = Color(0xff9cf0cf)
    val OnMint = Color(0xff141719)
    val Panel = Color(0xf5141719)
    val Border = Color(0xff61696e)
    val Text = Color(0xfff4f5f5)
    val Muted = Color(0xffb2bec7)
    val Background = Color(0xff121619)
    val Scrim = Color.Black.copy(alpha = 0.45f)
}

internal object ZootTextStyles {
    val Label = TextStyle(color = ZootColors.Text, fontSize = 13.sp)
    val Mono = TextStyle(color = ZootColors.Muted, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
}

internal val PanelShape = RoundedCornerShape(6.dp)
