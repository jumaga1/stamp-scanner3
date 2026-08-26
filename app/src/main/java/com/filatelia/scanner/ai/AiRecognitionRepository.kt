package com.filatelia.scanner.ai

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
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
import java.net.URLEncoder
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
            val bitmap = BitmapFactory.decodeFile(imageFile.absolutePath)
                ?: return@withContext AiRecognitionOutcome.Error("No se pudo cargar la imagen")

            val outputStream = ByteArrayOutputStream()
            val maxDimension = 1200
            val scale = if (bitmap.width > maxDimension || bitmap.height > maxDimension) {
                val ratio = maxDimension.toFloat() / maxOf(bitmap.width, bitmap.height)
                Bitmap.createScaledBitmap(bitmap, (bitmap.width * ratio).toInt(), (bitmap.height * ratio).toInt(), true)
            } else {
                bitmap
            }
            scale.compress(Bitmap.CompressFormat.JPEG, 90, outputStream)
            val base64Image = Base64.encodeToString(outputStream.toByteArray(), Base64.NO_WRAP)

            val prompt = """
                Analiza como experto filatélico la imagen de este sello postal.
                Lee con extremo cuidado los textos impresos en los bordes, el valor numérico, el personaje o motivo y el emisor (ej. "LUCAS CRANACH d. Ä. 1472", "DEUTSCHE BUNDESPOST", valor "25").
                
                Responde ÚNICAMENTE con este JSON:
                {
                  "country": "País o entidad emisora",
                  "era": "Periodo histórico",
                  "issueYearEstimate": 1972,
                  "faceValue": "Valor facial exacto con moneda",
                  "series": "Serie a la que pertenece",
                  "condition": "Estado",
                  "rarity": "Rareza",
                  "motif": "Nombre exacto del personaje, obra o evento",
                  "historicalNote": "Explicación histórica detallada",
                  "estimatedMarketValue": "$0.50 - $1.50 USD",
                  "catalogMichelNumber": "MiNr. ...",
                  "catalogScottNumber": "Scott ...",
                  "catalogYvertNumber": "Yvert ...",
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
            val models = listOf("gemini-2.0-flash", "gemini-1.5-flash", "gemini-1.5-flash-8b")

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
                            val baseResult = json.decodeFromString(StampRecognitionResult.serializer(), clean)
                            
                            // Buscar automáticamente la imagen nítida en Wikimedia Commons
                            val hdImageUrl = fetchHdStampImage(baseResult.motif ?: baseResult.country.orEmpty())
                            val finalResult = baseResult.copy(referenceImageUrl = hdImageUrl)
                            
                            return@withContext AiRecognitionOutcome.Success(finalResult)
                        }
                    } else {
                        lastError = "HTTP ${response.code()}: $bodyStr"
                    }
                } catch (e: Exception) {
                    lastError = e.message ?: "Error de red"
                }
            }

            AiRecognitionOutcome.Error("No se pudo procesar con la IA: $lastError")
        } catch (e: Exception) {
            AiRecognitionOutcome.Error(e.message ?: "Error al procesar la imagen")
        }
    }

    private fun fetchHdStampImage(searchTerm: String): String? {
        return try {
            if (searchTerm.isBlank()) return null
            val encodedQuery = URLEncoder.encode("$searchTerm stamp Briefmarke", "UTF-8")
            val url = "https://commons.wikimedia.org/w/api.php?action=query&generator=search&gsrsearch=$encodedQuery&gsrlimit=1&prop=imageinfo&iiprop=url&format=json"

            val request = Request.Builder().url(url).build()
            val response = httpClient.newCall(request).execute()
            val body = response.body?.string().orEmpty()

            if (response.isSuccessful && body.isNotBlank()) {
                val root = json.parseToJsonElement(body).jsonObject
                val pages = root["query"]?.jsonObject?.get("pages")?.jsonObject
                val firstPage = pages?.values?.firstOrNull()?.jsonObject
                val imageInfo = firstPage?.get("imageinfo")?.jsonArray?.firstOrNull()?.jsonObject
                imageInfo?.get("url")?.jsonPrimitive?.content
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }
}
