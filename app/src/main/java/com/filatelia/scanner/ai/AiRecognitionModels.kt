package com.filatelia.scanner.ai

import kotlinx.serialization.Serializable

@Serializable
data class GeminiRequest(
    val contents: List<GeminiContent>,
    val generationConfig: GenerationConfig? = null
)

@Serializable
data class GeminiContent(
    val parts: List<GeminiPart>
)

@Serializable
data class GeminiPart(
    val text: String? = null,
    val inlineData: InlineData? = null
)

@Serializable
data class InlineData(
    val mimeType: String,
    val data: String
)

@Serializable
data class GenerationConfig(
    val responseMimeType: String? = "application/json"
)

@Serializable
data class GeminiResponse(
    val candidates: List<GeminiCandidate>? = null
)

@Serializable
data class GeminiCandidate(
    val content: GeminiContent? = null
)

@Serializable
data class StampRecognitionResult(
    val country: String? = null,
    val era: String? = null,
    val issueYearEstimate: Int? = null,
    val faceValue: String? = null,
    val series: String? = null,
    val condition: String? = null,
    val rarity: String? = null,
    val motif: String? = null,
    val historicalNote: String? = null,
    val estimatedMarketValue: String? = null,
    val catalogMichelNumber: String? = null,
    val catalogScottNumber: String? = null,
    val catalogYvertNumber: String? = null,
    val confidence: Float = 0.90f
)

sealed interface AiRecognitionOutcome {
    data class Success(val result: StampRecognitionResult) : AiRecognitionOutcome
    data class Error(val message: String) : AiRecognitionOutcome
    data object OfflineFallback : AiRecognitionOutcome
    data object RateLimited : AiRecognitionOutcome
}
