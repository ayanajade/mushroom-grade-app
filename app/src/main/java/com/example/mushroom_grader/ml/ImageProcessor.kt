package com.example.mushroom_grader.ml

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.util.Log
import androidx.core.graphics.createBitmap
import androidx.core.graphics.scale
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

class ImageProcessor(private val context: Context) {

    companion object {
        private const val TAG = "ImageProcessor"
        private const val JPEG_QUALITY = 90
    }

    fun processImage(imagePath: String): Bitmap {
        Log.d(TAG, "Processing image from path: $imagePath")
        val bitmap = loadBitmapFromFile(imagePath)
            ?: throw IllegalArgumentException("Could not load image from path: $imagePath")
        val rotatedBitmap = handleExifRotation(imagePath, bitmap)

        // ✅ FIXED: Using KTX extension function Bitmap.scale()
        val resized = rotatedBitmap.scale(width = 256, height = 256, filter = true)

        Log.d(TAG, "Image processed successfully: ${resized.width}x${resized.height}")

        if (resized != rotatedBitmap) {
            rotatedBitmap.recycle()
        }
        if (rotatedBitmap != bitmap) {
            bitmap.recycle()
        }

        return resized
    }

    fun loadBitmapFromFile(filePath: String): Bitmap? {
        return try {
            BitmapFactory.decodeFile(filePath)
        } catch (e: Exception) {
            Log.e(TAG, "Error loading bitmap from file", e)
            null
        }
    }

    // ✅ Load bitmap from URI with proper EXIF handling
    fun loadBitmapFromUri(uri: Uri): Bitmap? {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri)
            val bitmap = BitmapFactory.decodeStream(inputStream)
            inputStream?.close()

            if (bitmap == null) {
                Log.e(TAG, "Failed to decode bitmap from URI")
                return null
            }

            // ✅ Handle EXIF orientation from URI
            val exif = try {
                val exifStream = context.contentResolver.openInputStream(uri)
                ExifInterface(exifStream!!)
            } catch (e: Exception) {
                Log.w(TAG, "Could not read EXIF data from URI", e)
                null
            }

