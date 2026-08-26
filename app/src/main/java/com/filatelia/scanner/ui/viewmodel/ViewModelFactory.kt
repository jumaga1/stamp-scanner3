package com.filatelia.scanner.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.filatelia.scanner.ai.AiRecognitionRepository
import com.filatelia.scanner.data.StampRepository
import com.filatelia.scanner.duplicate.DuplicateDetector
import com.filatelia.scanner.imageprocessing.ImagePreprocessor
import com.filatelia.scanner.imageprocessing.PerceptualHash

class ViewModelFactory(
    private val stampRepository: StampRepository,
    private val aiRepository: AiRecognitionRepository,
    private val duplicateDetector: DuplicateDetector = DuplicateDetector(),
    private val imagePreprocessor: ImagePreprocessor = ImagePreprocessor(),
    private val perceptualHash: PerceptualHash = PerceptualHash()
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return when {
            modelClass.isAssignableFrom(ScanViewModel::class.java) -> {
                ScanViewModel(
                    stampRepository = stampRepository,
                    aiRepository = aiRepository,
                    duplicateDetector = duplicateDetector,
                    imagePreprocessor = imagePreprocessor,
                    perceptualHash = perceptualHash
                ) as T
            }
            modelClass.isAssignableFrom(CollectionViewModel::class.java) -> {
                CollectionViewModel(stampRepository) as T
            }
            else -> throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}
