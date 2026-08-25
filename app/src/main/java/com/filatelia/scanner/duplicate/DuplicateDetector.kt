package com.filatelia.scanner.duplicate

import com.filatelia.scanner.data.StampHashProjection
import com.filatelia.scanner.imageprocessing.PerceptualHash

enum class DuplicateConfidence { NINGUNO, POSIBLE, PROBABLE, CASI_SEGURO }

data class DuplicateResult(
    val confidence: DuplicateConfidence,
    val matchedStampId: Long? = null,
    val hammingDistance: Int? = null,
    val metadataMatched: Boolean = false
)

/**
 * Reglas de decisión:
 *  - distancia de Hamming <= 5 (de 63 bits)  -> visualmente casi idéntico
 *  - distancia <= 5 y país+año+valor coinciden -> CASI_SEGURO (mismo sello)
 *  - distancia <= 5 sin coincidencia de metadatos -> PROBABLE (mismo diseño, revisar)
 *  - distancia entre 6 y 12 con metadatos coincidentes -> POSIBLE (avisar, no bloquear)
 *  - distancia > 12 -> NINGUNO
 *
 * Estos umbrales son configurables y pensados para pHash de 63 bits (8x8 - 1).
 */
object DuplicateDetector {

    private const val THRESHOLD_STRONG = 5
    private const val THRESHOLD_WEAK = 12

    fun findDuplicate(
        existing: List<StampHashProjection>,
        newHash: String,
        country: String?,
        issueYear: Int?,
        faceValue: String?
    ): DuplicateResult {
        var best: DuplicateResult = DuplicateResult(DuplicateConfidence.NINGUNO)
        var bestDistance = Int.MAX_VALUE

        for (candidate in existing) {
            val distance = PerceptualHash.hammingDistance(newHash, candidate.perceptualHash)
            if (distance >= bestDistance) continue

            val metadataMatch = country != null && country.equals(candidate.country, ignoreCase = true) &&
                issueYear != null && issueYear == candidate.issueYear &&
                faceValue != null && faceValue.equals(candidate.faceValue, ignoreCase = true)

            val confidence = when {
                distance <= THRESHOLD_STRONG && metadataMatch -> DuplicateConfidence.CASI_SEGURO
                distance <= THRESHOLD_STRONG -> DuplicateConfidence.PROBABLE
                distance <= THRESHOLD_WEAK && metadataMatch -> DuplicateConfidence.POSIBLE
                else -> DuplicateConfidence.NINGUNO
            }

            if (confidence != DuplicateConfidence.NINGUNO) {
                bestDistance = distance
                best = DuplicateResult(confidence, candidate.id, distance, metadataMatch)
            }
        }
        return best
    }
}
