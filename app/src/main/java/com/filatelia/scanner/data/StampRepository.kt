package com.filatelia.scanner.data

import com.filatelia.scanner.duplicate.DuplicateDetector
import com.filatelia.scanner.duplicate.DuplicateResult
import kotlinx.coroutines.flow.Flow

class StampRepository(private val stampDao: StampDao) {

    val stamps: Flow<List<StampEntity>> = stampDao.getAllStamps()

    fun getAll(): Flow<List<StampEntity>> = stampDao.getAllStamps()

    fun getAllStamps(): Flow<List<StampEntity>> = stampDao.getAllStamps()

    suspend fun getStampById(id: Long): StampEntity? = stampDao.getStampById(id)

    suspend fun insert(stamp: StampEntity): Long = stampDao.insertStamp(stamp)

    suspend fun insertStamp(stamp: StampEntity): Long = stampDao.insertStamp(stamp)

    suspend fun update(stamp: StampEntity) = stampDao.updateStamp(stamp)

    suspend fun updateStamp(stamp: StampEntity) = stampDao.updateStamp(stamp)

    suspend fun delete(stamp: StampEntity) = stampDao.deleteStamp(stamp)

    suspend fun deleteStamp(stamp: StampEntity) = stampDao.deleteStamp(stamp)

    fun checkDuplicate(candidate: StampEntity, collection: List<StampEntity>): DuplicateResult {
        return DuplicateDetector.check(candidate, collection)
    }
}
