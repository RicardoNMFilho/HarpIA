package com.example.harpia.ml

import android.content.Context
import org.tensorflow.lite.gpu.GpuDelegate
import org.tensorflow.lite.Interpreter

object TFLiteGpuDelegateHelper {
    private var gpuDelegate: GpuDelegate? = null

    fun createInterpreterWithGpu(modelPath: String): Interpreter {
        try {
            gpuDelegate = GpuDelegate()
            val options = Interpreter.Options().addDelegate(gpuDelegate)
            return Interpreter(java.io.File(modelPath), options)
        } catch (e: Exception) {
            gpuDelegate?.close()
            gpuDelegate = null
            throw RuntimeException("Falha ao inicializar o delegate GPU: ${e.message}", e)
        }
    }

    fun closeGpuDelegate() {
        gpuDelegate?.close()
        gpuDelegate = null
    }
}
