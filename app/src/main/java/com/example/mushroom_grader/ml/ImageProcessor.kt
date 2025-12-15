package com.example.mushroom_grader.ml

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.util.Log
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/**
 * ✅ TRAINING-CONSISTENT IMAGE PROCESSOR
 * Only handles loading, rotation (EXIF), and lighting analysis
 * No enhancement - model handles variations from training augmentations
 */
class ImageProcessor(private val context: Context) {

    companion object {
        private const val TAG = "ImageProcessor"
        private const val JPEG_QUALITY = 90

        /**
         * ✅ TRAINING-CONSISTENT PREPROCESSING
         * Loads image and handles EXIF rotation only
         * Resize and normalization happen in MLModelHelper.preprocessImage()
         */
        fun processImage(imagePath: String): Bitmap {
            Log.d(TAG, "Processing image from path: $imagePath")

            val bitmap = loadBitmapFromFile(imagePath)
                ?: throw IllegalArgumentException("Could not load image from path: $imagePath")

            // Handle EXIF rotation only - no enhancement
            val rotatedBitmap = handleExifRotation(imagePath, bitmap)

            Log.d(TAG, "Image loaded and rotated successfully: ${rotatedBitmap.width}x${rotatedBitmap.height}")

            if (rotatedBitmap != bitmap) {
                bitmap.recycle()
            }

            return rotatedBitmap
        }

        fun loadBitmapFromFile(filePath: String): Bitmap? {
            return try {
                BitmapFactory.decodeFile(filePath)
            } catch (e: Exception) {
                Log.e(TAG, "Error loading bitmap from file", e)
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

        private fun rotateBitmap(bitmap: Bitmap, degrees: Float): Bitmap {
            val matrix = Matrix()
            matrix.postRotate(degrees)
            val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            if (rotated != bitmap) {
                bitmap.recycle()
            }
            return rotated
        }

        /**
         * Analyze lighting quality for user feedback ONLY
         * Does NOT modify the image - just provides information
         */
        fun analyzeLightingQuality(bitmap: Bitmap): LightingQuality {
            val pixels = IntArray(bitmap.width * bitmap.height)
            bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)

            var totalBrightness = 0.0
            for (pixel in pixels) {
                val r = (pixel shr 16) and 0xFF
                val g = (pixel shr 8) and 0xFF
                val b = pixel and 0xFF
                totalBrightness += (0.299 * r + 0.587 * g + 0.114 * b)
            }
            val avgBrightness = totalBrightness / pixels.size

            Log.d(TAG, "📊 Average brightness: $avgBrightness")

            return when {
                avgBrightness < 50 -> LightingQuality.TOO_DARK
                avgBrightness < 80 -> LightingQuality.SLIGHTLY_DARK
                avgBrightness > 200 -> LightingQuality.TOO_BRIGHT
                avgBrightness > 170 -> LightingQuality.SLIGHTLY_BRIGHT
                else -> LightingQuality.GOOD
            }
        }

        enum class LightingQuality {
            TOO_DARK,
            SLIGHTLY_DARK,
            GOOD,
            SLIGHTLY_BRIGHT,
            TOO_BRIGHT
        }
    }

    // Instance methods that need context

    fun loadBitmapFromUri(uri: Uri): Bitmap? {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri)
            val bitmap = BitmapFactory.decodeStream(inputStream)
            inputStream?.close()

            if (bitmap == null) {
                Log.e(TAG, "Failed to decode bitmap from URI")
                return null
            }

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

    private fun rotateBitmap(bitmap: Bitmap, degrees: Float): Bitmap {
        val matrix = Matrix()
        matrix.postRotate(degrees)
        val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        if (rotated != bitmap) {
            bitmap.recycle()
        }
        return rotated
    }

    fun saveBitmapToFile(bitmap: Bitmap, fileName: String = "image_${System.currentTimeMillis()}.jpg"): String? {
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
        return "${prefix}_${timestamp}.jpg"
    }
}
