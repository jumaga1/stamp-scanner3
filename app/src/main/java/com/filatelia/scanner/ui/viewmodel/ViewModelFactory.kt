package com.filatelia.scanner.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.filatelia.scanner.ai.AiRecognitionRepository
import com.filatelia.scanner.data.StampRepository

class ViewModelFactory(
    private val stampRepository: StampRepository,
    private val aiRepository: AiRecognitionRepository
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return when (modelClass) {
            ScanViewModel::class.java -> ScanViewModel(stampRepository, aiRepository) as T
            CollectionViewModel::class.java -> CollectionViewModel(stampRepository) as T
            else -> throw IllegalArgumentException("ViewModel desconocido: ${modelClass.name}")
        }
    }
}
