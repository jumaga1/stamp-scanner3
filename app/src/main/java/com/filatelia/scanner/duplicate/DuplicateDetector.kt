package com.filatelia.scanner.duplicate

import com.filatelia.scanner.data.StampEntity

enum class DuplicateConfidence {
    NINGUNO,
    POSIBLE,
    PROBABLE,
    CASI_SEGURO
}

data class DuplicateCheckResult(
    val confidence: DuplicateConfidence,
    val matchedStamp: StampEntity? = null,
    val message: String = ""
)

object DuplicateDetector {
    fun check(candidate: StampEntity, collection: List<StampEntity>): DuplicateCheckResult {
        if (collection.isEmpty()) {
            return DuplicateCheckResult(DuplicateConfidence.NINGUNO)
        }

        for (existing in collection) {
            // Coincidencia por catálogo o hash perceptual
            val sameCat = !candidate.catalogMichelNumber.isNullOrBlank() &&
                    candidate.catalogMichelNumber.equals(existing.catalogMichelNumber, ignoreCase = true)
            val sameScott = !candidate.catalogScottNumber.isNullOrBlank() &&
                    candidate.catalogScottNumber.equals(existing.catalogScottNumber, ignoreCase = true)
            val sameCountryYearFace = candidate.country?.equals(existing.country, ignoreCase = true) == true &&
                    candidate.issueYear == existing.issueYear &&
                    candidate.faceValue?.equals(existing.faceValue, ignoreCase = true) == true

            if (sameCat || sameScott) {
                return DuplicateCheckResult(DuplicateConfidence.CASI_SEGURO, existing, "Coincidencia exacta de catálogo.")
            }
            if (sameCountryYearFace) {
                return DuplicateCheckResult(DuplicateConfidence.PROBABLE, existing, "Coincidencia de país, año y valor facial.")
            }
        }

        return DuplicateCheckResult(DuplicateConfidence.NINGUNO)
    }
}
