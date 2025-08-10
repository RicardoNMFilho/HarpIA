package com.example.harpia.ml

import org.tensorflow.lite.Interpreter
import java.nio.ByteBuffer

class TFLiteModelRunner(private val device: TFLiteDevice = TFLiteDevice.CPU) : ModelRunner {
    private var interpreter: Interpreter? = null

    fun close() {
        interpreter?.close()
        interpreter = null
        if (device == TFLiteDevice.GPU) {
            TFLiteGpuDelegateHelper.closeGpuDelegate()
        }
    }

    override fun loadModel(modelPath: String) {
        interpreter = if (device == TFLiteDevice.GPU) {
            TFLiteGpuDelegateHelper.createInterpreterWithGpu(modelPath)
        } else {
            Interpreter(java.io.File(modelPath))
        }
    }

    override fun runInference(input: FloatArray): FloatArray {
        // Espera input shape [1,224,224,3]
        if (input.size != 1 * 224 * 224 * 3) {
            throw IllegalArgumentException("Input deve ter shape [1,224,224,3] (total 150528 elementos)")
        }
        // Converter FloatArray achatado para [1][224][224][3]
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
        val output = Array(1) { FloatArray(1000) } // Exemplo para classificação
        interpreter?.run(reshapedInput, output)
        return output[0]
    }
}
