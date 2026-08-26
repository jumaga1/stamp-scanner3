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
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
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

    private val service: AiRecognitionService by lazy {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }
        val client = OkHttpClient.Builder()
            .addInterceptor(logging)
            .connectTimeout(45, TimeUnit.SECONDS)
            .readTimeout(45, TimeUnit.SECONDS)
            .build()

        val contentType = "application/json".toMediaType()

        Retrofit.Builder()
            .baseUrl("https://generativelanguage.googleapis.com/")
            .client(client)
            .addConverterFactory(json.asConverterFactory(contentType))
            .build()
            .create(AiRecognitionService::class.java)
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
                Eres un experto mundial en filatelia. Analiza minuciosamente este sello postal (ej: Deutsche Bundespost, RDA, PFA, Alemania, etc.).
                Extrae con precisión:
                1. country: País o entidad emisora oficial (ej. "Alemania", "Alemania (Berlín Oeste)", "RDA", "México").
                2. era: Época estimada (ej. "1970-1979", "República Federal").
                3. issueYearEstimate: Año de emisión estimado (número entero, ej. 1978).
                4. faceValue: Valor facial completo y moneda (ej. "80 Pf", "50 Céntimos").
                5. series: Serie o emisión temática si aplica (ej. "Para el bienestar público - Carruajes").
                6. condition: Estado aparente ("Usado / Matasellado", "Nuevo con goma").
                7. rarity: Rareza ("Común", "Escaso", "Raro").
                8. motif: Motivo o personaje que ilustra.
                9. historicalNote: Breve resumen histórico del sello.
                10. catalogMichelNumber: Referencia estimada de catálogo Michel (ej. "MiNr. 580").
                11. catalogScottNumber: Referencia Scott estimada.
                12. catalogYvertNumber: Referencia Yvert estimada.
                13. confidence: Número flotante de 0.0 a 1.0 indicando certeza.

                Devuelve EXCLUSIVAMENTE este JSON:
                {
                  "country": "...",
                  "era": "...",
                  "issueYearEstimate": 1978,
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

            val request = GeminiRequest(
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

            val fullUrl = "https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent"
            val response = service.generateContent(fullUrl, key, request)

            if (response.code() == 429) {
                return@withContext AiRecognitionOutcome.RateLimited
            }
            if (!response.isSuccessful) {
                return@withContext AiRecognitionOutcome.Error("Error HTTP ${response.code()}: ${response.errorBody()?.string() ?: response.message()}")
            }

            val rawJson = response.body()?.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                ?: return@withContext AiRecognitionOutcome.Error("Respuesta vacía de la IA")

            val cleanedJson = rawJson.replace("```json", "").replace("```", "").trim()
            val parsed = json.decodeFromString<StampRecognitionResult>(cleanedJson)
            AiRecognitionOutcome.Success(parsed)
        } catch (e: Exception) {
            AiRecognitionOutcome.Error(e.message ?: "Error desconocido al procesar con IA")
        }
    }
}
