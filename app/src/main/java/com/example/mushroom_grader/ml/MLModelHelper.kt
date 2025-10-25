package com.example.mushroom_grader.ml

import android.content.Context
import org.tensorflow.lite.Interpreter
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import java.io.FileInputStream

class MLModelHelper(private val context: Context) {
    private var interpreter: Interpreter? = null
    private val modelName = "yolov8_mushroom_classifier.tflite"

    init {
        setupInterpreter()
    }

    private fun setupInterpreter() {
        try {
            val buffer = loadModelFile()
            val options = Interpreter.Options()
            interpreter = Interpreter(buffer, options)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun loadModelFile(): MappedByteBuffer {
        val assetFileDescriptor = context.assets.openFd(modelName)
        val inputStream = FileInputStream(assetFileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        return fileChannel.map(
            FileChannel.MapMode.READ_ONLY,
            assetFileDescriptor.startOffset,
            assetFileDescriptor.declaredLength
        )
    }

    fun classifyImage(inputArray: Array<Array<Array<FloatArray>>>): FloatArray {
        val output = Array(1) { FloatArray(1000) } // Adjust based on your model
        interpreter?.run(inputArray, output)
        return output[0]
    }

    fun close() {
        interpreter?.close()
    }
}