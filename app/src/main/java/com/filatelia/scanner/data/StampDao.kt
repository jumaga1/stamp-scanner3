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

    @Query("SELECT * FROM stamps ORDER BY id DESC")
    fun getAllStamps(): Flow<List<StampEntity>>

    @Query("SELECT * FROM stamps WHERE id = :id LIMIT 1")
    suspend fun getStampById(id: Long): StampEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStamp(stamp: StampEntity): Long

    @Update
    suspend fun updateStamp(stamp: StampEntity)

    @Delete
    suspend fun deleteStamp(stamp: StampEntity)
}
