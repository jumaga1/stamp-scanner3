package com.filatelia.scanner.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "stamps")
data class StampEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val imagePath: String,
    val referenceImageUrl: String? = null,
    val perceptualHash: String = "",
    val country: String? = null,
    val era: String? = null,
    val faceValue: String? = null,
    val series: String? = null,
    val condition: String? = null,
    val rarity: String? = null,
    val issueYear: Int? = null,
    val motif: String? = null,
    val historicalNote: String? = null,
    val estimatedMarketValue: String? = null,
    val catalogScottNumber: String? = null,
    val catalogMichelNumber: String? = null,
    val catalogYvertNumber: String? = null,
    val aiSuggested: Boolean = false,
    val aiConfidence: Float? = null
)
