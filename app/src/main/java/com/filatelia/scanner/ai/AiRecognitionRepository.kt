package com.filatelia.scanner.ai

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import com.filatelia.scanner.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
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
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
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
            // Reducción eficiente para que la API responda a máxima velocidad
            val maxDimension = 1024
            val scale = if (bitmap.width > maxDimension || bitmap.height > maxDimension) {
                val ratio = maxDimension.toFloat() / maxOf(bitmap.width, bitmap.height)
                Bitmap.createScaledBitmap(bitmap, (bitmap.width * ratio).toInt(), (bitmap.height * ratio).toInt(), true)
            } else {
                bitmap
            }
            scale.compress(Bitmap.CompressFormat.JPEG, 80, outputStream)
            val base64Image = Base64.encodeToString(outputStream.toByteArray(), Base64.NO_WRAP)

            val prompt = """
                Eres un experto filatélico mundial y tasador profesional de sellos postales. Analiza minuciosamente la imagen del sello adjunto.
                
                Extrae o deduce con máxima precisión:
                1. country: País emisor o entidad oficial (ej. "Alemania (Deutsche Bundespost)", "Alemania (Berlín Oeste)", "Alemania (RDA)", "México").
                2. era: Periodo histórico (ej. "1970-1979", "República Federal").
                3. issueYearEstimate: Año exacto o aproximado de emisión (entero).
                4. faceValue: Valor facial exacto y moneda (ej. "50+25 Pf", "80 Pf").
                5. series: Nombre de la serie o temática oficial.
                6. condition: Estado de conservación (ej. "Usado / Matasellado", "Nuevo / Mint Never Hinged").
                7. rarity: Rareza filatélica ("Común", "Escaso", "Raro", "Pieza de Museo").
                8. motif: Descripción detallada del diseño o conmemoración.
                9. historicalNote: Nota histórica y contexto de la emisión.
                10. estimatedMarketValue: Rango de precio comercial estimado en el mercado filatélico actual (ej. "$0.50 - $1.50 USD", "€2.00 - €5.00 EUR").
                11. catalogMichelNumber: Código de Catálogo Michel si aplica (ej. "MiNr. 1024").
                12. catalogScottNumber: Código Catálogo Scott si aplica.
                13. catalogYvertNumber: Código Catálogo Yvert si aplica.
                14. confidence: Flotante entre 0.0 y 1.0 indicando nivel de certeza.

                Devuelve EXCLUSIVAMENTE un JSON válido con esta estructura:
                {
                  "country": "...",
                  "era": "...",
                  "issueYearEstimate": 1979,
                  "faceValue": "...",
                  "series": "...",
                  "condition": "...",
                  "rarity": "...",
                  "motif": "...",
                  "historicalNote": "...",
                  "estimatedMarketValue": "...",
                  "catalogMichelNumber": "...",
                  "catalogScottNumber": "...",
                  "catalogYvertNumber": "...",
                  "confidence": 0.95
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

            // Cascada de modelos gratuitos de Gemini con rotación automática ante cuota excedida (429) o fallos
            val freeModelsCascade = listOf(
                "gemini-2.0-flash",
                "gemini-1.5-flash-8b",
                "gemini-1.5-flash",
                "gemini-2.0-flash-lite-preview-02-05",
                "gemini-1.5-flash-001",
                "gemini-1.5-flash-002",
                "gemini-1.5-pro",
                "gemini-pro-vision"
            )

            var lastErrorMessage = ""

            for (model in freeModelsCascade) {
                val url = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$key"
                try {
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
                        // Cuota excedida en este modelo: salta al siguiente modelo gratuito de la lista
                        lastErrorMessage = "Cuota excedida en $model, intentando modelo alternativo..."
                        delay(200)
                        continue
                    } else {
                        lastErrorMessage = "Modelo $model falló (HTTP ${response.code})"
                    }
                } catch (e: Exception) {
                    lastErrorMessage = e.message ?: "Error de red"
                }
            }

            AiRecognitionOutcome.Error("Todos los modelos gratuitos estaban ocupados. Intenta de nuevo en unos segundos.")
        } catch (e: Exception) {
            AiRecognitionOutcome.Error(e.message ?: "Error inesperado durante el reconocimiento")
        }
    }
}
