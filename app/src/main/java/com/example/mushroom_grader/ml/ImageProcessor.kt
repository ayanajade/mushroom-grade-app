package com.example.mushroom_grader.ml

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import android.util.Log

/**
 * Helper class for image processing operations
 */
class ImageProcessor(private val context: Context) {

    companion object {
        private const val TAG = "ImageProcessor"
        private const val MAX_IMAGE_DIMENSION = 2048
        private const val JPEG_QUALITY = 90
    }

    /**
     * Main method to process image for ML model
     * Loads, resizes to 224x224, and returns bitmap ready for classification
     */
    fun processImage(imagePath: String): Bitmap {
        // Load bitmap from file
        val bitmap = loadBitmapFromFile(imagePath)
            ?: throw IllegalArgumentException("Could not load image from path: $imagePath")

        // Resize to 224x224 for EfficientNet model
        val resized = Bitmap.createScaledBitmap(bitmap, 224, 224, true)

        // Clean up original if different
        if (resized != bitmap) {
            bitmap.recycle()
        }

        Log.d(TAG, "Processed image: 224x224")
        return resized
    }

    /**
     * Load bitmap from URI with proper orientation handling
     */
    fun loadBitmapFromUri(uri: Uri): Bitmap? {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri)
            val bitmap = BitmapFactory.decodeStream(inputStream)
            inputStream?.close()

            // Fix orientation
            val correctedBitmap = fixOrientation(uri, bitmap)

            // Resize if too large
            resizeIfNeeded(correctedBitmap)

        } catch (e: Exception) {
            Log.e(TAG, "Error loading bitmap from URI", e)
            null
        }
    }

    /**
     * Load bitmap from file path
     */
    fun loadBitmapFromFile(filePath: String): Bitmap? {
        return try {
            val bitmap = BitmapFactory.decodeFile(filePath)
            val correctedBitmap = fixOrientation(filePath, bitmap)
            resizeIfNeeded(correctedBitmap)
        } catch (e: Exception) {
            Log.e(TAG, "Error loading bitmap from file", e)
            null
        }
    }

    /**
     * Fix image orientation based on EXIF data
     */
    private fun fixOrientation(uri: Uri, bitmap: Bitmap): Bitmap {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri)
            val exif = ExifInterface(inputStream!!)
            val orientation = exif.getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )
            inputStream.close()

            rotateBitmap(bitmap, orientation)
        } catch (e: Exception) {
            Log.w(TAG, "Could not fix orientation", e)
            bitmap
        }
    }

    /**
     * Fix image orientation from file path
     */
    private fun fixOrientation(filePath: String, bitmap: Bitmap): Bitmap {
        return try {
            val exif = ExifInterface(filePath)
            val orientation = exif.getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )
            rotateBitmap(bitmap, orientation)
        } catch (e: Exception) {
            Log.w(TAG, "Could not fix orientation", e)
            bitmap
        }
    }

    /**
     * Rotate bitmap based on EXIF orientation
     */
    private fun rotateBitmap(bitmap: Bitmap, orientation: Int): Bitmap {
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            else -> return bitmap
        }

        return try {
            val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            if (rotated != bitmap) {
                bitmap.recycle()
            }
            rotated
        } catch (e: Exception) {
            Log.e(TAG, "Error rotating bitmap", e)
            bitmap
        }
    }

    /**
     * Resize bitmap if dimensions exceed maximum
     */
    private fun resizeIfNeeded(bitmap: Bitmap): Bitmap {
        val width = bitmap.width
        val height = bitmap.height

        if (width <= MAX_IMAGE_DIMENSION && height <= MAX_IMAGE_DIMENSION) {
            return bitmap
        }

        val scale = if (width > height) {
            MAX_IMAGE_DIMENSION.toFloat() / width
        } else {
            MAX_IMAGE_DIMENSION.toFloat() / height
        }

        val newWidth = (width * scale).toInt()
        val newHeight = (height * scale).toInt()

        Log.d(TAG, "Resizing image from ${width}x${height} to ${newWidth}x${newHeight}")

        return try {
            val resized = Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
            if (resized != bitmap) {
                bitmap.recycle()
            }
            resized
        } catch (e: Exception) {
            Log.e(TAG, "Error resizing bitmap", e)
            bitmap
        }
    }

    /**
     * Save bitmap to file
     */
    fun saveBitmapToFile(bitmap: Bitmap, fileName: String): String? {
        return try {
            val directory = File(context.filesDir, "mushroom_images")
            if (!directory.exists()) {
                directory.mkdirs()
            }

            val file = File(directory, fileName)
            val outputStream = FileOutputStream(file)
            bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, outputStream)
            outputStream.flush()
            outputStream.close()

            Log.d(TAG, "Image saved to: ${file.absolutePath}")
            file.absolutePath

        } catch (e: IOException) {
            Log.e(TAG, "Error saving bitmap", e)
            null
        }
    }

    /**
     * Generate unique filename for image
     */
    fun generateFileName(prefix: String = "mushroom"): String {
        val timestamp = System.currentTimeMillis()
        return "${prefix}_${timestamp}.jpg"
    }

    /**
     * Delete image file
     */
    fun deleteImageFile(filePath: String): Boolean {
        return try {
            val file = File(filePath)
            if (file.exists()) {
                file.delete()
            } else {
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting file", e)
            false
        }
    }

    /**
     * Crop bitmap to square (center crop)
     */
    fun cropToSquare(bitmap: Bitmap): Bitmap {
        val width = bitmap.width
        val height = bitmap.height

        if (width == height) {
            return bitmap
        }

        val size = minOf(width, height)
        val x = (width - size) / 2
        val y = (height - size) / 2

        return try {
            val cropped = Bitmap.createBitmap(bitmap, x, y, size, size)
            if (cropped != bitmap) {
                bitmap.recycle()
            }
            cropped
        } catch (e: Exception) {
            Log.e(TAG, "Error cropping bitmap", e)
            bitmap
        }
    }

    /**
     * Apply basic image enhancement
     */
    fun enhanceImage(bitmap: Bitmap): Bitmap {
        // For future enhancement: brightness, contrast adjustments
        return bitmap
    }
}
