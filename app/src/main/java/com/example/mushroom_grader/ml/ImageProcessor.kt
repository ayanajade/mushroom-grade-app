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
        val resized = Bitmap.createScaledBitmap(rotatedBitmap, 256, 256, true)

        Log.d(TAG, "Image processed successfully: ${resized.width}x${resized.height}")

        if (resized != bitmap) {
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

    fun loadBitmapFromUri(uri: Uri): Bitmap? {
        return try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                BitmapFactory.decodeStream(inputStream)
            }
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
                val matrix = Matrix().apply { postRotate(rotation) }
                Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            }
        } catch (e: IOException) {
            Log.w(TAG, "Could not read EXIF data", e)
            bitmap
        }
    }

    fun saveBitmapToFile(bitmap: Bitmap, fileName: String): String? {
        return try {
            val filesDir = context.getExternalFilesDir(null)
            val file = File(filesDir, fileName)

            FileOutputStream(file).use { fos ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, fos)
            }

            Log.d(TAG, "Bitmap saved to: ${file.absolutePath}")
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
