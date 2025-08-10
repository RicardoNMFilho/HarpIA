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

    private var pytorchRunner: PyTorchModelRunner? = null
    private var pytorchUseVulkan: Boolean? = null
    private var pytorchModelPath: String? = null

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

                val needsNewRunner = pytorchRunner == null ||
                    pytorchUseVulkan != useVulkan ||
                    pytorchModelPath != modelPath

                if (needsNewRunner) {
                    // Fecha runner anterior (garbage collect), se houver
                    pytorchRunner?.close()

                    // Cria novo runner e tenta carregar com backend solicitado
                    var actualUseVulkan = useVulkan
                    var newRunner = PyTorchModelRunner(actualUseVulkan)
                    try {
                        newRunner.loadModel(modelPath)
                    } catch (e: Exception) {
                        android.util.Log.e("PyTorchModelRunner", "Erro ao carregar modelo", e)
                        val msg = "Falha ao carregar PyTorch (${if (actualUseVulkan) "VULKAN" else "CPU"}).\n" +
                            "Tipo: ${e::class.java.simpleName}\nMotivo: ${e.message}"
                        android.widget.Toast.makeText(
                            context,
                            msg,
                            android.widget.Toast.LENGTH_LONG
                        ).show()
                        // Tentar fallback para CPU se falhou em Vulkan
                        if (actualUseVulkan) {
                            actualUseVulkan = false
                            newRunner = PyTorchModelRunner(false)
                            newRunner.loadModel(modelPath)
                        } else {
                            throw e
                        }
                    }
                    // Atribui sempre o novo runner e flags
                    pytorchRunner = newRunner
                    pytorchUseVulkan = actualUseVulkan
                    pytorchModelPath = modelPath
                }
                // Garante que o modelo esteja carregado se runner existir mas não tiver módulo (edge case)
                if (pytorchRunner != null && !pytorchRunner!!.isLoaded()) pytorchRunner!!.loadModel(modelPath)


                pytorchRunner!!.runInference(input)
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
