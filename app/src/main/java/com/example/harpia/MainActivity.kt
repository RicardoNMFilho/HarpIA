package com.example.harpia

import java.io.File
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import com.example.harpia.util.EnergyMonitor
import com.example.harpia.util.ModelInferenceHelper
import android.widget.RadioButton
import android.widget.RadioGroup
import android.view.View
import android.widget.AdapterView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity


class MainActivity : AppCompatActivity() {
    private var loadedModelPath: String? = null
    private var loadedModelName: String? = null

    // Novo launcher para selecionar arquivo
    private val loadModelLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            val modelTypeGroup: RadioGroup = findViewById(R.id.radioGroupModelType)
            val deviceGroup: RadioGroup = findViewById(R.id.radioGroupDevice)
            val selectedModelType = when (modelTypeGroup.checkedRadioButtonId) {
                R.id.radioPyTorch -> "PyTorch"
                R.id.radioTFLite -> "TFLite"
                R.id.radioMindSpore -> "MindSpore"
                else -> "Unknown"
            }
            val selectedDevice = when (deviceGroup.checkedRadioButtonId) {
                R.id.radioCPU -> "CPU"
                R.id.radioGPU -> "GPU"
                R.id.radioNNAPI -> "NNAPI"
                else -> "Unknown"
            }
            val modelPath = copyUriToTempFile(uri) ?: run {
                findViewById<android.widget.TextView>(R.id.textViewInferenceResult).text = "Erro ao obter arquivo do modelo."
                return@registerForActivityResult
            }
            loadedModelPath = modelPath // Salva o caminho do modelo carregado
            loadedModelName = uri.lastPathSegment ?: "Modelo desconhecido"

            // Atualiza nome do modelo e dispositivo
            findViewById<android.widget.TextView>(R.id.textViewModelName).text = "Modelo: $loadedModelName"
            findViewById<android.widget.TextView>(R.id.textViewDevice).text = "Dispositivo: $selectedDevice"

            val input = FloatArray(1 * 224 * 224 * 3) // Aqui você deve carregar/preprocessar a imagem real

            // Coleta de energia com amostragem periódica
            val energyMonitor = EnergyMonitor(this)
            val energySamples = mutableListOf<Double>()
            val samplingIntervalMs = 100L
            var sampling = true
            val samplingThread = Thread {
                while (sampling) {
                    energySamples.add(energyMonitor.getPartialEnergy())
                    try { Thread.sleep(samplingIntervalMs) } catch (_: Exception) {}
                }
            }
            energyMonitor.start()
            val startTime = System.nanoTime()
            samplingThread.start()
            val result: FloatArray = try {
                ModelInferenceHelper.runInference(
                    this,
                    selectedModelType,
                    selectedDevice,
                    modelPath,
                    input
                )
            } catch (e: Exception) {
                sampling = false
                samplingThread.join()
                energyMonitor.stopAndGetEnergy()
                findViewById<android.widget.TextView>(R.id.textViewInferenceResult).text = "Erro ao executar modelo: ${e.message}"
                return@registerForActivityResult
            }
            val (energy, _) = energyMonitor.stopAndGetEnergy()
            sampling = false
            samplingThread.join()
            val timeElapsed = (System.nanoTime() - startTime) / 1_000_000 // ms

            // Se não houve amostras (inferência muito rápida), usa só início/fim
            if (energySamples.isEmpty()) {
                energySamples.add(0.0)
                energySamples.add(energy)
            } else {
                // Garante que o último valor seja o final
                energySamples.add(energy)
            }
            // Calcula consumo de cada intervalo
            val consumos = energySamples.zipWithNext { a, b -> b - a }
            val consumoTotal = consumos.sum()
            val avgConsumo = consumoTotal / consumos.size
            val stdDev = if (consumos.size > 1) Math.sqrt(consumos.map { (it - avgConsumo) * (it - avgConsumo) }.sum() / (consumos.size - 1)) else 0.0
            val conf95 = if (consumos.size > 1) 1.96 * stdDev / Math.sqrt(consumos.size.toDouble()) else 0.0

            // Exibir resultado nos TextViews
            findViewById<android.widget.TextView>(R.id.textViewInferenceResult).text = "Resultado: ${result.joinToString(", ").take(100)}\n" +
                "Consumo médio: %.6f J\nDesvio padrão: %.6f J\nIC 95%%: ±%.6f J".format(avgConsumo, stdDev, conf95)
            findViewById<android.widget.TextView>(R.id.textViewInferenceTime).text = "Tempo de inferência: $timeElapsed ms"
            findViewById<android.widget.TextView>(R.id.textViewEnergy).text = if (energy > 0) {
                "Consumo total: %.4f Joules".format(energy)
            } else {
                "Consumo estimado: não disponível"
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val btnLoadModel: android.widget.Button = findViewById(R.id.btnLoadModel)
        btnLoadModel.setOnClickListener {
            loadModelLauncher.launch("*/*")
        }

        val btnBenchmark: android.widget.Button = findViewById(R.id.btnBenchmark)
        btnBenchmark.setOnClickListener {
            runBenchmarkOnImageNetV2()
        }
    }

