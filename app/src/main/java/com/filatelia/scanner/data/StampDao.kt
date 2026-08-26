package com.filatelia.scanner.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface StampDao {

    @Query("SELECT * FROM stamps ORDER BY createdAt DESC")
    fun getAllStamps(): Flow<List<StampEntity>>

    @Query("SELECT * FROM stamps WHERE id = :id LIMIT 1")
    suspend fun getStampById(id: Long): StampEntity?

    @Query("SELECT * FROM stamps WHERE (:query IS NULL OR country LIKE '%' || :query || '%' OR motif LIKE '%' || :query || '%' OR catalogMichelNumber LIKE '%' || :query || '%')")
    fun observeStamps(query: String?): Flow<List<StampEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStamp(stamp: StampEntity): Long

    @Update
    suspend fun updateStamp(stamp: StampEntity)

    @Delete
    suspend fun deleteStamp(stamp: StampEntity)
}
