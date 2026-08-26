package com.filatelia.scanner.data

import com.filatelia.scanner.duplicate.DuplicateDetector
import com.filatelia.scanner.duplicate.DuplicateResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

enum class SortOrder {
    RECENT,
    COUNTRY,
    YEAR
}

class StampRepository(private val stampDao: StampDao) {

    val stamps: Flow<List<StampEntity>> = stampDao.getAllStamps()

    fun getAll(): Flow<List<StampEntity>> = stampDao.getAllStamps()

    fun getAllStamps(): Flow<List<StampEntity>> = stampDao.getAllStamps()

    fun observeStamps(query: String? = null, sort: SortOrder = SortOrder.RECENT): Flow<List<StampEntity>> {
        return stampDao.getAllStamps().map { list ->
            var filtered = if (!query.isNullOrBlank()) {
                val q = query.trim().lowercase()
                list.filter { item ->
                    (item.country?.lowercase()?.contains(q) == true) ||
                    (item.motif?.lowercase()?.contains(q) == true) ||
                    (item.catalogMichelNumber?.lowercase()?.contains(q) == true) ||
                    (item.catalogScottNumber?.lowercase()?.contains(q) == true)
                }
            } else {
                list
            }

            when (sort) {
                SortOrder.RECENT -> filtered.sortedByDescending { it.id }
                SortOrder.COUNTRY -> filtered.sortedBy { it.country ?: "" }
                SortOrder.YEAR -> filtered.sortedBy { it.issueYear ?: 0 }
            }
        }
    }

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
