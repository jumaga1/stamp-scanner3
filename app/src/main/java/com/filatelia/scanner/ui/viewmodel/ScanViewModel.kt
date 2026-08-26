package com.filatelia.scanner.ui.viewmodel

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.filatelia.scanner.ai.AiRecognitionOutcome
import com.filatelia.scanner.ai.AiRecognitionRepository
import com.filatelia.scanner.ai.StampRecognitionResult
import com.filatelia.scanner.data.StampEntity
import com.filatelia.scanner.data.StampRepository
import com.filatelia.scanner.duplicate.DuplicateCheckResult
import com.filatelia.scanner.duplicate.DuplicateDetector
import com.filatelia.scanner.imageprocessing.ImagePreprocessor
import com.filatelia.scanner.imageprocessing.PerceptualHash
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

sealed class ScanStep {
    object Idle : ScanStep()
    object Preprocessing : ScanStep()
    object CheckingDuplicates : ScanStep()
    data class DuplicateFound(val result: DuplicateCheckResult) : ScanStep()
    object RunningAi : ScanStep()
    object ReadyToSave : ScanStep()
    data class Error(val message: String) : ScanStep()
}

data class ScanUiState(
    val step: ScanStep = ScanStep.Idle,
    val rawImageFile: File? = null,
    val processedImageFile: File? = null,
    val processedBitmap: Bitmap? = null,
    val perceptualHash: String? = null,
    val duplicateResult: DuplicateCheckResult? = null,
    val aiResult: StampRecognitionResult? = null
)

class ScanViewModel(
    private val stampRepository: StampRepository,
    private val aiRepository: AiRecognitionRepository,
    private val duplicateDetector: DuplicateDetector = DuplicateDetector(),
    private val imagePreprocessor: ImagePreprocessor = ImagePreprocessor(),
    private val perceptualHash: PerceptualHash = PerceptualHash()
) : ViewModel() {

    private val _uiState = MutableStateFlow(ScanUiState())
    val uiState: StateFlow<ScanUiState> = _uiState.asStateFlow()

    fun onImageCaptured(file: File) {
        viewModelScope.launch {
            _uiState.update { it.copy(step = ScanStep.Preprocessing, rawImageFile = file) }

            val prepResult = imagePreprocessor.preprocess(file)
            if (prepResult.file == null) {
                _uiState.update {
                    it.copy(step = ScanStep.Error(prepResult.errorMessage ?: "Error al procesar la imagen."))
                }
                return@launch
            }

            val processedFile = prepResult.file
            val bitmap = BitmapFactory.decodeFile(processedFile.absolutePath)
            val hash = perceptualHash.compute(bitmap)

            _uiState.update {
                it.copy(
                    processedImageFile = processedFile,
                    processedBitmap = bitmap,
                    perceptualHash = hash,
                    step = ScanStep.CheckingDuplicates
                )
            }

            // Consultar colección actual de sellos
            val existingStamps = try {
                stampRepository.getAllStamps().first()
            } catch (_: Exception) {
                emptyList()
            }

            val candidateEntity = StampEntity(
                imagePath = processedFile.absolutePath,
                perceptualHash = hash,
                country = "",
                era = "",
                faceValue = "",
                series = "",
                condition = "",
                rarity = "",
                motif = "",
                historicalNote = ""
            )

            val duplicateResult = duplicateDetector.check(
                candidate = candidateEntity,
                collection = existingStamps
            )

            if (duplicateResult.isDuplicate) {
                _uiState.update {
                    it.copy(
                        duplicateResult = duplicateResult,
                        step = ScanStep.DuplicateFound(duplicateResult)
                    )
                }
            } else {
                runAiAnalysis(processedFile)
            }
        }
    }

    fun continueDespiteDuplicate() {
        val file = _uiState.value.processedImageFile ?: return
        runAiAnalysis(file)
    }

    private fun runAiAnalysis(file: File) {
        viewModelScope.launch {
            _uiState.update { it.copy(step = ScanStep.RunningAi) }

            when (val outcome = aiRepository.recognize(file)) {
                is AiRecognitionOutcome.Success -> {
                    _uiState.update {
                        it.copy(
                            aiResult = outcome.result,
                            step = ScanStep.ReadyToSave
                        )
                    }
                }
                is AiRecognitionOutcome.Error -> {
                    _uiState.update {
                        it.copy(step = ScanStep.Error(outcome.message))
                    }
                }
            }
        }
    }

    fun saveStamp(stamp: StampEntity, onSaved: () -> Unit) {
        viewModelScope.launch {
            stampRepository.insert(stamp)
            reset()
            onSaved()
        }
    }

    fun reset() {
        _uiState.value = ScanUiState()
    }
}
