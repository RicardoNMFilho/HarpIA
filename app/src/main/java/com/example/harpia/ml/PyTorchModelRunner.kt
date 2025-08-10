package com.example.harpia.ml

import org.pytorch.Module
import org.pytorch.IValue
import org.pytorch.Tensor

import org.pytorch.Device

class PyTorchModelRunner(private val useVulkan: Boolean = false) : ModelRunner {
    private var module: Module? = null
    private var loadedModelPath: String? = null

    fun close() {
        module = null
        loadedModelPath = null
    }

    fun isLoaded(): Boolean = module != null
    fun backendLabel(): String = if (useVulkan) "VULKAN" else "CPU"

    override fun loadModel(modelPath: String) {
        // Reusar módulo se já carregado com o mesmo caminho
        if (module != null && loadedModelPath == modelPath) {
            return
        }

        // Se for um modelo diferente, descarrega o anterior
        if (module != null && loadedModelPath != modelPath) {
            close()
        }

        // PyTorch Android 2.x: seleção explícita do backend
        module = if (useVulkan) {
            Module.load(modelPath, emptyMap(), Device.VULKAN)
        } else {
            Module.load(modelPath, emptyMap(), Device.CPU)
        }
        loadedModelPath = modelPath
    }

    override fun runInference(input: FloatArray): FloatArray {
        val m = module ?: throw IllegalStateException("Modelo não carregado. Chame loadModel antes de inferir.")
        val inputTensor = Tensor.fromBlob(input, longArrayOf(1, 3, 224, 224))
        val outputTensor = m.forward(IValue.from(inputTensor))?.toTensor()
        return outputTensor?.dataAsFloatArray ?: floatArrayOf()
    }
}
