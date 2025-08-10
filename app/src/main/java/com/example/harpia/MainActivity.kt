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

            // Coleta de energia
            val energyMonitor = EnergyMonitor(this)
            energyMonitor.start()
            val startTime = System.nanoTime()
            val result: FloatArray = try {
                ModelInferenceHelper.runInference(
                    this,
                    selectedModelType,
                    selectedDevice,
                    modelPath,
                    input
                )
            } catch (e: Exception) {
                energyMonitor.stopAndGetEnergy()
                findViewById<android.widget.TextView>(R.id.textViewInferenceResult).text = "Erro ao executar modelo: ${e.message}"
                return@registerForActivityResult
            }
            val (energy, _) = energyMonitor.stopAndGetEnergy()
            val timeElapsed = (System.nanoTime() - startTime) / 1_000_000 // ms

            // Exibir resultado nos TextViews
            findViewById<android.widget.TextView>(R.id.textViewInferenceResult).text = "Resultado: ${result.joinToString(", ").take(100)}"
            findViewById<android.widget.TextView>(R.id.textViewInferenceTime).text = "Tempo de inferência: $timeElapsed ms"
            findViewById<android.widget.TextView>(R.id.textViewEnergy).text = if (energy > 0) {
                "Consumo estimado: %.4f Joules".format(energy)
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
                var totalTime = 0L
                var totalEnergy = 0.0
                val n = imageFiles.size
                val energyMonitor = EnergyMonitor(this)
                energyMonitor.start()
                for ((idx, fileName) in imageFiles.withIndex()) {
                    val input = loadAndPreprocessImageFromAssets("imagenetv2/$fileName")
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
                    totalTime += elapsed
                    if ((idx + 1) % 100 == 0) {
                        runOnUiThread {
                            findViewById<android.widget.TextView>(R.id.textViewInferenceResult).text = "Processadas $idx/$n imagens..."
                        }
                    }
                }
                val (energy, duration) = energyMonitor.stopAndGetEnergy()
                totalEnergy = energy
                val avgTime = totalTime.toDouble() / n
                val avgEnergy = if (n > 0) totalEnergy / n else 0.0
                runOnUiThread {
                    findViewById<android.widget.TextView>(R.id.textViewInferenceResult).text = "Benchmark concluído! Imagens: $n"
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

    // Função utilitária para carregar e preprocessar imagem do assets
    private fun loadAndPreprocessImageFromAssets(assetPath: String): FloatArray {
        val inputStream = assets.open(assetPath)
        val bitmap = BitmapFactory.decodeStream(inputStream)
        inputStream.close()
        val resized = Bitmap.createScaledBitmap(bitmap, 224, 224, true)
        val floatValues = FloatArray(1 * 224 * 224 * 3)
        var idx = 0
        for (y in 0 until 224) {
            for (x in 0 until 224) {
                val pixel = resized.getPixel(x, y)
                floatValues[idx++] = ((pixel shr 16) and 0xFF) / 255.0f // R
                floatValues[idx++] = ((pixel shr 8) and 0xFF) / 255.0f  // G
                floatValues[idx++] = (pixel and 0xFF) / 255.0f          // B
            }
        }
        return floatValues
    }

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
