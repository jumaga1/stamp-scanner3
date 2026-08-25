package com.filatelia.scanner.imageprocessing

import android.graphics.Bitmap
import kotlin.math.cos
import kotlin.math.PI

/**
 * Hash perceptual (pHash) de 64 bits basado en DCT (Discrete Cosine Transform).
 * Dos imágenes visualmente similares producen hashes con poca distancia de Hamming,
 * incluso si difieren ligeramente en iluminación, ángulo o compresión — justo lo que
 * necesitamos para detectar que "este sello ya está en la colección" aunque la foto
 * nueva no sea pixel-a-pixel idéntica a la guardada.
 *
 * Esto NO requiere modelos de IA ni conexión a internet: es matemática pura y corre
 * instantáneamente en el dispositivo.
 */
object PerceptualHash {

    private const val SIZE = 32       // imagen reducida a 32x32 antes de la DCT
    private const val LOW_FREQ = 8    // se usa el bloque 8x8 de bajas frecuencias

    fun compute(bitmap: Bitmap): String {
        val gray = toGrayscale(resize(bitmap, SIZE, SIZE))
        val dct = applyDct(gray)

        // Bloque de bajas frecuencias (ignorando el término DC en [0][0])
        val freqs = DoubleArray(LOW_FREQ * LOW_FREQ - 1)
        var idx = 0
        for (u in 0 until LOW_FREQ) {
            for (v in 0 until LOW_FREQ) {
                if (u == 0 && v == 0) continue
                freqs[idx++] = dct[u][v]
            }
        }
        val median = freqs.sorted()[freqs.size / 2]

        val bits = StringBuilder()
        for (u in 0 until LOW_FREQ) {
            for (v in 0 until LOW_FREQ) {
                if (u == 0 && v == 0) continue
                bits.append(if (dct[u][v] > median) '1' else '0')
            }
        }
        return bits.toString()
    }

    /** Distancia de Hamming entre dos hashes: cuántos bits difieren. 0 = idénticos. */
    fun hammingDistance(hashA: String, hashB: String): Int {
        if (hashA.length != hashB.length) return Int.MAX_VALUE
        var distance = 0
        for (i in hashA.indices) {
            if (hashA[i] != hashB[i]) distance++
        }
        return distance
    }

    private fun resize(bitmap: Bitmap, width: Int, height: Int): Bitmap =
        Bitmap.createScaledBitmap(bitmap, width, height, true)

    private fun toGrayscale(bitmap: Bitmap): Array<DoubleArray> {
        val matrix = Array(bitmap.height) { DoubleArray(bitmap.width) }
        for (y in 0 until bitmap.height) {
            for (x in 0 until bitmap.width) {
                val pixel = bitmap.getPixel(x, y)
                val r = (pixel shr 16) and 0xFF
                val g = (pixel shr 8) and 0xFF
                val b = pixel and 0xFF
                matrix[y][x] = 0.299 * r + 0.587 * g + 0.114 * b
            }
        }
        return matrix
    }

    private fun applyDct(matrix: Array<DoubleArray>): Array<DoubleArray> {
        val n = matrix.size
        val result = Array(n) { DoubleArray(n) }
        val c = DoubleArray(n) { if (it == 0) 1.0 / Math.sqrt(2.0) else 1.0 }

        for (u in 0 until n) {
            for (v in 0 until n) {
                var sum = 0.0
                for (x in 0 until n) {
                    for (y in 0 until n) {
                        sum += matrix[x][y] *
                            cos((2 * x + 1) * u * PI / (2.0 * n)) *
                            cos((2 * y + 1) * v * PI / (2.0 * n))
                    }
                }
                result[u][v] = 0.25 * c[u] * c[v] * sum
            }
        }
        return result
    }
}
