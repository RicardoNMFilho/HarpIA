package com.example.harpia.util

import android.content.Context
import com.example.harpia.ml.TFLiteModelRunner
import com.example.harpia.ml.TFLiteDevice
import com.example.harpia.ml.PyTorchModelRunner
import com.example.harpia.ml.MindSporeModelRunner

object ModelInferenceHelper {
    // Cache de runners para evitar recriação a cada inferência
    private var tfliteRunner: TFLiteModelRunner? = null
    private var tfliteDevice: TFLiteDevice? = null
    private var tfliteModelPath: String? = null

    fun runInference(
        context: Context,
        modelType: String,
        device: String,
        modelPath: String,
        input: FloatArray
    ): FloatArray {
        return when (modelType) {
            "TFLite" -> {
                val desiredDevice = if (device == "GPU") TFLiteDevice.GPU else TFLiteDevice.CPU

                // Reusar runner se config/modelo não mudou
                val needsNewRunner = tfliteRunner == null ||
                    tfliteDevice != desiredDevice ||
                    tfliteModelPath != modelPath

                if (needsNewRunner) {
                    // Fecha runner anterior, se houver
                    tfliteRunner?.close()

                    val newRunner = TFLiteModelRunner(context, desiredDevice)
                    try {
                        newRunner.loadModel(modelPath)
                    } catch (e: Exception) {
                        android.util.Log.e("TFLiteModelRunner", "Erro ao inicializar delegate GPU", e)
                        val msg = "Falha ao inicializar na GPU. O modelo foi carregado na CPU.\n" +
                            "Tipo: ${e::class.java.simpleName}\nMotivo: ${e.message}"
                        android.widget.Toast.makeText(
                            context,
                            msg,
                            android.widget.Toast.LENGTH_LONG
                        ).show()
                    }
                    tfliteRunner = newRunner
                    tfliteDevice = desiredDevice
                    tfliteModelPath = modelPath
                }

                tfliteRunner!!.runInference(input)
            }
            "PyTorch" -> {
                val useVulkan = device == "GPU"
                val runner = PyTorchModelRunner(useVulkan)
                runner.loadModel(modelPath)
                runner.runInference(input)
            }
            "MindSpore" -> {
                val runner = MindSporeModelRunner(context)
                runner.loadModel(modelPath)
                runner.runInference(input)
            }
            else -> floatArrayOf()
        }
    }
}
