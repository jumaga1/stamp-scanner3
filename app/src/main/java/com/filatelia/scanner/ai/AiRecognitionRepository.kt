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
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

class AiRecognitionRepository(
    private val apiKeyOverride: String? = null
) {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    // Lee la API Key real desde BuildConfig o inyección dinámica
    private val effectiveApiKey: String
        get() = when {
            !apiKeyOverride.isNullOrBlank() -> apiKeyOverride
            BuildConfig.AI_API_KEY.isNotBlank() -> BuildConfig.AI_API_KEY
            else -> ""
        }

    private val httpClient: OkHttpClient by lazy {
        val logging = HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BODY else HttpLoggingInterceptor.Level.NONE
        }
        OkHttpClient.Builder()
            .addInterceptor(logging)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    suspend fun recognize(imageFile: File): AiRecognitionOutcome = withContext(Dispatchers.IO) {
        val key = effectiveApiKey
        if (key.isBlank()) {
            return@withContext AiRecognitionOutcome.Error(
                "Falta configurar tu API Key de IA. Agrégala en local.properties o en los ajustes."
            )
        }

        try {
            val bitmap = BitmapFactory.decodeFile(imageFile.absolutePath)
                ?: return@withContext AiRecognitionOutcome.Error("No se pudo procesar la imagen capturada.")

            // Comprimir y codificar en Base64 con resolución balanceada
            val outputStream = ByteArrayOutputStream()
            val maxDimension = 1024
            val scale = if (bitmap.width > maxDimension || bitmap.height > maxDimension) {
                val ratio = maxDimension.toFloat() / maxOf(bitmap.width, bitmap.height)
                Bitmap.createScaledBitmap(bitmap, (bitmap.width * ratio).toInt(), (bitmap.height * ratio).toInt(), true)
            } else {
                bitmap
            }
            scale.compress(Bitmap.CompressFormat.JPEG, 85, outputStream)
            val base64Image = Base64.encodeToString(outputStream.toByteArray(), Base64.NO_WRAP)

            val prompt = """
                Eres un perito tasador y filatelista experto. Analiza minuciosamente la imagen de este sello postal (timbre).
                Examina el texto, facial, motivo o personaje histórico, país/emisión y año.
                
                Devuelve EXCLUSIVAMENTE un JSON con esta estructura exacta:
                {
                  "country": "Nombre oficial del país o entidad emisora",
                  "era": "Periodo o década histórica (ej. 1980 - 1989)",
                  "issueYearEstimate": 1980,
                  "faceValue": "Valor facial exacto con unidad (ej. 60+30 Pf, 50 c)",
                  "series": "Nombre de la serie oficial o emisión conmemorativa",
                  "condition": "Usado / Matasellado o Nuevo",
                  "rarity": "Común, Escaso o Raro",
                  "motif": "Descripción exacta del motivo, personaje, obra o evento ilustrado",
                  "historicalNote": "Explicación detallada y contexto histórico de la emisión",
                  "estimatedMarketValue": "Rango de precio de mercado filatélico (ej. $1.00 - $3.00 USD)",
                  "catalogMichelNumber": "Código aproximado Catálogo Michel (MiNr.)",
                  "catalogScottNumber": "Código aproximado Catálogo Scott",
                  "catalogYvertNumber": "Código aproximado Catálogo Yvert",
                  "confidence": 0.95
                }
            """.trimIndent()

            val requestPayload = buildJsonObject {
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
            val models = listOf("gemini-2.0-flash", "gemini-1.5-flash")
            var lastError = ""

            for (model in models) {
                try {
                    val url = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$key"
                    val requestBuilder = Request.Builder()
                        .url(url)
                        .addHeader("x-goog-api-key", key)
                        .post(requestPayload.toRequestBody(mediaType))

                    if (key.startsWith("AQ.")) {
                        requestBuilder.addHeader("Authorization", "Bearer $key")
                    }

                    val response = httpClient.newCall(requestBuilder.build()).execute()
                    val bodyString = response.body?.string().orEmpty()

                    if (response.isSuccessful && bodyString.isNotBlank()) {
                        val rootObj = json.parseToJsonElement(bodyString).jsonObject
                        val rawText = rootObj["candidates"]?.jsonArray?.firstOrNull()?.jsonObject
                            ?.get("content")?.jsonObject
                            ?.get("parts")?.jsonArray?.firstOrNull()?.jsonObject
                            ?.get("text")?.jsonPrimitive?.content

                        if (!rawText.isNullOrBlank()) {
                            val cleanJson = rawText.replace("```json", "").replace("```", "").trim()
                            val parsedStamp = json.decodeFromString(StampRecognitionResult.serializer(), cleanJson)

                            // Búsqueda de imagen de catálogo en Wikimedia Commons
                            val hdUrl = fetchHdStampImage("${parsedStamp.motif ?: ""} ${parsedStamp.country ?: ""}")
                            return@withContext AiRecognitionOutcome.Success(parsedStamp.copy(referenceImageUrl = hdUrl))
                        }
                    } else {
                        lastError = "HTTP ${response.code}: $bodyString"
                    }
                } catch (e: Exception) {
                    lastError = e.message ?: "Error de conexión"
                }
            }

            AiRecognitionOutcome.Error("No se pudo identificar el timbre con la IA: $lastError")
        } catch (e: Exception) {
            AiRecognitionOutcome.Error("Error al procesar el timbre: ${e.message}")
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
