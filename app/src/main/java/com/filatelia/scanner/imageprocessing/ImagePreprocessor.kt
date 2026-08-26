package com.filatelia.scanner.imageprocessing

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File
import java.io.FileOutputStream

object ImagePreprocessor {
    fun preprocess(inputFile: File): File {
        return try {
            val bitmap = BitmapFactory.decodeFile(inputFile.absolutePath) ?: return inputFile
            val scaled = Bitmap.createScaledBitmap(bitmap, 800, ((800f / bitmap.width) * bitmap.height).toInt(), true)
            val outFile = File(inputFile.parentFile, "proc_${inputFile.name}")
            val outStream = FileOutputStream(outFile)
            scaled.compress(Bitmap.CompressFormat.JPEG, 85, outStream)
            outStream.flush()
            outStream.close()
            outFile
        } catch (_: Exception) {
            inputFile
        }
    }
}
