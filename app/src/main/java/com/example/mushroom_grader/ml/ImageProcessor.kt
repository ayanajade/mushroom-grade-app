package com.example.mushroom_grader.ml

import android.graphics.Bitmap

class ImageProcessor {
    companion object {
        private const val INPUT_SIZE = 640
    }

    fun preprocessImage(bitmap: Bitmap): Array<Array<Array<FloatArray>>> {
        val resized = Bitmap.createScaledBitmap(bitmap, INPUT_SIZE, INPUT_SIZE, true)
        val floatValues = FloatArray(1 * INPUT_SIZE * INPUT_SIZE * 3)
        val pixels = IntArray(INPUT_SIZE * INPUT_SIZE)
        resized.getPixels(pixels, 0, INPUT_SIZE, 0, 0, INPUT_SIZE, INPUT_SIZE)

        var pixel = 0
        for (i in pixels.indices) {
            val pixelValue = pixels[i]
            floatValues[pixel++] = ((pixelValue shr 16) and 0xFF) / 255.0f
            floatValues[pixel++] = ((pixelValue shr 8) and 0xFF) / 255.0f
            floatValues[pixel++] = (pixelValue and 0xFF) / 255.0f
        }

        return Array(1) {
            Array(INPUT_SIZE) {
                Array(INPUT_SIZE) { FloatArray(3) }
            }
        }.apply {
            var idx = 0
            for (i in 0 until INPUT_SIZE) {
                for (j in 0 until INPUT_SIZE) {
                    for (k in 0..2) {
                        this[0][i][j][k] = floatValues[idx++]
                    }
                }
            }
        }
    }

    fun postprocessOutput(output: FloatArray): ClassificationResult {
        val maxIndex = output.indices.maxByOrNull { output[it] } ?: 0
        val confidence = output[maxIndex]
        return ClassificationResult(maxIndex, confidence, System.currentTimeMillis())
    }
}
