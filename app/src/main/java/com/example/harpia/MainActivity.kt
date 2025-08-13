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
    private var pickedImagesDirUri: Uri? = null
    private var pickedImageUris: List<Uri> = emptyList()
    @Volatile private var cancelBenchmark: Boolean = false
    private var benchmarkThread: Thread? = null

    // Novo launcher para selecionar arquivo
    private val loadModelLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            val modelTypeGroup: RadioGroup = findViewById(R.id.radioGroupModelType)
            val deviceGroup: RadioGroup = findViewById(R.id.radioGroupDevice)
            val selectedModelType = when (modelTypeGroup.checkedRadioButtonId) {
                R.id.radioPyTorch -> "PyTorch"
                R.id.radioTFLite -> "TFLite"
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

    // Launcher para selecionar diretório de imagens
    private val pickImagesDirLauncher = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
        if (uri != null) {
            pickedImagesDirUri = uri
            // Dar permissão persistente para acessar o diretório
            val flags = (Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
            try {
                contentResolver.takePersistableUriPermission(uri, flags)
            } catch (_: Exception) {}
            findViewById<android.widget.TextView>(R.id.textViewInferenceResult).text = "Diretório selecionado: ${uri}"
        }
    }

    // Launcher para selecionar múltiplas imagens (arquivos)
    private val pickImagesLauncher = registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            pickedImageUris = uris
            // Concede permissão de leitura para cada uri
            uris.forEach { u ->
                try { contentResolver.takePersistableUriPermission(u, Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (_: Exception) {}
            }
            findViewById<android.widget.TextView>(R.id.textViewInferenceResult).text = "Imagens selecionadas: ${uris.size}"
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

        val btnCancel: android.widget.Button = findViewById(R.id.btnCancelBenchmark)
        btnCancel.setOnClickListener {
            cancelBenchmark = true
        }

        val btnPickDir: android.widget.Button = findViewById(R.id.btnPickImageDir)
        btnPickDir.setOnClickListener {
            pickImagesDirLauncher.launch(null)
        }

        val btnPickImages: android.widget.Button = findViewById(R.id.btnPickImages)
        btnPickImages.setOnClickListener {
            pickImagesLauncher.launch(arrayOf("image/*"))
        }
    }

    private fun runBenchmarkOnImageNetV2() {
        val modelTypeGroup: RadioGroup = findViewById(R.id.radioGroupModelType)
        val deviceGroup: RadioGroup = findViewById(R.id.radioGroupDevice)
        val selectedModelType = when (modelTypeGroup.checkedRadioButtonId) {
            R.id.radioPyTorch -> "PyTorch"
            R.id.radioTFLite -> "TFLite"
            else -> "Unknown"
        }
        val selectedDevice = when (deviceGroup.checkedRadioButtonId) {
            R.id.radioCPU -> "CPU"
            R.id.radioGPU -> "GPU"
            R.id.radioNNAPI -> "NNAPI"
            else -> "Unknown"
        }
        findViewById<android.widget.TextView>(R.id.textViewInferenceResult).text = "Iniciando benchmark..."
        val progressBar = findViewById<android.widget.ProgressBar>(R.id.progressBar)
        val progressText = findViewById<android.widget.TextView>(R.id.textViewProgress)
        val btnCancel = findViewById<android.widget.Button>(R.id.btnCancelBenchmark)
        progressBar.progress = 0
        progressBar.isIndeterminate = false
        progressBar.visibility = View.VISIBLE
        progressText.visibility = View.VISIBLE
        btnCancel.visibility = View.VISIBLE
        cancelBenchmark = false

        benchmarkThread?.interrupt()
        benchmarkThread = Thread {
            try {
                val selectedFiles = pickedImageUris
                val useDirUri = pickedImagesDirUri
                val usingFiles = selectedFiles.isNotEmpty()
                val usingExternal = !usingFiles && useDirUri != null
                val assetManager = assets
                val imageFiles: List<String>
                val assetPrefix: String
                if (!usingExternal && !usingFiles) {
                    val listed = assetManager.list("imagenetv2")?.filter {
                        it.endsWith(".JPEG", true) || it.endsWith(".jpg", true) || it.endsWith(".png", true)
                    } ?: emptyList()
                    if (listed.isEmpty()) {
                        runOnUiThread { findViewById<android.widget.TextView>(R.id.textViewInferenceResult).text = "Nenhuma imagem encontrada em assets/imagenetv2." }
                        return@Thread
                    }
                    imageFiles = listed
                    assetPrefix = "imagenetv2/"
                } else {
                    imageFiles = emptyList()
                    assetPrefix = ""
                }
                // Carregar modelo
                val modelPath = loadedModelPath ?: run {
                    runOnUiThread { findViewById<android.widget.TextView>(R.id.textViewInferenceResult).text = "Selecione e carregue um modelo antes do benchmark." }
                    return@Thread
                }
                val times = mutableListOf<Long>()
                val energies = mutableListOf<Double>()
                val n = when {
                    usingFiles -> selectedFiles.size
                    usingExternal -> -1
                    else -> imageFiles.size
                }
                val energyMonitor = EnergyMonitor(this)
                energyMonitor.start()
                var lastEnergy = energyMonitor.getPartialEnergy()
                var lastUiUpdate = 0L
                if (usingFiles) {
                    val progressBar = findViewById<android.widget.ProgressBar>(R.id.progressBar)
                    val progressText = findViewById<android.widget.TextView>(R.id.textViewProgress)
                    val total = selectedFiles.size
                    selectedFiles.forEachIndexed { idx, uri ->
                        if (cancelBenchmark) {
                            runOnUiThread { findViewById<android.widget.TextView>(R.id.textViewInferenceResult).text = "Benchmark cancelado." }
                            return@Thread
                        }
                        val input = com.example.harpia.util.ImageUtils.loadAndPreprocessImageFromUri(contentResolver, uri)
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
                            runOnUiThread { findViewById<android.widget.TextView>(R.id.textViewInferenceResult).text = "Erro na inferência de um arquivo: ${e.message}" }
                            return@Thread
                        }
                        val elapsed = (System.nanoTime() - start) / 1_000_000
                        val currentEnergy = energyMonitor.getPartialEnergy()
                        times.add(elapsed)
                        energies.add(currentEnergy - lastEnergy)
                        lastEnergy = currentEnergy
                        val now = System.currentTimeMillis()
                        if (now - lastUiUpdate > 100) {
                            lastUiUpdate = now
                            runOnUiThread {
                                val pct = ((idx + 1) * 100) / total
                                progressBar.progress = pct
                                progressText.text = "Progresso: ${idx + 1}/$total (${pct}%)"
                            }
                        }
                    }
                } else if (usingExternal) {
                    runOnUiThread {
                        progressBar.isIndeterminate = true
                        progressText.text = "Progresso: preparando..."
                    }
                    val docId = android.provider.DocumentsContract.getTreeDocumentId(useDirUri!!)
                    val childrenUri = android.provider.DocumentsContract.buildChildDocumentsUriUsingTree(useDirUri, docId)
                    contentResolver.query(
                        childrenUri,
                        arrayOf(
                            android.provider.DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                            android.provider.DocumentsContract.Document.COLUMN_MIME_TYPE
                        ),
                        null,
                        null,
                        null
                    )?.use { cursor ->
                        val idIdx = cursor.getColumnIndex(android.provider.DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                        val mimeIdx = cursor.getColumnIndex(android.provider.DocumentsContract.Document.COLUMN_MIME_TYPE)
                        val total = cursor.count
                        var processed = 0
                        if (total > 0 && total < 200000) {
                            runOnUiThread {
                                progressBar.isIndeterminate = false
                                progressBar.progress = 0
                            }
                        }
                        while (cursor.moveToNext()) {
                            if (cancelBenchmark) {
                                runOnUiThread {
                                    findViewById<android.widget.TextView>(R.id.textViewInferenceResult).text = "Benchmark cancelado."
                                }
                                break
                            }
                            val mime = cursor.getString(mimeIdx) ?: ""
                            if (!mime.startsWith("image/")) continue
                            val childId = cursor.getString(idIdx)
                            val docUri = android.provider.DocumentsContract.buildDocumentUriUsingTree(useDirUri, childId)
                            val input = com.example.harpia.util.ImageUtils.loadAndPreprocessImageFromUri(contentResolver, docUri)
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
                                    findViewById<android.widget.TextView>(R.id.textViewInferenceResult).text = "Erro na inferência de um arquivo: ${e.message}"
                                }
                                break
                            }
                            val elapsed = (System.nanoTime() - start) / 1_000_000 // ms
                            val currentEnergy = energyMonitor.getPartialEnergy()
                            times.add(elapsed)
                            energies.add(currentEnergy - lastEnergy)
                            lastEnergy = currentEnergy
                            processed += 1
                            val now = System.currentTimeMillis()
                            if (now - lastUiUpdate > 100) {
                                lastUiUpdate = now
                                runOnUiThread {
                                    if (!progressBar.isIndeterminate && total > 0) {
                                        val pct = (processed * 100) / total
                                        progressBar.progress = pct
                                        progressText.text = "Progresso: $processed/$total (${pct}%)"
                                    } else {
                                        progressText.text = "Progresso: $processed itens..."
                                    }
                                }
                            }
                        }
                        if (processed == 0) {
                            runOnUiThread { findViewById<android.widget.TextView>(R.id.textViewInferenceResult).text = "Nenhuma imagem encontrada no diretório selecionado." }
                            return@Thread
                        }
                    }
                } else {
                    for ((idx, entry) in imageFiles.withIndex()) {
                        if (cancelBenchmark) {
                            runOnUiThread {
                                findViewById<android.widget.TextView>(R.id.textViewInferenceResult).text = "Benchmark cancelado."
                            }
                            break
                        }
                        val input = com.example.harpia.util.ImageUtils.loadAndPreprocessImage(assets, assetPrefix + entry)
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
                                findViewById<android.widget.TextView>(R.id.textViewInferenceResult).text = "Erro na inferência da imagem $entry: ${e.message}"
                            }
                            break
                        }
                        val elapsed = (System.nanoTime() - start) / 1_000_000 // ms
                        val currentEnergy = energyMonitor.getPartialEnergy()
                        times.add(elapsed)
                        energies.add(currentEnergy - lastEnergy)
                        lastEnergy = currentEnergy
                        val now = System.currentTimeMillis()
                        if (now - lastUiUpdate > 100) {
                            lastUiUpdate = now
                            val processed = idx + 1
                            val pct = (processed * 100) / n
                            runOnUiThread {
                                progressBar.progress = pct
                                progressText.text = "Progresso: $processed/$n (${pct}%)"
                            }
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
                    progressBar.visibility = View.GONE
                    progressText.visibility = View.GONE
                    btnCancel.visibility = View.GONE
                }
            } catch (e: Exception) {
                runOnUiThread {
                    findViewById<android.widget.TextView>(R.id.textViewInferenceResult).text = "Erro no benchmark: ${e.message}"
                    findViewById<android.widget.ProgressBar>(R.id.progressBar).visibility = View.GONE
                    findViewById<android.widget.TextView>(R.id.textViewProgress).visibility = View.GONE
                    findViewById<android.widget.Button>(R.id.btnCancelBenchmark).visibility = View.GONE
                }
            }
        }.also { it.start() }
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
