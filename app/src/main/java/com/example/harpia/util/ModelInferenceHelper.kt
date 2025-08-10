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
                    if (device == "GPU") TFLiteDevice.GPU else TFLiteDevice.CPU
                )
                runner.loadModel(modelPath)
                runner.runInference(input)
            }
            "PyTorch" -> {
                val runner = PyTorchModelRunner()
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
