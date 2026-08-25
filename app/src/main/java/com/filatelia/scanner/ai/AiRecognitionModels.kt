package com.filatelia.scanner.ai

import kotlinx.serialization.Serializable

/**
 * Resultado normalizado que la app espera del proveedor de IA, sin importar
 * cuál sea (Claude con imágenes, GPT-4V, un modelo propio, etc.).
 * El adaptador de cada proveedor (ver AiProviderAdapter) traduce la respuesta
 * cruda de su API a esta forma.
 */
@Serializable
data class StampRecognitionResult(
    val country: String? = null,
    val era: String? = null,
    val issueYearEstimate: Int? = null,
    val faceValue: String? = null,
    val series: String? = null,
    val condition: String? = null,       // Mint / Usado / Dañado / etc.
    val rarity: String? = null,          // Común / Poco común / Raro / Muy raro
    val motif: String? = null,
    val historicalNote: String? = null,
    val confidence: Float = 0f,          // 0.0 - 1.0
    val rawModelNotes: String? = null    // texto libre adicional del modelo, por si acaso
)
