package com.example.harpia.ml

import com.mindspore.MSTensor
import com.mindspore.ModelParallelRunner
import com.mindspore.config.DeviceType
import com.mindspore.config.MSContext
import com.mindspore.config.RunnerConfig

class MindSporeModelRunner(private val device: MindSporeDevice = MindSporeDevice.CPU) : ModelRunner {
	companion object {
		init {
			try { System.loadLibrary("jpeg") } catch (_: Throwable) {}
			try { System.loadLibrary("turbojpeg") } catch (_: Throwable) {}
			try { System.loadLibrary("minddata-lite") } catch (_: Throwable) {}
			try { System.loadLibrary("mindspore-lite") } catch (_: Throwable) {}
			try { System.loadLibrary("mindspore-lite-jni") } catch (_: Throwable) {}
		}
	}

	private var runner: ModelParallelRunner? = null
	private var loadedModelPath: String? = null

	override fun loadModel(modelPath: String) {
		if (runner != null && loadedModelPath == modelPath) return
		close()
		var target = device
		val msContext = MSContext()
		addDevice(msContext, target)

		var runnerConfig = RunnerConfig()
		runnerConfig.init(msContext)

		var r = ModelParallelRunner()
		var ok = r.init(modelPath, runnerConfig)

		if (!ok && target == MindSporeDevice.GPU) {
			// Fallback para CPU
			try { r.free() } catch (_: Throwable) {}
			r = ModelParallelRunner()
			val msCtx2 = MSContext()
			addDevice(msCtx2, MindSporeDevice.CPU)
			runnerConfig = RunnerConfig()
			runnerConfig.init(msCtx2)
			ok = r.init(modelPath, runnerConfig)
			target = MindSporeDevice.CPU
		}

		if (!ok) throw RuntimeException("Erro ao inicializar MindSpore Lite 1.9")
		runner = r
		loadedModelPath = modelPath
	}

	override fun runInference(input: FloatArray): FloatArray {
		val r = runner ?: throw IllegalStateException("Modelo MindSpore não carregado")
		val inputs = r.inputs
		if (inputs.isEmpty()) throw IllegalStateException("Sem tensores de entrada")
		val inTensor = inputs[0]
		val shape = inTensor.shape
		val isNHWC = shape.size == 4 && shape[0] == 1 && (shape[3] == 3 || shape[3] == 1)
		val isNCHW = shape.size == 4 && shape[0] == 1 && (shape[1] == 3 || shape[1] == 1)
		val data = when {
			isNHWC -> input
			isNCHW -> hwcToChw(input, shape[2], shape[3], shape[1])
			else -> input
		}
		inTensor.setData(data)

		val outputs = mutableListOf<MSTensor>()
		val ok = r.predict(listOf(inTensor), outputs)
		if (!ok || outputs.isEmpty()) throw RuntimeException("Erro na inferência MindSpore Lite")
		val out = outputs[0]
		return out.floatData
	}

	fun close() {
		try { runner?.free() } catch (_: Throwable) {}
		runner = null
		loadedModelPath = null
	}

	private fun addDevice(ctx: MSContext, dev: MindSporeDevice) {
		when (dev) {
			MindSporeDevice.CPU -> ctx.addDeviceInfo(DeviceType.DT_CPU, false, 0)
			MindSporeDevice.GPU -> ctx.addDeviceInfo(DeviceType.DT_GPU, false, 0)
		}
	}

	private fun hwcToChw(src: FloatArray, h: Int, w: Int, c: Int): FloatArray {
		val dst = FloatArray(src.size)
		for (ci in 0 until c) {
			for (yi in 0 until h) {
				for (xi in 0 until w) {
					val srcIdx = (yi * w + xi) * c + ci
					val dstIdx = ci * (h * w) + yi * w + xi
					dst[dstIdx] = src[srcIdx]
				}
			}
		}
		return dst
	}
}
