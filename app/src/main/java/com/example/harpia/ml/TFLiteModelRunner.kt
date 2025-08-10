package com.example.harpia.ml


import org.tensorflow.lite.Interpreter
import android.content.Context
import java.io.File
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel


class TFLiteModelRunner(
    private val context: Context,
    private val device: TFLiteDevice = TFLiteDevice.CPU
) : ModelRunner {
    private var interpreter: Interpreter? = null

    fun close() {
        interpreter?.close()
        interpreter = null
    }

    override fun loadModel(modelPath: String) {
        val modelBuffer = loadModelFile(modelPath)
        val options = Interpreter.Options()
        // LiteRT não expõe delegate GPU diretamente, apenas CPU por enquanto
        interpreter = Interpreter(modelBuffer, options)
    }

    override fun runInference(input: FloatArray): FloatArray {
        if (input.size != 1 * 224 * 224 * 3) {
            throw IllegalArgumentException("Input deve ter shape [1,224,224,3] (total 150528 elementos)")
        }
        val reshapedInput = Array(1) { Array(224) { Array(224) { FloatArray(3) } } }
        var idx = 0
        for (i in 0 until 1) {
            for (j in 0 until 224) {
                for (k in 0 until 224) {
                    for (c in 0 until 3) {
                        reshapedInput[i][j][k][c] = input[idx++]
                    }
                }
            }
        }
        val output = Array(1) { FloatArray(1000) }
        interpreter?.run(reshapedInput, output)
        return output[0]
    }

    private fun loadModelFile(modelPath: String): MappedByteBuffer {
        val file = File(modelPath)
        val inputStream = file.inputStream()
        val fileChannel = inputStream.channel
        val startOffset = 0L
        val declaredLength = file.length()
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
    }
}
