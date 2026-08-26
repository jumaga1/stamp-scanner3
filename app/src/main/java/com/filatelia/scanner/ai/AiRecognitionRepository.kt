package com.filatelia.scanner.ai

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import com.filatelia.scanner.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.logging.HttpLoggingInterceptor
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.concurrent.TimeUnit

class AiRecognitionRepository(
    private val apiKeyOverride: String? = null
) {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private val effectiveApiKey: String
        get() = when {
            !apiKeyOverride.isNullOrBlank() -> apiKeyOverride
            BuildConfig.AI_API_KEY.isNotBlank() -> BuildConfig.AI_API_KEY
            else -> "AQ.Ab8RN6KqD2wgAw69n4UdJks6mqCpCi_bXLvCHlZT7aONEI19xQ"
        }

    private val httpClient: OkHttpClient by lazy {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }
        OkHttpClient.Builder()
            .addInterceptor(logging)
            .connectTimeout(45, TimeUnit.SECONDS)
            .readTimeout(45, TimeUnit.SECONDS)
            .build()
    }

    suspend fun recognize(imageFile: File): AiRecognitionOutcome = withContext(Dispatchers.IO) {
        try {
            val key = effectiveApiKey
            if (key.isBlank()) {
                return@withContext AiRecognitionOutcome.Error("API Key no configurada")
            }

            val bitmap = BitmapFactory.decodeFile(imageFile.absolutePath)
                ?: return@withContext AiRecognitionOutcome.Error("No se pudo leer la imagen")

            val outputStream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 85, outputStream)
            val base64Image = Base64.encodeToString(outputStream.toByteArray(), Base64.NO_WRAP)

            val prompt = """
                Eres un experto mundial en filatelia. Analiza minuciosamente este sello postal (ej: Deutsche Bundespost, Berlín, RDA, PFA, Alemania, etc.).
                Extrae con precisión:
                1. country: País o entidad emisora oficial (ej. "Alemania (Deutsche Bundespost)", "Alemania (Berlín)", "RDA", "México").
                2. era: Época estimada (ej. "1970-1979", "República Federal").
                3. issueYearEstimate: Año de emisión estimado (número entero, ej. 1971).
                4. faceValue: Valor facial completo y moneda (ej. "40 Pf", "80 Pf").
                5. series: Serie o emisión temática si aplica (ej. "Personajes ilustres", "Para el bienestar público").
                6. condition: Estado aparente ("Usado / Matasellado", "Nuevo con goma").
                7. rarity: Rareza ("Común", "Escaso", "Raro").
                8. motif: Motivo o personaje que ilustra.
                9. historicalNote: Breve contexto histórico.
                10. catalogMichelNumber: Referencia estimada de catálogo Michel (ej. "MiNr. 614").
                11. catalogScottNumber: Referencia Scott estimada.
                12. catalogYvertNumber: Referencia Yvert estimada.
                13. confidence: Grado de certeza de 0.0 a 1.0.

                Devuelve EXCLUSIVAMENTE este JSON:
                {
                  "country": "...",
                  "era": "...",
                  "issueYearEstimate": 1971,
                  "faceValue": "...",
                  "series": "...",
                  "condition": "...",
                  "rarity": "...",
                  "motif": "...",
                  "historicalNote": "...",
                  "catalogMichelNumber": "...",
                  "catalogScottNumber": "...",
                  "catalogYvertNumber": "...",
                  "confidence": 0.90
                }
            """.trimIndent()

            val requestPayload = GeminiRequest(
                contents = listOf(
                    GeminiContent(
                        parts = listOf(
                            GeminiPart(text = prompt),
                            GeminiPart(inlineData = InlineData("image/jpeg", base64Image))
                        )
                    )
                ),
                generationConfig = GenerationConfig("application/json")
            )

            val jsonBody = json.encodeToString(GeminiRequest.serializer(), requestPayload)
            val mediaType = "application/json; charset=utf-8".toMediaType()

            // Lista de endpoints candidatos compatibles para garantizar respuesta 200
            val candidateUrls = listOf(
                "https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash-latest:generateContent?key=$key",
                "https://generativelanguage.googleapis.com/v1beta/models/gemini-pro-vision:generateContent?key=$key",
                "https://generativelanguage.googleapis.com/v1/models/gemini-1.5-flash:generateContent?key=$key"
            )

            var lastError = ""
            for (url in candidateUrls) {
                val httpRequest = Request.Builder()
                    .url(url)
                    .addHeader("x-goog-api-key", key)
                    .post(jsonBody.toRequestBody(mediaType))
                    .build()

                val response = httpClient.newCall(httpRequest).execute()
                val responseBody = response.body?.string().orEmpty()

                if (response.isSuccessful && responseBody.isNotBlank()) {
                    val geminiRes = json.decodeFromString(GeminiResponse.serializer(), responseBody)
                    val rawText = geminiRes.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                    if (!rawText.isNullOrBlank()) {
                        val cleaned = rawText.replace("```json", "").replace("```", "").trim()
                        val parsed = json.decodeFromString(StampRecognitionResult.serializer(), cleaned)
                        return@withContext AiRecognitionOutcome.Success(parsed)
                    }
                } else if (response.code == 429) {
                    return@withContext AiRecognitionOutcome.RateLimited
                } else {
                    lastError = "HTTP ${response.code}: $responseBody"
                }
            }

            AiRecognitionOutcome.Error("No se pudo identificar con los modelos disponibles: $lastError")
        } catch (e: Exception) {
            AiRecognitionOutcome.Error(e.message ?: "Error desconocido al procesar con IA")
        }
    }
}
