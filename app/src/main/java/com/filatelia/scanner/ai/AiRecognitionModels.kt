package com.filatelia.scanner.ai

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

sealed class AiRecognitionOutcome {
    data class Success(val result: StampRecognitionResult) : AiRecognitionOutcome()
    data class Error(val message: String) : AiRecognitionOutcome()
}

@Serializable
data class StampRecognitionResult(
    @SerialName("country") val country: String? = null,
    @SerialName("era") val era: String? = null,
    @SerialName("issueYearEstimate") val issueYearEstimate: Int? = null,
    @SerialName("faceValue") val faceValue: String? = null,
    @SerialName("series") val series: String? = null,
    @SerialName("condition") val condition: String? = null,
    @SerialName("rarity") val rarity: String? = null,
    @SerialName("motif") val motif: String? = null,
    @SerialName("historicalNote") val historicalNote: String? = null,
    @SerialName("estimatedMarketValue") val estimatedMarketValue: String? = null,
    @SerialName("referenceImageUrl") val referenceImageUrl: String? = null,
    @SerialName("catalogMichelNumber") val catalogMichelNumber: String? = null,
    @SerialName("catalogScottNumber") val catalogScottNumber: String? = null,
    @SerialName("catalogYvertNumber") val catalogYvertNumber: String? = null,
    @SerialName("confidence") val confidence: Float = 0.95f
)
