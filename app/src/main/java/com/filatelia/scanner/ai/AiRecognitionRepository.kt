package com.filatelia.scanner.ai

import android.graphics.Bitmap
import android.util.Base64
import com.filatelia.scanner.BuildConfig
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.MediaType.Companion.toMediaType
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit

private const val RECOGNITION_PROMPT = """
Eres un experto en filatelia. Analiza la imagen de este sello postal y responde
EXCLUSIVAMENTE con un objeto JSON (sin texto adicional, sin markdown, sin ```) con esta forma exacta:
{
  "country": string o null,
  "era": string o null (ej. "1950-1959"),
  "issueYearEstimate": number o null,
  "faceValue": string o null,
  "series": string o null,
  "condition": string o null (uno de: "Mint", "Usado", "Dañado", "Desconocido"),
  "rarity": string o null (uno de: "Común", "Poco común", "Raro", "Muy raro", "Desconocido"),
  "motif": string o null (breve descripción del motivo/diseño),
  "historicalNote": string o null (dato histórico relevante, 1-2 frases),
  "confidence": number entre 0 y 1,
  "rawModelNotes": string o null
}
Si no puedes determinar un campo con confianza razonable, usa null en vez de inventar.
"""

// Modelo gratuito con visión de Gemini. "flash" tiene mejor cuota gratuita que "pro".
// Verifica el nombre vigente en https://ai.google.dev/gemini-api/docs/models antes de compilar.
private const val GEMINI_MODEL = "gemini-2.5-flash"

sealed class AiRecognitionOutcome {
    data class Success(val result: StampRecognitionResult) : AiRecognitionOutcome()
    data class Error(val message: String, val cause: Throwable? = null) : AiRecognitionOutcome()
    object MissingApiKey : AiRecognitionOutcome()
    object QuotaExceeded : AiRecognitionOutcome()
}

class AiRecognitionRepository {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private val retrofit = Retrofit.Builder()
        .baseUrl(BuildConfig.AI_API_BASE_URL)
        .client(okHttpClient)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()

    private val service = retrofit.create(AiRecognitionService::class.java)

    suspend fun recognize(bitmap: Bitmap): AiRecognitionOutcome {
        if (BuildConfig.AI_API_KEY.isBlank()) {
            return AiRecognitionOutcome.MissingApiKey
        }
        return try {
            val base64Image = bitmapToBase64(bitmap)
            val request = GeminiGenerateContentRequest(
                contents = listOf(
                    GeminiContent(
                        parts = listOf(
                            GeminiPart(inlineData = GeminiInlineData(data = base64Image)),
                            GeminiPart(text = RECOGNITION_PROMPT)
                        )
                    )
                )
            )

            val response = service.analyzeStampImage(
                model = GEMINI_MODEL,
                apiKey = BuildConfig.AI_API_KEY,
                request = request
            )

            val textBlock = response.candidates
                .firstOrNull()?.content?.parts
                ?.firstOrNull { it.text != null }?.text
                ?: return AiRecognitionOutcome.Error("Respuesta vacía del proveedor de IA")

            val cleanJson = textBlock.trim()
                .removePrefix("```json").removePrefix("```")
                .removeSuffix("```").trim()

            val result = json.decodeFromString(StampRecognitionResult.serializer(), cleanJson)
            AiRecognitionOutcome.Success(result)
        } catch (t: retrofit2.HttpException) {
            if (t.code() == 429) {
                AiRecognitionOutcome.QuotaExceeded
            } else {
                AiRecognitionOutcome.Error("Error HTTP ${t.code()} al consultar la IA", t)
            }
        } catch (t: Throwable) {
            AiRecognitionOutcome.Error(t.message ?: "Error desconocido al consultar la IA", t)
        }
    }

    private fun bitmapToBase64(bitmap: Bitmap): String {
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 90, stream)
        return Base64.encodeToString(stream.toByteArray(), Base64.NO_WRAP)
    }
}
