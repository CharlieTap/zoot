@file:OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)

package com.tap.crashreporting

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asSkiaBitmap
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import platform.Foundation.NSData
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.create
import platform.Foundation.writeToFile

internal actual fun saveCapture(
    name: String,
    image: ImageBitmap,
) {
    val snapshot = Image.makeFromBitmap(image.asSkiaBitmap())
    try {
        val data = snapshot.encodeToData(EncodedImageFormat.PNG)!!
        try {
            val png = data.bytes
            val path = "${NSTemporaryDirectory()}crash-dialog-$name.png"
            png.usePinned { NSData.create(bytes = it.addressOf(0), length = png.size.toULong()).writeToFile(path, atomically = true) }
            println("Crash dialog capture: $path")
        } finally {
            data.close()
        }
    } finally {
        snapshot.close()
    }
}
