package com.example.harpia.ml

import org.pytorch.Module
import org.pytorch.IValue
import org.pytorch.Tensor

import org.pytorch.Device

class PyTorchModelRunner(private val useVulkan: Boolean = false) : ModelRunner {
    private var module: Module? = null

    override fun loadModel(modelPath: String) {
        // PyTorch Android 2.0.0+ permite seleção explícita do backend
        module = if (useVulkan) {
            Module.load(modelPath, emptyMap(), Device.VULKAN)
        } else {
            Module.load(modelPath, emptyMap(), Device.CPU)
        }
    }

    override fun runInference(input: FloatArray): FloatArray {
        val inputTensor = Tensor.fromBlob(input, longArrayOf(1, 3, 224, 224))
        val outputTensor = module?.forward(IValue.from(inputTensor))?.toTensor()
        return outputTensor?.dataAsFloatArray ?: floatArrayOf()
    }
}
