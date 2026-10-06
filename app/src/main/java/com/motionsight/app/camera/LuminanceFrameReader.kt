package com.motionsight.app.camera

import androidx.camera.core.ImageProxy

object LuminanceFrameReader {
    fun read(image: ImageProxy): ByteArray {
        val plane = image.planes[0]
        val buffer = plane.buffer
        val width = image.width
        val height = image.height
        val output = ByteArray(width * height)
        val rowStride = plane.rowStride
        val pixelStride = plane.pixelStride

        if (pixelStride == 1 && rowStride == width) {
            buffer.rewind()
            buffer.get(output, 0, output.size.coerceAtMost(buffer.remaining()))
            return output
        }

        for (row in 0 until height) {
            val rowStart = row * rowStride
            for (column in 0 until width) {
                output[row * width + column] = buffer.get(rowStart + column * pixelStride)
            }
        }
        return output
    }
}
