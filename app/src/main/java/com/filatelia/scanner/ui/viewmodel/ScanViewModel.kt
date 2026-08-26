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
import com.filatelia.scanner.duplicate.DuplicateConfidence
import com.filatelia.scanner.duplicate.DuplicateDetector
import com.filatelia.scanner.imageprocessing.ImagePreprocessor
import com.filatelia.scanner.imageprocessing.PerceptualHash
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

sealed interface ScanStep {
    data object Idle : ScanStep
    data object Preprocessing : ScanStep
    data object CheckingDuplicates : ScanStep
    data object RunningAi : ScanStep
    data class DuplicateFound(val result: DuplicateCheckResult) : ScanStep
    data object ReadyToSave : ScanStep
    data class Error(val message: String) : ScanStep
}

data class ScanUiState(
    val step: ScanStep = ScanStep.Idle,
    val rawImageFile: File? = null,
    val processedImageFile: File? = null,
    val processedBitmap: Bitmap? = null,
    val perceptualHash: String? = null,
    val duplicateResult: DuplicateCheckResult? = null,
    val aiResult: StampRecognitionResult? = null,
    val aiUnavailableReason: String? = null
)

class ScanViewModel(
    private val stampRepository: StampRepository,
    private val aiRepository: AiRecognitionRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ScanUiState())
    val uiState: StateFlow<ScanUiState> = _uiState.asStateFlow()

    fun onImageCaptured(file: File) {
        viewModelScope.launch {
            _uiState.value = ScanUiState(step = ScanStep.Preprocessing, rawImageFile = file)
            val preprocessed = withContext(Dispatchers.Default) {
                ImagePreprocessor.preprocess(file)
            }
            val pHash = withContext(Dispatchers.Default) {
                val bmp = BitmapFactory.decodeFile(preprocessed.absolutePath)
                bmp?.let { PerceptualHash.compute(it) } ?: ""
            }

            _uiState.value = _uiState.value.copy(
                processedImageFile = preprocessed,
                processedBitmap = BitmapFactory.decodeFile(preprocessed.absolutePath),
                perceptualHash = pHash,
                step = ScanStep.CheckingDuplicates
            )

            val existing = stampRepository.getAll().first()
            val candidate = StampEntity(
                imagePath = preprocessed.absolutePath,
                perceptualHash = pHash
            )
            val dup = DuplicateDetector.check(candidate, existing)

            if (dup.confidence == DuplicateConfidence.CASI_SEGURO || dup.confidence == DuplicateConfidence.PROBABLE) {
                _uiState.value = _uiState.value.copy(
                    duplicateResult = dup,
                    step = ScanStep.DuplicateFound(dup)
                )
            } else {
                runAiRecognition(preprocessed, dup)
            }
        }
    }

    fun continueDespiteDuplicate() {
        val file = _uiState.value.processedImageFile ?: return
        val dup = _uiState.value.duplicateResult
        runAiRecognition(file, dup)
    }

    private fun runAiRecognition(file: File, dup: DuplicateCheckResult?) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(step = ScanStep.RunningAi)
            when (val outcome = aiRepository.recognize(file)) {
                is AiRecognitionOutcome.Success -> {
                    _uiState.value = _uiState.value.copy(
                        aiResult = outcome.result,
                        step = ScanStep.ReadyToSave
                    )
                }
                is AiRecognitionOutcome.RateLimited -> {
                    _uiState.value = _uiState.value.copy(
                        aiUnavailableReason = "Límite de consultas de IA alcanzado por hoy.",
                        step = ScanStep.ReadyToSave
                    )
                }
                is AiRecognitionOutcome.OfflineFallback -> {
                    _uiState.value = _uiState.value.copy(
                        aiUnavailableReason = "Sin conexión a internet para IA.",
                        step = ScanStep.ReadyToSave
                    )
                }
                is AiRecognitionOutcome.Error -> {
                    _uiState.value = _uiState.value.copy(
                        aiUnavailableReason = "No se pudo consultar la IA: ${outcome.message}",
                        step = ScanStep.ReadyToSave
                    )
                }
            }
        }
    }

    fun saveStamp(entity: StampEntity, onSaved: () -> Unit) {
        viewModelScope.launch {
            stampRepository.insert(entity)
            reset()
            onSaved()
        }
    }

    fun reset() {
        _uiState.value = ScanUiState()
    }
}
