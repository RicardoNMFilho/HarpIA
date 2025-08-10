package com.example.harpia.util

import android.content.Context
import com.example.harpia.ml.TFLiteModelRunner
import com.example.harpia.ml.TFLiteDevice
import com.example.harpia.ml.PyTorchModelRunner
import com.example.harpia.ml.MindSporeModelRunner

object ModelInferenceHelper {
    fun runInference(
        context: Context,
        modelType: String,
        device: String,
        modelPath: String,
        input: FloatArray
    ): FloatArray {
        return when (modelType) {
            "TFLite" -> {
                val runner = TFLiteModelRunner(
                    context,
                    if (device == "GPU") TFLiteDevice.GPU else TFLiteDevice.CPU
                )
                try {
                    runner.loadModel(modelPath)
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
                runner.runInference(input)
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