            val orientation = exif?.getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            ) ?: ExifInterface.ORIENTATION_NORMAL

            val rotated = when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> rotateBitmap(bitmap, 90f)
                ExifInterface.ORIENTATION_ROTATE_180 -> rotateBitmap(bitmap, 180f)
                ExifInterface.ORIENTATION_ROTATE_270 -> rotateBitmap(bitmap, 270f)
                // ✅ FIXED: Named parameters for boolean arguments
                ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> flipBitmap(bitmap, horizontal = true, vertical = false)
                ExifInterface.ORIENTATION_FLIP_VERTICAL -> flipBitmap(bitmap, horizontal = false, vertical = true)
                else -> bitmap
            }

            if (rotated != bitmap) {
                bitmap.recycle()
            }

            Log.d(TAG, "✅ Loaded bitmap with EXIF orientation: $orientation")
            rotated

        } catch (e: Exception) {
            Log.e(TAG, "Error loading bitmap from URI", e)
            null
        }
    }

    private fun handleExifRotation(imagePath: String, bitmap: Bitmap): Bitmap {
        return try {
            val exif = ExifInterface(imagePath)
            val orientation = exif.getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )

            val rotation = when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }

            if (rotation == 0f) {
                bitmap
            } else {
                rotateBitmap(bitmap, rotation)
            }
        } catch (e: IOException) {
            Log.w(TAG, "Could not read EXIF data", e)
            bitmap
        }
    }

    // ✅ Rotate bitmap
    private fun rotateBitmap(bitmap: Bitmap, degrees: Float): Bitmap {
        val matrix = Matrix()
        matrix.postRotate(degrees)
        val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        if (rotated != bitmap) {
            bitmap.recycle()
        }
        return rotated
    }

    // ✅ Flip bitmap
    private fun flipBitmap(bitmap: Bitmap, horizontal: Boolean, vertical: Boolean): Bitmap {
        val matrix = Matrix()
        matrix.postScale(if (horizontal) -1f else 1f, if (vertical) -1f else 1f)
        val flipped = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        if (flipped != bitmap) {
            bitmap.recycle()
        }
        return flipped
    }

    // ✅ Enhanced image processing for better detection
    fun enhanceImageForDetection(bitmap: Bitmap): Bitmap {
        try {
            val width = bitmap.width
            val height = bitmap.height

            // ✅ FIXED: Using KTX extension function createBitmap()
            val result = createBitmap(width, height, Bitmap.Config.ARGB_8888)

            // Get pixel data
            val pixels = IntArray(width * height)
            bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

            // Calculate histogram for each channel
            val histR = IntArray(256)
            val histG = IntArray(256)
            val histB = IntArray(256)

            for (pixel in pixels) {
                val r = (pixel shr 16) and 0xFF
                val g = (pixel shr 8) and 0xFF
                val b = pixel and 0xFF
                histR[r]++
                histG[g]++
                histB[b]++
            }

            // ✅ Contrast stretching (histogram normalization)
            val rMin = histR.indexOfFirst { it > 0 }
            val rMax = histR.indexOfLast { it > 0 }
            val gMin = histG.indexOfFirst { it > 0 }
            val gMax = histG.indexOfLast { it > 0 }
            val bMin = histB.indexOfFirst { it > 0 }
            val bMax = histB.indexOfLast { it > 0 }

            val rRange = if (rMax > rMin) rMax - rMin else 1
            val gRange = if (gMax > gMin) gMax - gMin else 1
            val bRange = if (bMax > bMin) bMax - bMin else 1

            // Apply contrast enhancement
            val enhanced = IntArray(width * height)
            for (i in pixels.indices) {
                val pixel = pixels[i]
                val alpha = (pixel shr 24) and 0xFF
                val r = ((((pixel shr 16) and 0xFF) - rMin) * 255) / rRange
                val g = ((((pixel shr 8) and 0xFF) - gMin) * 255) / gRange
                val b = (((pixel and 0xFF) - bMin) * 255) / bRange

                val clampedR = r.coerceIn(0, 255)
                val clampedG = g.coerceIn(0, 255)
                val clampedB = b.coerceIn(0, 255)

                enhanced[i] = (alpha shl 24) or (clampedR shl 16) or (clampedG shl 8) or clampedB
            }

            result.setPixels(enhanced, 0, width, 0, 0, width, height)

            // ✅ Apply sharpening filter
            val sharpened = applySharpeningFilter(result)
            result.recycle()

            Log.d(TAG, "✅ Image enhancement complete")
            return sharpened

        } catch (e: Exception) {
            Log.e(TAG, "Error enhancing image, returning original", e)
            return bitmap
        }
    }

    // ✅ Sharpening filter for edge enhancement
    private fun applySharpeningFilter(bitmap: Bitmap): Bitmap {
        val width = bitmap.width
        val height = bitmap.height

        // ✅ FIXED: Using KTX extension function createBitmap()
        val result = createBitmap(width, height, Bitmap.Config.ARGB_8888)

        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        // Sharpening kernel
        val kernel = arrayOf(
            intArrayOf(0, -1, 0),
            intArrayOf(-1, 5, -1),
            intArrayOf(0, -1, 0)
        )

        val output = IntArray(width * height)

        for (y in 1 until height - 1) {
            for (x in 1 until width - 1) {
                var sumR = 0
                var sumG = 0
                var sumB = 0

                for (ky in -1..1) {
                    for (kx in -1..1) {
                        val pixel = pixels[(y + ky) * width + (x + kx)]
                        val weight = kernel[ky + 1][kx + 1]

                        sumR += ((pixel shr 16) and 0xFF) * weight
                        sumG += ((pixel shr 8) and 0xFF) * weight
                        sumB += (pixel and 0xFF) * weight
                    }
                }

                val alpha = (pixels[y * width + x] shr 24) and 0xFF
                val r = sumR.coerceIn(0, 255)
                val g = sumG.coerceIn(0, 255)
                val b = sumB.coerceIn(0, 255)

                output[y * width + x] = (alpha shl 24) or (r shl 16) or (g shl 8) or b
            }
        }

        // Copy edges from original
        for (x in 0 until width) {
            output[x] = pixels[x]  // Top edge
            output[(height - 1) * width + x] = pixels[(height - 1) * width + x]  // Bottom edge
        }
        for (y in 0 until height) {
            output[y * width] = pixels[y * width]  // Left edge
            output[y * width + width - 1] = pixels[y * width + width - 1]  // Right edge
        }

        result.setPixels(output, 0, width, 0, 0, width, height)
        return result
    }

    fun saveBitmapToFile(bitmap: Bitmap, fileName: String): String? {
        return try {
            val filesDir = context.getExternalFilesDir(null)
            val file = File(filesDir, fileName)
            FileOutputStream(file).use { fos ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, fos)
            }

            Log.d(TAG, "✅ Bitmap saved to: ${file.absolutePath}")
            file.absolutePath
        } catch (e: IOException) {
            Log.e(TAG, "Error saving bitmap", e)
            null
        }
    }

    fun generateFileName(prefix: String = "image"): String {
        val timestamp = System.currentTimeMillis()
        return "${prefix}_$timestamp.jpg"
    }
}
