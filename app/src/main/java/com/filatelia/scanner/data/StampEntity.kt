package com.filatelia.scanner.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Representa un sello guardado en la colección personal del usuario.
 * Cada campo puede llenarse manualmente o ser sugerido por el módulo de IA
 * (ver com.filatelia.scanner.ai.AiRecognitionRepository).
 */
@Entity(tableName = "stamps")
data class StampEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    // Imagen
    val imagePath: String,                 // ruta al archivo procesado (recortado/normalizado)
    val originalImagePath: String? = null, // ruta a la foto original sin procesar
    val perceptualHash: String,            // hash pHash en binario (64 bits como String) para detectar duplicados

    // Identificación / clasificación
    val country: String? = null,
    val era: String? = null,               // época estimada, ej. "1950-1959"
    val faceValue: String? = null,         // valor nominal impreso, ej. "5 centavos"
    val series: String? = null,
    val condition: String? = null,         // estado de conservación: Mint, Usado, Dañado, etc.
    val rarity: String? = null,            // Común, Poco común, Raro, Muy raro

    // Ficha informativa
    val issueYear: Int? = null,
    val printRun: String? = null,          // tiraje
    val motif: String? = null,             // motivo / diseño del sello
    val historicalNote: String? = null,    // valor histórico / contexto

    // Referencias a catálogos internacionales (número de catálogo, si el usuario lo conoce)
    val catalogScottNumber: String? = null,
    val catalogMichelNumber: String? = null,
    val catalogYvertNumber: String? = null,

    // Origen de los datos
    val aiSuggested: Boolean = false,      // true si los campos vinieron de la IA y no han sido confirmados
    val aiConfidence: Float? = null,       // 0.0 - 1.0

    val userNotes: String? = null,
    val dateAdded: Long = System.currentTimeMillis()
)
