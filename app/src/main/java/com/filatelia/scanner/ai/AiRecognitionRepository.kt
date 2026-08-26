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
            val token = effectiveApiKey
            val bitmap = BitmapFactory.decodeFile(imageFile.absolutePath)
                ?: return@withContext AiRecognitionOutcome.Error("No se pudo procesar la imagen capturada")

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
                Eres un perito tasador y experto en filatelia internacional. Analiza detenidamente la fotografía de este sello postal.
                Lee con exactitud los textos impresos en la estampilla, el valor facial, el país o correo emisor, las fechas y el motivo ilustrado.

                Devuelve EXCLUSIVAMENTE un JSON válido con esta estructura:
                {
                  "country": "País o entidad postal emisora (ej. Alemania (Deutsche Bundespost), RDA, México)",
                  "era": "Periodo o época de emisión (ej. 1970 - 1979)",
                  "issueYearEstimate": 1972,
                  "faceValue": "Valor facial exacto con unidad (ej. 25 Pf, 50 Pf)",
                  "series": "Nombre de la serie oficial o emisión conmemorativa",
                  "condition": "Estado de conservación visual (ej. Usado / Matasellado, Nuevo)",
                  "rarity": "Nivel de rareza (Común, Escaso, Raro)",
                  "motif": "Nombre exacto del personaje, obra, monumento o evento ilustrado",
                  "historicalNote": "Reseña histórica y contexto filatélico de la emisión",
                  "estimatedMarketValue": "Valor estimado en mercado filatélico (ej. $0.50 - $1.80 USD)",
                  "catalogMichelNumber": "Código aproximado Catálogo Michel (MiNr.)",
                  "catalogScottNumber": "Código aproximado Catálogo Scott",
                  "catalogYvertNumber": "Código aproximado Catálogo Yvert",
                  "confidence": 0.95
                }
            """.trimIndent()

            val requestJson = buildJsonObject {
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

            val targetEndpoints = listOf(
                "https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent?key=$token",
                "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent?key=$token",
                "https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash-8b:generateContent?key=$token",
                "https://generativelanguage.googleapis.com/v1/models/gemini-1.5-flash:generateContent?key=$token"
            )

            var lastError = ""

            for (endpointUrl in targetEndpoints) {
                try {
                    val request = Request.Builder()
                        .url(endpointUrl)
                        .addHeader("Authorization", "Bearer $token")
                        .addHeader("x-goog-api-key", token)
                        .post(requestJson.toRequestBody(mediaType))
                        .build()

                    val response = httpClient.newCall(request).execute()
                    val bodyString = response.body?.string().orEmpty()

                    if (response.isSuccessful && bodyString.isNotBlank()) {
                        val rootObj = json.parseToJsonElement(bodyString).jsonObject
                        val responseText = rootObj["candidates"]?.jsonArray?.firstOrNull()?.jsonObject
                            ?.get("content")?.jsonObject
                            ?.get("parts")?.jsonArray?.firstOrNull()?.jsonObject
                            ?.get("text")?.jsonPrimitive?.content

                        if (!responseText.isNullOrBlank()) {
                            val cleanJson = responseText.replace("```json", "").replace("```", "").trim()
                            val baseStamp = json.decodeFromString(StampRecognitionResult.serializer(), cleanJson)
                            
                            val hdUrl = fetchHdStampImage("${baseStamp.motif.orEmpty()} ${baseStamp.country.orEmpty()}")
                            val finalStamp = baseStamp.copy(referenceImageUrl = hdUrl)
                            
                            return@withContext AiRecognitionOutcome.Success(finalStamp)
                        }
                    } else {
                        lastError = "HTTP ${response.code}: $bodyString"
                    }
                } catch (e: Exception) {
                    lastError = e.message ?: "Error de red"
                }
            }

            AiRecognitionOutcome.Error("No se pudo consultar el modelo: $lastError")
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
