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
        return when {
            modelClass.isAssignableFrom(ScanViewModel::class.java) -> {
                ScanViewModel(
                    stampRepository = stampRepository,
                    aiRepository = aiRepository
                ) as T
            }
            modelClass.isAssignableFrom(CollectionViewModel::class.java) -> {
                CollectionViewModel(stampRepository) as T
            }
            else -> throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}
