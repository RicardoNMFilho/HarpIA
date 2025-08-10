package com.example.harpia.ml

import android.content.Context
import com.mindspore.ModelParallelRunner
import com.mindspore.MSTensor
import com.mindspore.config.MSContext
import com.mindspore.config.DeviceType
import com.mindspore.config.RunnerConfig

class MindSporeModelRunner(private val context: Context) : ModelRunner {
	private var runner: ModelParallelRunner? = null

	override fun loadModel(modelPath: String) {
		try {
			val msContext = MSContext()
			// Adiciona device CPU (pode trocar para DeviceType.DT_GPU se quiser GPU)
			msContext.addDeviceInfo(DeviceType.DT_CPU, false, 0)

			val runnerConfig = RunnerConfig()
			runnerConfig.init(msContext)

			runner = ModelParallelRunner()
			val ok = runner!!.init(modelPath, runnerConfig)
			if (!ok) throw RuntimeException("Erro ao inicializar o modelo MindSpore Lite")
		} catch (e: Exception) {
			android.util.Log.e("MindSporeModelRunner", "Erro ao carregar modelo MindSpore", e)
			throw RuntimeException("Exceção ao carregar modelo MindSpore: ${e.message}", e)
		}
	}

	override fun runInference(input: FloatArray): FloatArray {
		val inputTensors = runner!!.inputs
		val inputTensor = inputTensors[0]
		inputTensor.setData(input)

		val outputs = mutableListOf<MSTensor>()
		val ok = runner!!.predict(listOf(inputTensor), outputs)
		if (!ok) throw RuntimeException("Erro na inferência MindSpore Lite")

		val outputTensor = outputs[0]
		return outputTensor.data as FloatArray
	}

	fun close() {
		runner?.free()
		runner = null
	}
}
