package com.filatelia.scanner.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface StampDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(stamp: StampEntity): Long

    @Update
    suspend fun update(stamp: StampEntity)

    @Delete
    suspend fun delete(stamp: StampEntity)

    @Query("SELECT * FROM stamps ORDER BY dateAdded DESC")
    fun getAll(): Flow<List<StampEntity>>

    @Query("SELECT * FROM stamps WHERE id = :id")
    suspend fun getById(id: Long): StampEntity?

    @Query("SELECT * FROM stamps ORDER BY country ASC, issueYear ASC")
    fun getAllOrderedByCountry(): Flow<List<StampEntity>>

    @Query("SELECT * FROM stamps ORDER BY issueYear ASC")
    fun getAllOrderedByYear(): Flow<List<StampEntity>>

    @Query("SELECT * FROM stamps ORDER BY series ASC")
    fun getAllOrderedBySeries(): Flow<List<StampEntity>>

    @Query("SELECT * FROM stamps ORDER BY rarity DESC")
    fun getAllOrderedByRarity(): Flow<List<StampEntity>>

    @Query("SELECT * FROM stamps WHERE country = :country")
    fun getByCountry(country: String): Flow<List<StampEntity>>

    @Query("""
        SELECT * FROM stamps
        WHERE country LIKE '%' || :query || '%'
           OR series LIKE '%' || :query || '%'
           OR motif LIKE '%' || :query || '%'
           OR catalogScottNumber LIKE '%' || :query || '%'
           OR catalogMichelNumber LIKE '%' || :query || '%'
           OR catalogYvertNumber LIKE '%' || :query || '%'
    """)
    fun search(query: String): Flow<List<StampEntity>>

    // Trae todos los hashes existentes para comparar contra un sello recién escaneado.
    // Se filtra en Kotlin por distancia de Hamming (ver DuplicateDetector) porque
    // SQLite no calcula distancia de Hamming de forma nativa.
    @Query("SELECT id, perceptualHash, country, issueYear, faceValue FROM stamps")
    suspend fun getAllHashesForDuplicateCheck(): List<StampHashProjection>
}

data class StampHashProjection(
    val id: Long,
    val perceptualHash: String,
    val country: String?,
    val issueYear: Int?,
    val faceValue: String?
)
