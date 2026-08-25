package com.filatelia.scanner.data

import com.filatelia.scanner.duplicate.DuplicateDetector
import com.filatelia.scanner.duplicate.DuplicateResult
import kotlinx.coroutines.flow.Flow

enum class SortOrder { RECIENTES, PAIS, ANIO, SERIE, RAREZA }

class StampRepository(private val dao: StampDao) {

    fun observeStamps(order: SortOrder): Flow<List<StampEntity>> = when (order) {
        SortOrder.RECIENTES -> dao.getAll()
        SortOrder.PAIS -> dao.getAllOrderedByCountry()
        SortOrder.ANIO -> dao.getAllOrderedByYear()
        SortOrder.SERIE -> dao.getAllOrderedBySeries()
        SortOrder.RAREZA -> dao.getAllOrderedByRarity()
    }

    fun search(query: String): Flow<List<StampEntity>> = dao.search(query)

    suspend fun getById(id: Long): StampEntity? = dao.getById(id)

    /**
     * Revisa la colección completa antes de guardar un sello nuevo.
     * Combina distancia de hash perceptual (similitud visual) con coincidencia
     * de metadatos (país, año, valor nominal) para decidir si es un duplicado probable.
     */
    suspend fun checkForDuplicate(newHash: String, country: String?, issueYear: Int?, faceValue: String?): DuplicateResult {
        val existing = dao.getAllHashesForDuplicateCheck()
        return DuplicateDetector.findDuplicate(existing, newHash, country, issueYear, faceValue)
    }

    suspend fun saveStamp(stamp: StampEntity): Long = dao.insert(stamp)

    suspend fun updateStamp(stamp: StampEntity) = dao.update(stamp)

    suspend fun deleteStamp(stamp: StampEntity) = dao.delete(stamp)
}
