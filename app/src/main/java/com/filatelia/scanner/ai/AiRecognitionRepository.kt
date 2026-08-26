package com.filatelia.scanner.ai

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import com.filatelia.scanner.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import okhttp3.MediaType.Companion.toMediaType
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

        val baseUrl = BuildConfig.AI_API_BASE_URL.ifBlank { "https://generativelanguage.googleapis.com/" }
        val contentType = "application/json".toMediaType()

        Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(json.asConverterFactory(contentType))
            .build()
            .create(AiRecognitionService::class.java)
    }

    suspend fun recognizeStamp(imageFile: File): Result<AiStampJson> = withContext(Dispatchers.IO) {
        try {
            val key = effectiveApiKey
            if (key.isBlank()) {
                return@withContext Result.failure(Exception("API Key no configurada"))
            }

            val bitmap = BitmapFactory.decodeFile(imageFile.absolutePath)
                ?: return@withContext Result.failure(Exception("No se pudo leer la imagen"))

            val outputStream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 85, outputStream)
            val base64Image = Base64.encodeToString(outputStream.toByteArray(), Base64.NO_WRAP)

            val prompt = """
                Eres un experto mundial en filatelia. Analiza minuciosamente este sello postal (ej: Deutsche Bundespost, RDA, PFA, Alemania, etc.).
                Extrae o calcula con precisión:
                1. country: Nombre oficial de la entidad o país emisor (ej. "Alemania (RFA)", "Alemania (RDA / DDR)", "México").
                2. era: Época estimada (ej. "1960-1969", "República de Weimar", "Imperio Alemán").
                3. issueYearEstimate: Año exacto o estimado de emisión (número entero, ej. 1976).
                4. faceValue: Valor facial completo y moneda legible (ej. "40 Pf", "50 Céntimos", "5 USD").
                5. series: Serie o emisión temática si aplica (ej. "Retratos célebres", "Pintura expresionista").
                6. condition: Estado aparente (ej. "Usado / Matasellado", "Nuevo con goma").
                7. rarity: Nivel de rareza ("Común", "Escaso", "Raro").
                8. motif: Descripción del diseño o personaje ilustrado.
                9. historicalNote: Breve dato histórico de la emisión.
                10. catalogMichelNumber: Número estimado en catálogo Michel (ej. "MiNr. 814").
                11. catalogScottNumber: Referencia Scott si es identificable.
                12. catalogYvertNumber: Referencia Yvert si es identificable.
                13. confidence: Flotante de 0.0 a 1.0 indicando tu grado de certeza.

                Devuelve EXCLUSIVAMENTE este formato JSON:
                {
                  "country": "...",
                  "era": "...",
                  "issueYearEstimate": 1976,
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

            val response = service.generateContent(key, request)
            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("Error HTTP ${response.code()} al consultar la IA: ${response.message()}"))
            }

            val rawJson = response.body()?.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                ?: return@withContext Result.failure(Exception("La IA devolvió una respuesta vacía"))

            val cleanedJson = rawJson.replace("```json", "").replace("```", "").trim()
            val parsed = json.decodeFromString<AiStampJson>(cleanedJson)
            Result.success(parsed)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
