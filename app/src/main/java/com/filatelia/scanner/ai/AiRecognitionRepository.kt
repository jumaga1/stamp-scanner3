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

            // 1. Optimizar imagen para el envío multimodal a Gemini
            val maxDimension = 1200
            val scaledBitmap = if (bitmap.width > maxDimension || bitmap.height > maxDimension) {
                val ratio = maxDimension.toFloat() / maxOf(bitmap.width, bitmap.height)
                Bitmap.createScaledBitmap(bitmap, (bitmap.width * ratio).toInt(), (bitmap.height * ratio).toInt(), true)
            } else {
                bitmap
            }

            val outputStream = ByteArrayOutputStream()
            scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 90, outputStream)
            val base64ImageData = Base64.encodeToString(outputStream.toByteArray(), Base64.NO_WRAP)

            // 2. Prompt pericial filatélico estricto (cero falsificación de datos)
            val promptText = """
                Actúa como un perito filatélico y tasador profesional de sellos postales internacionales.
                Analiza exhaustivamente la imagen del timbre/estampilla postal adjunta.
                
                Instrucciones de análisis visual:
                1. Examina minuciosamente el país emisor, texto, idioma, valor facial, sobretasa (si tiene "+"), año impreso, temática o personaje retratado y diseño general.
                2. Si la imagen muestra una estampilla real, determina con máxima precisión histórica y catalográfica sus datos reales (Catálogo Michel, Scott, Yvert, año exacto de emisión, serie oficial y valor de mercado estimado).
                3. Si la imagen no contiene una estampilla identificable, está ilegible o es un objeto diferente, devuélvelo en el JSON indicando "No identificado" sin inventar datos.
                
                Responde ÚNICAMENTE un objeto JSON válido con este formato:
                {
                  "country": "Nombre exacto del país o entidad emisora",
                  "era": "Periodo o década (ej. 1980 - 1989)",
                  "issueYearEstimate": 1980,
                  "faceValue": "Valor facial exacto con unidad (ej. 80 Pf, 60+30 Pf, 50 c)",
                  "series": "Nombre oficial de la serie o motivo de emisión",
                  "condition": "Usado / Matasellado o Nuevo / Mint",
                  "rarity": "Común, Escaso, Raro o Muy Raro",
                  "motif": "Descripción exacta y fidedigna de la ilustración, personaje, monumento o evento conmemorativo",
                  "historicalNote": "Reseña histórica real y contexto de la emisión postal",
                  "estimatedMarketValue": "Valor de mercado filatélico estimado (ej. $0.50 - $2.00 USD)",
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
                    put("temperature", 0.1)
                })
            }.toString()

            val mediaType = "application/json; charset=utf-8".toMediaType()
            val candidateModels = listOf("gemini-2.5-flash", "gemini-2.0-flash", "gemini-1.5-flash")
            var lastErrorMessage = ""

            for (model in candidateModels) {
                try {
                    val url = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$apiKey"
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
                            
                            // Buscar imagen oficial en alta resolución del motivo detectado
                            val hdReferenceUrl = fetchHdStampImage("${parsed.motif ?: ""} ${parsed.country ?: ""}")
                            return@withContext AiRecognitionOutcome.Success(
                                parsed.copy(referenceImageUrl = hdReferenceUrl)
                            )
                        }
                    } else {
                        lastErrorMessage = "HTTP ${response.code}: $bodyString"
                    }
                } catch (e: Exception) {
                    lastErrorMessage = e.message ?: "Error de conexión con la IA"
                }
            }

            AiRecognitionOutcome.Error("No se pudo identificar la estampilla: $lastErrorMessage")
        } catch (e: Exception) {
            AiRecognitionOutcome.Error("Error procesando la imagen con IA: ${e.message}")
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
