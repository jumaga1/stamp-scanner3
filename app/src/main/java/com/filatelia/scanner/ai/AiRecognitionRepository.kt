package com.filatelia.scanner.ai

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import com.filatelia.scanner.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
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
            .connectTimeout(40, TimeUnit.SECONDS)
            .readTimeout(40, TimeUnit.SECONDS)
            .build()
    }

    suspend fun recognize(imageFile: File): AiRecognitionOutcome = withContext(Dispatchers.IO) {
        try {
            val key = effectiveApiKey
            if (key.isBlank()) {
                return@withContext AiRecognitionOutcome.Error("API Key no configurada")
            }

            val bitmap = BitmapFactory.decodeFile(imageFile.absolutePath)
                ?: return@withContext AiRecognitionOutcome.Error("No se pudo cargar la imagen del sello")

            val outputStream = ByteArrayOutputStream()
            // Ajuste óptimo de resolución para máxima nitidez en los textos del sello
            val maxDimension = 1200
            val scale = if (bitmap.width > maxDimension || bitmap.height > maxDimension) {
                val ratio = maxDimension.toFloat() / maxOf(bitmap.width, bitmap.height)
                Bitmap.createScaledBitmap(bitmap, (bitmap.width * ratio).toInt(), (bitmap.height * ratio).toInt(), true)
            } else {
                bitmap
            }
            scale.compress(Bitmap.CompressFormat.JPEG, 85, outputStream)
            val base64Image = Base64.encodeToString(outputStream.toByteArray(), Base64.NO_WRAP)

            val prompt = """
                Eres un experto mundial y tasador en filatelia. Analiza minuciosamente el sello postal visible en la fotografía (lee todos los textos impresos en el sello, facial, matasellos e ilustraciones).
                
                IMPORTANTE: Si la imagen contiene anotaciones escritas (ej. hojas clasificadoras con año o país), úsalas como confirmación pero prioriza lo impreso en el sello.
                
                Devuelve EXCLUSIVAMENTE este formato JSON:
                {
                  "country": "País o entidad emisora oficial (ej. Alemania (RFA), Alemania (RDA), Deutsche Bundespost)",
                  "era": "Periodo o época histórica (ej. 1970-1979)",
                  "issueYearEstimate": 1970,
                  "faceValue": "Valor facial exacto con moneda (ej. 50 Pf, 40 Pf)",
                  "series": "Serie o tipo de emisión (ej. Serie básica Presidentes Federales)",
                  "condition": "Estado aparente (Usado / Matasellado, Nuevo)",
                  "rarity": "Nivel de rareza (Común, Escaso, Raro)",
                  "motif": "Descripción del diseño (ej. Retrato de Gustav Heinemann)",
                  "historicalNote": "Breve contexto histórico del sello",
                  "estimatedMarketValue": "Valor comercial estimado (ej. $0.50 - $1.50 USD)",
                  "catalogMichelNumber": "Número en catálogo Michel si es identificable (ej. MiNr. 638)",
                  "catalogScottNumber": "Número en catálogo Scott",
                  "catalogYvertNumber": "Número en catálogo Yvert",
                  "confidence": 0.95
                }
            """.trimIndent()

            val geminiPayload = buildJsonObject {
                put("contents", buildJsonArray {
                    add(buildJsonObject {
                        put("parts", buildJsonArray {
                            add(buildJsonObject { put("text", prompt) })
                            add(buildJsonObject {
                                put("inlineData", buildJsonObject {
                                    put("mimeType", "image/jpeg")
                                    put("data", base64Image)
                                })
                            })
                        })
                    })
                })
                put("generationConfig", buildJsonObject {
                    put("responseMimeType", "application/json")
                })
            }.toString()

            val mediaType = "application/json; charset=utf-8".toMediaType()

            // Lista de modelos oficiales
            val models = listOf(
                "gemini-2.0-flash",
                "gemini-1.5-flash",
                "gemini-1.5-flash-8b",
                "gemini-1.5-pro"
            )

            var lastError = ""

            for (model in models) {
                try {
                    val url = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$key"
                    val requestBuilder = Request.Builder()
                        .url(url)
                        .addHeader("x-goog-api-key", key)
                        .post(geminiPayload.toRequestBody(mediaType))

                    if (key.startsWith("AQ.")) {
                        requestBuilder.addHeader("Authorization", "Bearer $key")
                    }

                    val response = httpClient.newCall(requestBuilder.build()).execute()
                    val bodyStr = response.body?.string().orEmpty()

                    if (response.isSuccessful && bodyStr.isNotBlank()) {
                        val parsedObj = json.parseToJsonElement(bodyStr).jsonObject
                        val text = parsedObj["candidates"]?.jsonArray?.firstOrNull()?.jsonObject
                            ?.get("content")?.jsonObject
                            ?.get("parts")?.jsonArray?.firstOrNull()?.jsonObject
                            ?.get("text")?.jsonPrimitive?.content

                        if (!text.isNullOrBlank()) {
                            val clean = text.replace("```json", "").replace("```", "").trim()
                            val stamp = json.decodeFromString(StampRecognitionResult.serializer(), clean)
                            return@withContext AiRecognitionOutcome.Success(stamp)
                        }
                    } else if (response.code == 429) {
                        lastError = "Límite de consultas temporal (429) en $model"
                    } else {
                        lastError = "HTTP ${response.code}: $bodyStr"
                    }
                } catch (e: Exception) {
                    lastError = e.message ?: "Error de conexión"
                }
            }

            AiRecognitionOutcome.Error("La IA no pudo procesar la imagen: $lastError")
        } catch (e: Exception) {
            AiRecognitionOutcome.Error(e.message ?: "Error al procesar la imagen")
        }
    }
}
