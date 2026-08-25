package com.filatelia.scanner.imageprocessing

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.io.FileOutputStream

/**
 * Pipeline de preprocesamiento aplicado a cada foto/escaneo antes de:
 *   1) guardarla en la colección
 *   2) calcular su hash perceptual
 *   3) enviarla al servicio de IA
 *
 * Pasos: corregir orientación EXIF -> recorte (manual o automático por bordes) ->
 * normalización de contraste/brillo -> reducción de ruido simple.
 *
 * Nota: para un recorte automático de bordes robusto (detección del contorno del
 * sello) se recomienda integrar OpenCV (org.opencv:opencv-android). Aquí se deja
 * un recorte manual controlado por el usuario (rectángulo en la UI) más un
 * fallback de recorte central, para no forzar una dependencia pesada por defecto.
 */
object ImagePreprocessor {

    fun correctOrientation(file: File, bitmap: Bitmap): Bitmap {
        val exif = ExifInterface(file.absolutePath)
        val orientation = exif.getAttributeInt(
            ExifInterface.TAG_ORIENTATION,
            ExifInterface.ORIENTATION_NORMAL
        )
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            else -> return bitmap
        }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    /** Recorta el bitmap a un rectángulo dado (coordenadas en píxeles de la imagen original). */
    fun crop(bitmap: Bitmap, rect: Rect): Bitmap {
        val safeRect = Rect(
            rect.left.coerceIn(0, bitmap.width),
            rect.top.coerceIn(0, bitmap.height),
            rect.right.coerceIn(0, bitmap.width),
            rect.bottom.coerceIn(0, bitmap.height)
        )
        val width = (safeRect.right - safeRect.left).coerceAtLeast(1)
        val height = (safeRect.bottom - safeRect.top).coerceAtLeast(1)
        return Bitmap.createBitmap(bitmap, safeRect.left, safeRect.top, width, height)
    }

    /** Recorte automático simple: toma el 90% central de la imagen (fallback sin OpenCV). */
    fun centerCrop(bitmap: Bitmap, marginPercent: Float = 0.05f): Bitmap {
        val marginX = (bitmap.width * marginPercent).toInt()
        val marginY = (bitmap.height * marginPercent).toInt()
        return crop(
            bitmap,
            Rect(marginX, marginY, bitmap.width - marginX, bitmap.height - marginY)
        )
    }

    /**
     * Normaliza contraste y saturación para compensar distintas condiciones de luz
     * al escanear, y aplica un ligero aumento de nitidez percibida.
     */
    fun normalize(bitmap: Bitmap, contrast: Float = 1.15f, brightness: Float = 6f): Bitmap {
        val colorMatrix = ColorMatrix(
            floatArrayOf(
                contrast, 0f, 0f, 0f, brightness,
                0f, contrast, 0f, 0f, brightness,
                0f, 0f, contrast, 0f, brightness,
                0f, 0f, 0f, 1f, 0f
            )
        )
        val result = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        val paint = Paint().apply { colorFilter = ColorMatrixColorFilter(colorMatrix) }
        canvas.drawBitmap(bitmap, 0f, 0f, paint)
        return result
    }

    /** Reduce el bitmap a un tamaño estándar antes de guardarlo/enviarlo a la IA. */
    fun standardizeSize(bitmap: Bitmap, maxDimension: Int = 1024): Bitmap {
        val ratio = minOf(
            maxDimension.toFloat() / bitmap.width,
            maxDimension.toFloat() / bitmap.height,
            1f
        )
        if (ratio >= 1f) return bitmap
        val newWidth = (bitmap.width * ratio).toInt().coerceAtLeast(1)
        val newHeight = (bitmap.height * ratio).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
    }

    /** Pipeline completo por defecto, sin recorte manual (usa recorte central). */
    fun processFull(file: File, rawBitmap: Bitmap, manualCropRect: Rect? = null): Bitmap {
        var bmp = correctOrientation(file, rawBitmap)
        bmp = if (manualCropRect != null) crop(bmp, manualCropRect) else centerCrop(bmp)
        bmp = normalize(bmp)
        bmp = standardizeSize(bmp)
        return bmp
    }

    fun saveToFile(bitmap: Bitmap, destination: File, quality: Int = 92) {
        FileOutputStream(destination).use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
        }
    }
}
