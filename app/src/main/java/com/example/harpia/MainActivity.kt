package com.example.harpia

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
import org.tensorflow.lite.Interpreter
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.io.FileInputStream
import java.io.File

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val btnLoadModel: android.widget.Button = findViewById(R.id.btnLoadModel)
        btnLoadModel.setOnClickListener {
            val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
                type = "*/*"
            }
            startActivityForResult(intent, 1)
        }
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

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 1 && resultCode == RESULT_OK) {
            val uri: Uri? = data?.data
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
                    val resultView: android.widget.TextView = findViewById(R.id.textViewResult)
                    resultView.text = "Erro ao obter arquivo do modelo."
                    return
                }
                val input = FloatArray(1 * 224 * 224 * 3) // Aqui você deve carregar/preprocessar a imagem real

                // Coleta de energia
                val energyMonitor = EnergyMonitor(this)
                energyMonitor.start()
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
                    val resultView: android.widget.TextView = findViewById(R.id.textViewResult)
                    resultView.text = "Erro ao executar modelo: ${e.message}"
                    return
                }
                val (energy, timeElapsed) = energyMonitor.stopAndGetEnergy()

                // Exibir resultado no TextView
                val resultView: android.widget.TextView = findViewById(R.id.textViewResult)
                val sb = StringBuilder()
                sb.append("Resultado: ${result.joinToString(", ")}".take(200))
                sb.append("\nTempo de inferência: ${timeElapsed} ms")
                if (energy > 0) {
                    sb.append("\nConsumo estimado de energia: %.4f Joules".format(energy))
                } else {
                    sb.append("\nConsumo de energia não disponível")
                }
                resultView.text = sb.toString()
            }
        }
    }
}