    private fun runBenchmarkOnImageNetV2() {
        val modelTypeGroup: RadioGroup = findViewById(R.id.radioGroupModelType)
        val deviceGroup: RadioGroup = findViewById(R.id.radioGroupDevice)
        val selectedModelType = when (modelTypeGroup.checkedRadioButtonId) {
            R.id.radioPyTorch -> "PyTorch"
            R.id.radioTFLite -> "TFLite"
            R.id.radioMindSpore -> "MindSpore"
            else -> "Unknown"
        }
        val selectedDevice = when (deviceGroup.checkedRadioButtonId) {
            R.id.radioCPU -> "CPU"
            R.id.radioGPU -> "GPU"
            R.id.radioNNAPI -> "NNAPI"
            else -> "Unknown"
        }
    findViewById<android.widget.TextView>(R.id.textViewInferenceResult).text = "Iniciando benchmark..."

        Thread {
            try {
                val assetManager = assets
                val imageFiles = assetManager.list("imagenetv2")?.filter {
                    it.endsWith(".JPEG") || it.endsWith(".jpeg") || it.endsWith(".jpg") || it.endsWith(".png")
                } ?: emptyList()
                if (imageFiles.isEmpty()) {
                    runOnUiThread { findViewById<android.widget.TextView>(R.id.textViewInferenceResult).text = "Nenhuma imagem encontrada em assets/imagenetv2." }
                    return@Thread
                }
                // Carregar modelo
                val modelPath = loadedModelPath ?: run {
                    runOnUiThread { findViewById<android.widget.TextView>(R.id.textViewInferenceResult).text = "Selecione e carregue um modelo antes do benchmark." }
                    return@Thread
                }
                val times = mutableListOf<Long>()
                val energies = mutableListOf<Double>()
                val n = imageFiles.size
                val energyMonitor = EnergyMonitor(this)
                energyMonitor.start()
                var lastEnergy = energyMonitor.getPartialEnergy()
                for ((idx, fileName) in imageFiles.withIndex()) {
                    val input = com.example.harpia.util.ImageUtils.loadAndPreprocessImage(assets, "imagenetv2/$fileName")
                    val start = System.nanoTime()
                    try {
                        ModelInferenceHelper.runInference(
                            this,
                            selectedModelType,
                            selectedDevice,
                            modelPath,
                            input
                        )
                    } catch (e: Exception) {
                        runOnUiThread {
                            findViewById<android.widget.TextView>(R.id.textViewInferenceResult).text = "Erro na inferência da imagem $fileName: ${e.message}"
                        }
                        return@Thread
                    }
                    val elapsed = (System.nanoTime() - start) / 1_000_000 // ms
                    val currentEnergy = energyMonitor.getPartialEnergy()
                    times.add(elapsed)
                    energies.add(currentEnergy - lastEnergy)
                    lastEnergy = currentEnergy
                    if ((idx + 1) % 100 == 0) {
                        runOnUiThread {
                            findViewById<android.widget.TextView>(R.id.textViewInferenceResult).text = "Processadas $idx/$n imagens..."
                        }
                    }
                }
                val (totalEnergy, duration) = energyMonitor.stopAndGetEnergy()
                // Estatísticas
                val avgTime = times.average()
                val stdDev = if (times.size > 1) Math.sqrt(times.map { (it - avgTime) * (it - avgTime) }.sum() / (times.size - 1)) else 0.0
                val conf95 = if (times.size > 1) 1.96 * stdDev / Math.sqrt(times.size.toDouble()) else 0.0
                val avgEnergy = energies.average()
                val stdDevEnergy = if (energies.size > 1) Math.sqrt(energies.map { (it - avgEnergy) * (it - avgEnergy) }.sum() / (energies.size - 1)) else 0.0
                val conf95Energy = if (energies.size > 1) 1.96 * stdDevEnergy / Math.sqrt(energies.size.toDouble()) else 0.0
                runOnUiThread {
                    findViewById<android.widget.TextView>(R.id.textViewInferenceResult).text = "Benchmark concluído! Imagens: $n\n" +
                        "Tempo médio: %.2f ms\nDesvio padrão: %.2f ms\nIC 95%%: ±%.2f ms\n".format(avgTime, stdDev, conf95) +
                        "Energia média: %.6f J\nDesvio padrão: %.6f J\nIC 95%%: ±%.6f J".format(avgEnergy, stdDevEnergy, conf95Energy)
                    findViewById<android.widget.TextView>(R.id.textViewInferenceTime).text = "Tempo médio: %.2f ms".format(avgTime)
                    findViewById<android.widget.TextView>(R.id.textViewEnergy).text = "Energia média: %.6f Joules".format(avgEnergy)
                }
            } catch (e: Exception) {
                runOnUiThread {
                    findViewById<android.widget.TextView>(R.id.textViewInferenceResult).text = "Erro no benchmark: ${e.message}"
                }
            }
        }.start()
    }

    // ... Função utilitária movida para ImageUtils.kt ...

    // Copia o arquivo selecionado para um arquivo temporário e retorna o caminho
    private fun copyUriToTempFile(uri: Uri): String? {
        return try {
            val inputStream = contentResolver.openInputStream(uri) ?: return null
            val tempFile = File.createTempFile("model", null, cacheDir)
            tempFile.outputStream().use { output ->
                inputStream.copyTo(output)
            }
            tempFile.absolutePath
        } catch (e: Exception) {
            null
        }
    }

    // onActivityResult removido, pois não é mais necessário com ActivityResultLauncher
}
