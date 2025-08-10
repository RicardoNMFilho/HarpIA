package com.example.harpia.ml

import org.pytorch.Module
import org.pytorch.IValue
import org.pytorch.Tensor

class PyTorchModelRunner : ModelRunner {
    private var module: Module? = null

    override fun loadModel(modelPath: String) {
        module = Module.load(modelPath)
    }

    override fun runInference(input: FloatArray): FloatArray {
        val inputTensor = Tensor.fromBlob(input, longArrayOf(1, 3, 224, 224))
        val outputTensor = module?.forward(IValue.from(inputTensor))?.toTensor()
        return outputTensor?.dataAsFloatArray ?: floatArrayOf()
    }
}
