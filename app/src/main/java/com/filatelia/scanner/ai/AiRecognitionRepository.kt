package com.filatelia.scanner.ai

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import com.filatelia.scanner.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
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
                2. era: Época estimada (ej. "1990-1999", "República Federal").
                3. issueYearEstimate: Año de emisión estimado (número entero, ej. 1991).
                4. faceValue: Valor facial completo y moneda (ej. "100 Pf", "80 Pf").
                5. series: Serie o emisión temática si aplica (ej. "Exposición Filatélica Internacional Berlín 1991", "Telecomunicaciones").
                6. condition: Estado aparente ("Usado / Matasellado", "Nuevo con goma").
                7. rarity: Rareza ("Común", "Escaso", "Raro").
                8. motif: Motivo o diseño ilustrado (ej. gráficos bursátiles, antenas, carruajes).
                9. historicalNote: Breve contexto histórico.
                10. catalogMichelNumber: Referencia estimada de catálogo Michel (ej. "MiNr. 1520").
                11. catalogScottNumber: Referencia Scott estimada.
                12. catalogYvertNumber: Referencia Yvert estimada.
                13. confidence: Grado de certeza de 0.0 a 1.0.

                Devuelve EXCLUSIVAMENTE este JSON:
                {
                  "country": "...",
                  "era": "...",
                  "issueYearEstimate": 1991,
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

            // 1. Descubrimiento automático de modelos habilitados para tu API Key
            val dynamicModels = mutableListOf<String>()
            try {
                val listReq = Request.Builder()
                    .url("https://generativelanguage.googleapis.com/v1beta/models?key=$key")
                    .addHeader("x-goog-api-key", key)
                    .get()
                    .build()
                val listRes = httpClient.newCall(listReq).execute()
                val listBody = listRes.body?.string().orEmpty()
                if (listRes.isSuccessful && listBody.isNotBlank()) {
                    val root = json.parseToJsonElement(listBody).jsonObject
                    val modelsArray = root["models"]?.jsonArray
                    modelsArray?.forEach { modelElem ->
                        val modelObj = modelElem.jsonObject
                        val name = modelObj["name"]?.jsonPrimitive?.content
                        val methods = modelObj["supportedGenerationMethods"]?.jsonArray?.map { it.jsonPrimitive.content }
                        if (name != null && methods?.contains("generateContent") == true) {
                            dynamicModels.add("https://generativelanguage.googleapis.com/v1beta/$name:generateContent?key=$key")
                        }
                    }
                }
            } catch (_: Exception) {}

            // 2. Lista de respaldo con versiones fijas y recientes
            val staticEndpoints = listOf(
                "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent?key=$key",
                "https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash-001:generateContent?key=$key",
                "https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash-002:generateContent?key=$key",
                "https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-pro:generateContent?key=$key"
            )

            val allEndpoints = (dynamicModels + staticEndpoints).distinct()

            var lastError = ""
            for (url in allEndpoints) {
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
                        return@withContext AiRecognitionOutcome.RateLimited
                    } else {
                        lastError = "HTTP ${response.code}: $responseBody"
                    }
                } catch (e: Exception) {
                    lastError = e.message ?: "Error de conexión"
                }
            }

            AiRecognitionOutcome.Error("Detalle: $lastError")
        } catch (e: Exception) {
            AiRecognitionOutcome.Error(e.message ?: "Error desconocido al procesar con IA")
        }
    }
}
