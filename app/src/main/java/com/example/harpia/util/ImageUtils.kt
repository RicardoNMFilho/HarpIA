package com.example.harpia.util

import android.content.ContentResolver
import android.content.res.AssetManager
import android.graphics.BitmapFactory
import android.net.Uri

object ImageUtils {
    fun loadAndPreprocessImage(assetManager: AssetManager, assetPath: String): FloatArray {
        val inputStream = assetManager.open(assetPath)
        val bitmap = BitmapFactory.decodeStream(inputStream)
        inputStream.close()
        return preprocessBitmap(bitmap)
    }

    fun loadAndPreprocessImageFromUri(contentResolver: ContentResolver, uri: Uri): FloatArray {
        val inputStream = contentResolver.openInputStream(uri) ?: throw IllegalArgumentException("Não foi possível abrir a imagem: $uri")
        val bitmap = BitmapFactory.decodeStream(inputStream)
        inputStream.close()
        return preprocessBitmap(bitmap)
    }

    private fun preprocessBitmap(bitmap: android.graphics.Bitmap): FloatArray {
        val resized = android.graphics.Bitmap.createScaledBitmap(bitmap, 224, 224, true)
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
}
