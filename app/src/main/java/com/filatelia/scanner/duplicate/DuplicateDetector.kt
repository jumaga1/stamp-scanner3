package com.filatelia.scanner.duplicate

import com.filatelia.scanner.data.StampEntity

enum class DuplicateConfidence {
    NINGUNO,
    POSIBLE,
    PROBABLE,
    CASI_SEGURO
}

data class DuplicateResult(
    val confidence: DuplicateConfidence,
    val matchedStamp: StampEntity? = null,
    val message: String = ""
)

// Alias para compatibilidad
typealias DuplicateCheckResult = DuplicateResult

object DuplicateDetector {
    fun check(candidate: StampEntity, collection: List<StampEntity>): DuplicateResult {
        if (collection.isEmpty()) {
            return DuplicateResult(DuplicateConfidence.NINGUNO)
        }

        for (existing in collection) {
            val sameCat = !candidate.catalogMichelNumber.isNullOrBlank() &&
                    candidate.catalogMichelNumber.equals(existing.catalogMichelNumber, ignoreCase = true)
            val sameScott = !candidate.catalogScottNumber.isNullOrBlank() &&
                    candidate.catalogScottNumber.equals(existing.catalogScottNumber, ignoreCase = true)
            val sameCountryYearFace = candidate.country?.equals(existing.country, ignoreCase = true) == true &&
                    candidate.issueYear == existing.issueYear &&
                    candidate.faceValue?.equals(existing.faceValue, ignoreCase = true) == true

            if (sameCat || sameScott) {
                return DuplicateResult(DuplicateConfidence.CASI_SEGURO, existing, "Coincidencia exacta de catálogo.")
            }
            if (sameCountryYearFace) {
                return DuplicateResult(DuplicateConfidence.PROBABLE, existing, "Coincidencia de país, año y valor facial.")
            }
        }

        return DuplicateResult(DuplicateConfidence.NINGUNO)
    }

    fun findDuplicate(candidate: StampEntity, collection: List<StampEntity>): DuplicateResult {
        return check(candidate, collection)
    }
}
