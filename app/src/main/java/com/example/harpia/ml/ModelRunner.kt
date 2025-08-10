package com.example.harpia.ml

interface ModelRunner {
    fun loadModel(modelPath: String)
    fun runInference(input: FloatArray): FloatArray
}
