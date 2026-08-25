package com.filatelia.scanner.ai

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.http.Body
import retrofit2.http.Headers
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * IMPORTANTE — ESTO ES UN PUNTO DE INTEGRACIÓN, NO UNA IA REAL YA ENTRENADA EN FILATELIA.
 *
 * No existe (a la fecha) un modelo de IA público ya entrenado específicamente en
 * "filatelia mundial" que reconozca país/época/valor/estado de un sello con solo
 * una foto. Lo que sí es viable hoy es usar un modelo de visión de propósito
 * general con un prompt bien diseñado.
 *
 * Este conector usa la API de Gemini (Google AI Studio), que tiene una capa
 * GRATUITA real: sin tarjeta de crédito, sin cobro, con un límite generoso de
 * solicitudes por día (no de dinero). Ojo: "gratis" no significa "sin límite" —
 * si superas la cuota diaria, la API responde error 429 hasta el día siguiente.
 * Para catalogar sellos uno por uno como colección personal, la cuota gratuita
 * alcanza sobradamente.
 *
 * Verifica siempre el nombre de modelo y el formato exacto en la documentación
 * vigente de Google (https://ai.google.dev/gemini-api/docs) antes de compilar,
 * ya que estos detalles cambian.
 */
interface AiRecognitionService {

    @Headers("content-type: application/json")
    @POST("v1beta/models/{model}:generateContent")
    suspend fun analyzeStampImage(
        @Path("model") model: String,
        @Query("key") apiKey: String,
        @Body request: GeminiGenerateContentRequest
    ): GeminiGenerateContentResponse
}

@Serializable
data class GeminiGenerateContentRequest(
    val contents: List<GeminiContent>
)

@Serializable
data class GeminiContent(
    val parts: List<GeminiPart>
)

@Serializable
data class GeminiPart(
    val text: String? = null,
    @SerialName("inline_data") val inlineData: GeminiInlineData? = null
)

@Serializable
data class GeminiInlineData(
    @SerialName("mime_type") val mimeType: String = "image/jpeg",
    val data: String
)

@Serializable
data class GeminiGenerateContentResponse(
    val candidates: List<GeminiCandidate> = emptyList()
)

@Serializable
data class GeminiCandidate(
    val content: GeminiContent? = null
)
