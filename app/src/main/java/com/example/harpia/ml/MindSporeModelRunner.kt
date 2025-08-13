package com.example.harpia.ml

// MindSpore removido do projeto. Este arquivo é um placeholder para evitar referências antigas.
// Não contém nenhuma implementação e não depende de bibliotecas MindSpore.

@Deprecated("MindSpore foi removido; não usar.")
class MindSporeModelRunner : ModelRunner {
	override fun loadModel(modelPath: String) {
		throw UnsupportedOperationException("MindSpore removido do projeto")
	}
	override fun runInference(input: FloatArray): FloatArray {
		throw UnsupportedOperationException("MindSpore removido do projeto")
	}
	fun close() { /* no-op */ }
}
