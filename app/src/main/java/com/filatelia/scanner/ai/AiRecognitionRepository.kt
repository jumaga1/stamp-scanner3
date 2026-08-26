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
    private val json = Json { 
        ignoreUnknownKeys = true 
        isLenient = true 
        coerceInputValues = true
    }

    private val effectiveApiKey: String
        get() = when {
            !apiKeyOverride.isNullOrBlank() -> apiKeyOverride.trim()
            BuildConfig.AI_API_KEY.isNotBlank() -> BuildConfig.AI_API_KEY.trim()
            else -> ""
        }

    private val httpClient: OkHttpClient by lazy {
        val logging = HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BODY else HttpLoggingInterceptor.Level.NONE
        }
        OkHttpClient.Builder()
            .addInterceptor(logging)
            .connectTimeout(45, TimeUnit.SECONDS)
            .readTimeout(45, TimeUnit.SECONDS)
            .writeTimeout(45, TimeUnit.SECONDS)
            .build()
    }

    suspend fun recognize(imageFile: File): AiRecognitionOutcome = withContext(Dispatchers.IO) {
        val apiKey = effectiveApiKey
        if (apiKey.isBlank()) {
            return@withContext AiRecognitionOutcome.Error(
                "No se encontró la clave de API de IA. Configura AI_API_KEY en los Secrets del repositorio o en local.properties."
            )
        }

        try {
            val bitmap = BitmapFactory.decodeFile(imageFile.absolutePath)
                ?: return@withContext AiRecognitionOutcome.Error("No se pudo leer el archivo de imagen capturado.")

            // 1. Optimizar imagen
            val maxDimension = 1024
            val scaledBitmap = if (bitmap.width > maxDimension || bitmap.height > maxDimension) {
                val ratio = maxDimension.toFloat() / maxOf(bitmap.width, bitmap.height)
                Bitmap.createScaledBitmap(bitmap, (bitmap.width * ratio).toInt(), (bitmap.height * ratio).toInt(), true)
            } else {
                bitmap
            }

            val outputStream = ByteArrayOutputStream()
            scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 85, outputStream)
            val base64ImageData = Base64.encodeToString(outputStream.toByteArray(), Base64.NO_WRAP)

            // 2. Prompt pericial filatélico estricto
            val promptText = """
                Actúa como un perito filatélico y tasador profesional de sellos postales internacionales.
                Analiza exhaustivamente la imagen del timbre/estampilla postal adjunta.
                
                Instrucciones:
                1. Examina el país emisor, texto, idioma, valor facial, sobretasa (si tiene "+"), año impreso, temática, personaje y diseño general.
                2. Determina con máxima precisión catalográfica: Catálogo Michel, Scott, Yvert, año exacto de emisión, serie oficial y valor de mercado estimado.
                3. Si la imagen no contiene una estampilla identificable o está ilegible, indícalo de forma veraz sin inventar datos.
                
                Responde ÚNICAMENTE un objeto JSON válido con esta estructura exacta:
                {
                  "country": "Nombre exacto del país o entidad emisora",
                  "era": "Periodo o década (ej. 1980 - 1989)",
                  "issueYearEstimate": 1980,
                  "faceValue": "Valor facial exacto con unidad (ej. 80 Pf, 60+30 Pf, 50 c)",
                  "series": "Nombre oficial de la serie o motivo de emisión",
                  "condition": "Usado / Matasellado o Nuevo / Mint",
                  "rarity": "Común, Escaso, Raro o Muy Raro",
                  "motif": "Descripción exacta de la ilustración o personaje",
                  "historicalNote": "Reseña histórica real y contexto de la emisión postal",
                  "estimatedMarketValue": "Valor estimado (ej. $0.50 - $2.00 USD)",
                  "catalogMichelNumber": "Código Michel (MiNr.)",
                  "catalogScottNumber": "Código Scott",
                  "catalogYvertNumber": "Código Yvert",
                  "confidence": 0.98
                }
            """.trimIndent()

            val requestBodyJson = buildJsonObject {
                put("contents", buildJsonArray {
                    add(buildJsonObject {
                        put("parts", buildJsonArray {
                            add(buildJsonObject { put("text", promptText) })
                            add(buildJsonObject {
                                put("inlineData", buildJsonObject {
                                    put("mimeType", "image/jpeg")
                                    put("data", base64ImageData)
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

            // 3. Obtener dinámicamente los modelos activos para tu clave
            val availableModels = fetchAvailableModels(apiKey)
            val candidateEndpoints = if (availableModels.isNotEmpty()) {
                availableModels.map { "https://generativelanguage.googleapis.com/v1beta/$it:generateContent" }
            } else {
                listOf(
                    "https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent",
                    "https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash-8b:generateContent",
                    "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent",
                    "https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-pro:generateContent"
                )
            }

            var lastErrorMessage = ""

            for (baseUrl in candidateEndpoints) {
                try {
                    val url = "$baseUrl?key=$apiKey"
                    val request = Request.Builder()
                        .url(url)
                        .addHeader("x-goog-api-key", apiKey)
                        .post(requestBodyJson.toRequestBody(mediaType))
                        .build()

                    val response = httpClient.newCall(request).execute()
                    val bodyString = response.body?.string().orEmpty()

                    if (response.isSuccessful && bodyString.isNotBlank()) {
                        val root = json.parseToJsonElement(bodyString).jsonObject
                        val rawContent = root["candidates"]?.jsonArray?.firstOrNull()?.jsonObject
                            ?.get("content")?.jsonObject
                            ?.get("parts")?.jsonArray?.firstOrNull()?.jsonObject
                            ?.get("text")?.jsonPrimitive?.content

                        if (!rawContent.isNullOrBlank()) {
                            val cleanJson = rawContent
                                .replace("```json", "")
                                .replace("```", "")
                                .trim()

                            val parsed = json.decodeFromString(StampRecognitionResult.serializer(), cleanJson)
                            
                            val hdReferenceUrl = fetchHdStampImage("${parsed.motif ?: ""} ${parsed.country ?: ""}")
                            return@withContext AiRecognitionOutcome.Success(
                                parsed.copy(referenceImageUrl = hdReferenceUrl)
                            )
                        }
                    } else {
                        lastErrorMessage = "HTTP ${response.code}: $bodyString"
                    }
                } catch (e: Exception) {
                    lastErrorMessage = e.message ?: "Error de red"
                }
            }

            AiRecognitionOutcome.Error("No se pudo identificar la estampilla: $lastErrorMessage")
        } catch (e: Exception) {
            AiRecognitionOutcome.Error("Error procesando la imagen con IA: ${e.message}")
        }
    }

    private fun fetchAvailableModels(apiKey: String): List<String> {
        return try {
            val url = "https://generativelanguage.googleapis.com/v1beta/models?key=$apiKey"
            val request = Request.Builder().url(url).build()
            val response = httpClient.newCall(request).execute()
            val body = response.body?.string().orEmpty()

            if (response.isSuccessful && body.isNotBlank()) {
                val root = json.parseToJsonElement(body).jsonObject
                val modelsArray = root["models"]?.jsonArray ?: return emptyList()
                
                modelsArray.mapNotNull { modelElement ->
                    val obj = modelElement.jsonObject
                    val name = obj["name"]?.jsonPrimitive?.content ?: return@mapNotNull null
                    val methods = obj["supportedGenerationMethods"]?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList()
                    if (methods.contains("generateContent") && name.contains("gemini")) name else null
                }
            } else {
                emptyList()
            }
        } catch (_: Exception) {
            emptyList()
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
