package com.example.harpia.ml


import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.gpu.GpuDelegate
import android.content.Context
import java.io.File
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel


class TFLiteModelRunner(
    private val context: Context,
    private val device: TFLiteDevice = TFLiteDevice.CPU
) : ModelRunner {
    private var interpreter: Interpreter? = null
    private var gpuDelegate: GpuDelegate? = null
    private var loadedModelPath: String? = null

    fun close() {
        interpreter?.close()
        interpreter = null
        gpuDelegate?.close()
        gpuDelegate = null
        loadedModelPath = null
    }

    override fun loadModel(modelPath: String) {
        // Evita recriar se já carregado com o mesmo modelo
        if (interpreter != null && loadedModelPath == modelPath) {
            return
        }

        // Se já houver um intérprete com outro modelo, feche antes
        if (interpreter != null && loadedModelPath != modelPath) {
            close()
        }

        val modelBuffer = loadModelFile(modelPath)
        val options = Interpreter.Options()

        if (device == TFLiteDevice.GPU) {
            try {
                if (gpuDelegate == null) {
                    gpuDelegate = GpuDelegate()
                }
                options.addDelegate(gpuDelegate)
            } catch (e: Exception) {
                // Fallback para CPU se delegate falhar
                android.util.Log.e("TFLiteModelRunner", "Erro ao inicializar delegate GPU", e)
            }
        }

    interpreter = Interpreter(modelBuffer, options)
        loadedModelPath = modelPath
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
